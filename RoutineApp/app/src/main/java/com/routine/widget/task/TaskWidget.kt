package com.routine.widget.task

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
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
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.datastore.preferences.core.*
import com.routine.data.repository.TaskRepository
import com.routine.worker.PatternAnalysisWorker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
        val lastDoneText = prefs[stringPreferencesKey("last_done")] ?: "Never"
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
                Text(
                    text = emoji,
                    style = TextStyle(fontSize = 26.sp)
                )
                Spacer(modifier = GlanceModifier.height(4.dp))
                Text(
                    text = taskName,
                    style = TextStyle(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = WidgetPalette.title
                    ),
                    maxLines = 1
                )
                Spacer(modifier = GlanceModifier.height(2.dp))
                Text(
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
        CoroutineScope(Dispatchers.IO).launch {
            TaskWidgetUpdateService.logAndUpdate(context, appWidgetId)
        }
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
                    TaskWidgetConfigScreen(
                        onConfirm = { name, emoji ->
                            CoroutineScope(Dispatchers.IO).launch {
                                val taskId = taskRepository.createTask(name, emoji)
                                taskRepository.assignWidget(taskId, appWidgetId)
                                TaskWidgetUpdateService.updateWidgetState(
                                    this@TaskWidgetConfigActivity, appWidgetId, name, emoji, "Never", false
                                )
                            }
                            val result = Intent().apply {
                                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                            }
                            setResult(RESULT_OK, result)
                            finish()
                        },
                        onCancel = { finish() }
                    )
                }
            }
        }
    }
}

@Composable
fun TaskWidgetConfigScreen(onConfirm: (String, String) -> Unit, onCancel: () -> Unit) {
    val emojis = listOf("🌱", "💊", "🏃", "💧", "📚", "🧹", "🐕", "🪴", "☕", "🧘")
    var taskName by remember { mutableStateOf("") }
    var selectedEmoji by remember { mutableStateOf("🌱") }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Create Task Widget", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(
            value = taskName,
            onValueChange = { taskName = it },
            label = { Text("Task name") },
            modifier = Modifier.fillMaxWidth()
        )
        Text("Pick an emoji", style = MaterialTheme.typography.labelLarge)
        FlowRow(emojis, selectedEmoji) { selectedEmoji = it }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancel") }
            Button(
                onClick = { if (taskName.isNotBlank()) onConfirm(taskName, selectedEmoji) },
                modifier = Modifier.weight(1f)
            ) { Text("Create") }
        }
    }
}

@Composable
fun FlowRow(emojis: List<String>, selected: String, onSelect: (String) -> Unit) {
    Column {
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

        // Log the tap with current location (best-effort)
        taskRepo.logTask(task.id, null, null, null)

        // Trigger pattern analysis every 3 logs
        val count = taskRepo.getLogCount(task.id)
        if (count % 3 == 0) {
            PatternAnalysisWorker.enqueue(context, task.id)
        }

        refreshTask(context, task.id)
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

        val lastDoneText = when {
            hoursSince == null -> "Never"
            hoursSince < 1 -> "Just now"
            hoursSince < 24 -> "${hoursSince}h ago"
            else -> "${hoursSince / 24}d ago"
        }

        updateWidgetState(context, task.widgetId, task.name, task.emoji, lastDoneText, isOverdue)
    }

    suspend fun updateWidgetState(
        context: Context,
        appWidgetId: Int,
        name: String,
        emoji: String,
        lastDone: String,
        isOverdue: Boolean
    ) {
        val manager = GlanceAppWidgetManager(context)
        val glanceId = manager
            .getGlanceIds(TaskWidget::class.java)
            .firstOrNull { manager.getAppWidgetId(it) == appWidgetId }
            ?: return

        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
            prefs.toMutablePreferences().apply {
                this[stringPreferencesKey("task_name")] = name
                this[stringPreferencesKey("task_emoji")] = emoji
                this[stringPreferencesKey("last_done")] = lastDone
                this[booleanPreferencesKey("is_overdue")] = isOverdue
            }
        }
        TaskWidget().update(context, glanceId)
    }
}
