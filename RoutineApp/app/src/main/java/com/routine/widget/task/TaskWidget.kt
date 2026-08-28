package com.routine.widget.task

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.*
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.color.ColorProvider
import androidx.glance.layout.*
import androidx.glance.layout.Alignment
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text as GlanceText
import androidx.glance.text.TextStyle
import androidx.datastore.preferences.core.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.routine.data.model.Task
import com.routine.data.repository.TaskRepository
import com.routine.domain.PatternAnalyzer
import com.routine.domain.RelativeTimeFormatter
import com.routine.worker.PatternAnalysisWorker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

// ─── Widget palette (day / night aware) ──────────────────────────────────────

private object WidgetPalette {
    val background = ColorProvider(
        day = androidx.compose.ui.graphics.Color(0xFFF1F7EC),
        night = androidx.compose.ui.graphics.Color(0xFF1A1F19)
    )
    val backgroundOverdue = ColorProvider(
        day = androidx.compose.ui.graphics.Color(0xFFFDEDE9),
        night = androidx.compose.ui.graphics.Color(0xFF2B1D18)
    )
    val title = ColorProvider(
        day = androidx.compose.ui.graphics.Color(0xFF1B1F1A),
        night = androidx.compose.ui.graphics.Color(0xFFE6EAE1)
    )
    val accent = ColorProvider(
        day = androidx.compose.ui.graphics.Color(0xFF2E7D32),
        night = androidx.compose.ui.graphics.Color(0xFFA5D6A7)
    )
    val accentOverdue = ColorProvider(
        day = androidx.compose.ui.graphics.Color(0xFFBA1A1A),
        night = androidx.compose.ui.graphics.Color(0xFFFFB4AB)
    )
}

// ─── Widget Provider ─────────────────────────────────────────────────────────

class TaskWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = TaskWidget()

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                com.routine.di.RepositoryEntryPoint.get(context)
                    .taskRepository()
                    .clearWidgetAssignments(appWidgetIds)
            } finally {
                pendingResult.finish()
            }
        }
    }
}

class TaskWidget : GlanceAppWidget() {
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            TaskWidgetContent()
        }
    }

    @Composable
    fun TaskWidgetContent() {
        val prefs = currentState<androidx.datastore.preferences.core.Preferences>()
        val taskName = prefs[stringPreferencesKey("task_name")] ?: "Task"
        val emoji = prefs[stringPreferencesKey("task_emoji")] ?: "📌"
        val lastDoneAt = prefs[longPreferencesKey("last_done_at")]
        val lastDoneText = RelativeTimeFormatter.format(lastDoneAt)
        val isOverdue = prefs[booleanPreferencesKey("is_overdue")] ?: false

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(
                    if (isOverdue) WidgetPalette.backgroundOverdue
                    else WidgetPalette.background
                )
                .cornerRadius(20.dp)
                .padding(12.dp)
                .clickable(actionRunCallback<TaskLogAction>()),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                GlanceText(
                    text = emoji,
                    style = TextStyle(fontSize = 26.sp)
                )
                Spacer(modifier = GlanceModifier.height(4.dp))
                GlanceText(
                    text = taskName,
                    style = TextStyle(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = WidgetPalette.title
                    ),
                    maxLines = 1
                )
                Spacer(modifier = GlanceModifier.height(2.dp))
                GlanceText(
                    text = if (isOverdue) "● due · $lastDoneText" else "✓ $lastDoneText",
                    style = TextStyle(
                        fontSize = 11.sp,
                        color = if (isOverdue) WidgetPalette.accentOverdue
                        else WidgetPalette.accent
                    )
                )
            }
        }
    }
}

// ─── Tap Action ──────────────────────────────────────────────────────────────

class TaskLogAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(glanceId)
        TaskWidgetUpdateService.logAndUpdate(context, appWidgetId)
    }
}

// ─── Widget Config Activity ───────────────────────────────────────────────────

@AndroidEntryPoint
class TaskWidgetConfigActivity : ComponentActivity() {

    @Inject lateinit var taskRepository: TaskRepository

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setContent {
            com.routine.ui.theme.AppTheme {
                Surface {
                    val tasks by taskRepository.observeAllTasks()
                        .collectAsStateWithLifecycle(initialValue = emptyList())
                    TaskWidgetConfigScreen(
                        tasks = tasks.filter { it.widgetId == -1 || it.widgetId == appWidgetId },
                        onChooseTask = { task ->
                            finishConfiguration(task.id, task.name, task.emoji)
                        },
                        onCreateTask = { name, emoji ->
                            lifecycleScope.launch {
                                val taskId = withContext(Dispatchers.IO) {
                                    taskRepository.createTask(name.trim(), emoji)
                                }
                                finishConfiguration(taskId, name.trim(), emoji)
                            }
                        },
                        onCancel = { finish() }
                    )
                }
            }
        }
    }

    private fun finishConfiguration(taskId: Long, name: String, emoji: String) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                taskRepository.assignWidget(taskId, appWidgetId)
                TaskWidgetUpdateService.updateWidgetState(
                    this@TaskWidgetConfigActivity,
                    appWidgetId,
                    name,
                    emoji,
                    taskRepository.getLastLog(taskId)?.timestamp,
                    false
                )
            }
            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
            finish()
        }
    }
}

