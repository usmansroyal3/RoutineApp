package com.routine.ui.home

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.routine.data.model.Routine
import com.routine.data.model.RoutineStatus
import com.routine.data.model.RoutineType
import com.routine.data.model.Task
import com.routine.data.model.TaskWithLastLog
import com.routine.data.repository.RoutineRepository
import com.routine.data.repository.TaskRepository
import com.routine.domain.RelativeTimeFormatter
import com.routine.widget.task.TaskWidgetUpdateService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

private val TASK_EMOJIS = listOf("🌱", "💧", "🪴", "💊", "🏃", "📚", "🧹", "🐕", "☕", "🧘")

@HiltViewModel
@SuppressLint("StaticFieldLeak") // Hilt supplies the process-wide application context.
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val taskRepository: TaskRepository,
    private val routineRepository: RoutineRepository
) : ViewModel() {

    val tasksWithState: StateFlow<List<TaskWithLastLog>> = taskRepository
        .observeTasksWithState()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val proposedRoutines: StateFlow<List<Routine>> = routineRepository
        .observeProposed()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun createTask(name: String, emoji: String) = viewModelScope.launch {
        taskRepository.createTask(name.trim(), emoji)
    }

    fun completeTask(task: Task) = viewModelScope.launch {
        TaskWidgetUpdateService.recordCompletion(context, task.id)
    }

    fun activateRoutine(routine: Routine) = viewModelScope.launch {
        routineRepository.activate(routine.id)
    }

    fun dismissRoutine(routine: Routine) = viewModelScope.launch {
        routineRepository.dismiss(routine.id)
    }

    fun snoozeRoutine(routine: Routine) = viewModelScope.launch {
        routineRepository.snooze(routine.id, System.currentTimeMillis() + 24 * 3_600_000L)
    }

    fun deleteTask(task: Task) = viewModelScope.launch {
        taskRepository.deleteTask(task)
    }
}

@Composable
fun HomeScreen(vm: HomeViewModel = hiltViewModel()) {
    val tasks by vm.tasksWithState.collectAsStateWithLifecycle()
    val proposals by vm.proposedRoutines.collectAsStateWithLifecycle()
    var showAddTask by remember { mutableStateOf(false) }
    var deleteCandidate by remember { mutableStateOf<Task?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddTask = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add task") }
            )
        }
    ) { scaffoldPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp,
                top = scaffoldPadding.calculateTopPadding() + 20.dp,
                end = 20.dp,
                bottom = scaffoldPadding.calculateBottomPadding() + 104.dp
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item { HomeHeader(tasks) }

            if (tasks.isEmpty()) {
                item { EmptyState(onAddTask = { showAddTask = true }) }
            } else {
                if (proposals.isNotEmpty()) {
                    item { SectionLabel("REMINDER SUGGESTIONS") }
                    items(proposals, key = { "proposal_${it.id}" }) { routine ->
                        RoutineProposalCard(
                            routine = routine,
                            tasks = tasks,
                            onActivate = { vm.activateRoutine(routine) },
                            onDismiss = { vm.dismissRoutine(routine) },
                            onSnooze = { vm.snoozeRoutine(routine) }
                        )
                    }
                }

                item { SectionLabel("YOUR TASKS") }
                items(tasks, key = { it.task.id }) { state ->
                    TaskCard(
                        state = state,
                        onDone = { vm.completeTask(state.task) },
                        onDelete = { deleteCandidate = state.task }
                    )
                }
            }

            item { WidgetGuideCard() }
        }
    }

    if (showAddTask) {
        AddTaskDialog(
            onDismiss = { showAddTask = false },
            onCreate = { name, emoji ->
                vm.createTask(name, emoji)
                showAddTask = false
            }
        )
    }

    deleteCandidate?.let { task ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text("Delete ${task.name}?") },
            text = { Text("Its completion history and reminder pattern will also be removed.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteTask(task)
                    deleteCandidate = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleteCandidate = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun HomeHeader(tasks: List<TaskWithLastLog>) {
    val completedToday = tasks.count { it.lastLog?.timestamp?.let(::isToday) == true }
    val overdue = tasks.count(TaskWithLastLog::isOverdue)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column {
            Text(
                "Routine",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                "One tap keeps life moving.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (tasks.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    SummaryMetric(completedToday.toString(), "done today")
                    SummaryMetric(tasks.size.toString(), "tasks")
                    SummaryMetric(overdue.toString(), "due now")
                }
            }
        }
    }
}

@Composable
private fun SummaryMetric(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f)
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        modifier = Modifier.padding(top = 6.dp),
        style = MaterialTheme.typography.labelSmall,
        letterSpacing = 2.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun TaskCard(
    state: TaskWithLastLog,
    onDone: () -> Unit,
    onDelete: () -> Unit
) {
    val routine = state.routine
    val interval = routine?.intervalHours
    val elapsedFraction = if (
        routine?.status == RoutineStatus.ACTIVE && interval != null && state.hoursSinceLastDone != null
    ) {
        (state.hoursSinceLastDone!!.toFloat() / interval).coerceIn(0f, 1f)
    } else null

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (state.isOverdue) {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
            }
        )
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                EmojiBadge(state.task.emoji)
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        state.task.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        RelativeTimeFormatter.format(state.lastLog?.timestamp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (state.isOverdue) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (routine?.status == RoutineStatus.ACTIVE && interval != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            buildIntervalText(interval),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete ${state.task.name}")
                }
                Button(
                    onClick = onDone,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Done")
                }
            }
            if (elapsedFraction != null) {
                LinearProgressIndicator(
                    progress = { elapsedFraction },
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                    color = if (state.isOverdue) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun RoutineProposalCard(
    routine: Routine,
    tasks: List<TaskWithLastLog>,
    onActivate: () -> Unit,
    onDismiss: () -> Unit,
    onSnooze: () -> Unit
) {
    val task = tasks.find { it.task.id == routine.taskId }?.task
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                EmojiBadge(task?.emoji ?: "✨")
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("A pattern is forming", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${task?.name ?: "This task"} · ${buildRoutineDescription(routine)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.75f)
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Text("Would you like a gentle reminder when it is usually due?")
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDismiss) { Text("No thanks") }
                OutlinedButton(onClick = onSnooze) { Text("Ask later") }
                Button(onClick = onActivate, modifier = Modifier.weight(1f)) { Text("Enable") }
            }
        }
    }
}

@Composable
private fun EmptyState(onAddTask: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.size(92.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) { Text("🌿", fontSize = 42.sp) }
            Spacer(Modifier.height(20.dp))
            Text("Start with one small thing", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(
                "Create a task here, then add its widget to your home screen for one-tap tracking.",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(22.dp))
            Button(onClick = onAddTask) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Create your first task")
            }
        }
    }
}

@Composable
private fun WidgetGuideCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Row(modifier = Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(46.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.65f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Widgets, contentDescription = null)
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text("Put a task on your home screen", fontWeight = FontWeight.SemiBold)
                Text(
                    "Long-press the home screen → Widgets → Routine → choose a task.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f)
                )
            }
        }
    }
}

@Composable
private fun EmojiBadge(emoji: String) {
    Box(
        modifier = Modifier.size(50.dp).clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)),
        contentAlignment = Alignment.Center
    ) { Text(emoji, fontSize = 24.sp) }
}

@Composable
private fun AddTaskDialog(onDismiss: () -> Unit, onCreate: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var emoji by remember { mutableStateOf(TASK_EMOJIS.first()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New task") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 60) name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Task name") },
                    placeholder = { Text("Water plants") },
                    singleLine = true
                )
                Text("Choose an icon", style = MaterialTheme.typography.labelLarge)
                TASK_EMOJIS.chunked(5).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { option ->
                            FilterChip(
                                selected = option == emoji,
                                onClick = { emoji = option },
                                label = { Text(option) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank(),
                onClick = { onCreate(name.trim(), emoji) }
            ) { Text("Create task") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun buildRoutineDescription(routine: Routine): String = when (routine.type) {
    RoutineType.LOCATION_TRIGGERED -> routine.locationLabel?.let { "Usually at $it" }
        ?: routine.intervalHours?.let(::buildIntervalText) ?: "Location pattern"
    else -> routine.intervalHours?.let(::buildIntervalText) ?: "Flexible pattern"
}

private fun buildIntervalText(hours: Int): String = when {
    hours <= 1 -> "About every hour"
    hours < 24 -> "About every $hours hours"
    hours < 48 -> "About every day"
    else -> "About every ${hours / 24} days"
}

private fun isToday(timestamp: Long): Boolean {
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = timestamp }
    return now.get(Calendar.ERA) == then.get(Calendar.ERA) &&
        now.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
}