@Composable
fun TaskWidgetConfigScreen(
    tasks: List<Task>,
    onChooseTask: (Task) -> Unit,
    onCreateTask: (String, String) -> Unit,
    onCancel: () -> Unit
) {
    val emojis = listOf("🌱", "💊", "🏃", "💧", "📚", "🧹", "🐕", "🪴", "☕", "🧘")
    var taskName by remember { mutableStateOf("") }
    var selectedEmoji by remember { mutableStateOf("🌱") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Add a task widget", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Choose what this widget should track. One tap will record a completion.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (tasks.isNotEmpty()) {
            item { Text("Choose an existing task", style = MaterialTheme.typography.titleSmall) }
            items(tasks, key = { it.id }) { task ->
                Card(
                    onClick = { onChooseTask(task) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Text(task.emoji, fontSize = 24.sp)
                        Spacer(Modifier.width(12.dp))
                        Text(task.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        Text("Choose", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            item { HorizontalDivider() }
        }

        item { Text("Create a new task", style = MaterialTheme.typography.titleSmall) }
        item {
            OutlinedTextField(
                value = taskName,
                onValueChange = { if (it.length <= 60) taskName = it },
                label = { Text("Task name") },
                placeholder = { Text("Water plants") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        item { Text("Choose an icon", style = MaterialTheme.typography.labelLarge) }
        item { FlowRow(emojis, selectedEmoji) { selectedEmoji = it } }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancel") }
                Button(
                    enabled = taskName.isNotBlank(),
                    onClick = { onCreateTask(taskName.trim(), selectedEmoji) },
                    modifier = Modifier.weight(1f)
                ) { Text("Create widget") }
            }
        }
    }
}

@Composable
fun FlowRow(emojis: List<String>, selected: String, onSelect: (String) -> Unit) {
    androidx.compose.foundation.layout.Column {
        emojis.chunked(5).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { emoji ->
                    FilterChip(
                        selected = emoji == selected,
                        onClick = { onSelect(emoji) },
                        label = { Text(emoji) }
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

// ─── Widget Update Service (static helper) ───────────────────────────────────

object TaskWidgetUpdateService {

    suspend fun logAndUpdate(context: Context, appWidgetId: Int) {
        // This is called from the widget action — uses Hilt entry points for repo access
        val entryPoint = com.routine.di.RepositoryEntryPoint.get(context)
        val taskRepo = entryPoint.taskRepository()
        val task = taskRepo.getTaskByWidget(appWidgetId) ?: return

        recordCompletion(context, task.id)
    }

    /** Records from widgets and notification actions through one consistent path. */
    suspend fun recordCompletion(context: Context, taskId: Long) {
        val entryPoint = com.routine.di.RepositoryEntryPoint.get(context)
        val taskRepo = entryPoint.taskRepository()

        // Log the tap with current location (best-effort)
        taskRepo.logTask(taskId, null, null, null)

        // Analyze after every eligible tap. Unique work coalesces rapid repeated taps.
        val count = taskRepo.getLogCount(taskId)
        if (count >= PatternAnalyzer.MIN_LOGS_FOR_ANALYSIS) {
            PatternAnalysisWorker.enqueue(context, taskId)
        }

        refreshTask(context, taskId)
    }

    /** Recompute a task's display state and push it to its widget, if any. */
    suspend fun refreshTask(context: Context, taskId: Long) {
        val entryPoint = com.routine.di.RepositoryEntryPoint.get(context)
        val taskRepo = entryPoint.taskRepository()
        val task = taskRepo.getTask(taskId) ?: return
        if (task.widgetId == -1) return

        val lastLog = taskRepo.getLastLog(task.id)
        val routine = entryPoint.routineRepository().getForTask(task.id)
        val hoursSince = lastLog?.let { (System.currentTimeMillis() - it.timestamp) / 3_600_000 }
        val isOverdue = routine != null &&
            hoursSince != null &&
            routine.intervalHours != null &&
            hoursSince > routine.intervalHours &&
            routine.status == com.routine.data.model.RoutineStatus.ACTIVE

        updateWidgetState(context, task.widgetId, task.name, task.emoji, lastLog?.timestamp, isOverdue)
    }

    suspend fun updateWidgetState(
        context: Context,
        appWidgetId: Int,
        name: String,
        emoji: String,
        lastDoneAt: Long?,
        isOverdue: Boolean
    ) {
        val manager = GlanceAppWidgetManager(context)
        val glanceId = manager.getGlanceIds(TaskWidget::class.java)
            .firstOrNull { manager.getAppWidgetId(it) == appWidgetId }
            ?: return

        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
            prefs.toMutablePreferences().apply {
                this[stringPreferencesKey("task_name")] = name
                this[stringPreferencesKey("task_emoji")] = emoji
                if (lastDoneAt == null) {
                    remove(longPreferencesKey("last_done_at"))
                } else {
                    this[longPreferencesKey("last_done_at")] = lastDoneAt
                }
                this[booleanPreferencesKey("is_overdue")] = isOverdue
            }
        }
        TaskWidget().update(context, glanceId)
    }
}
