package com.routine.ui.home

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.routine.data.model.*
import com.routine.data.repository.RoutineRepository
import com.routine.data.repository.TaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// ─── ViewModel ────────────────────────────────────────────────────────────────

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val taskRepository: TaskRepository,
    private val routineRepository: RoutineRepository
) : ViewModel() {

    val tasksWithState: StateFlow<List<TaskWithLastLog>> = taskRepository
        .observeTasksWithState()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val proposedRoutines: StateFlow<List<Routine>> = routineRepository
        .observeProposed()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun activateRoutine(routine: Routine) = viewModelScope.launch {
        routineRepository.activate(routine.id)
    }

    fun dismissRoutine(routine: Routine) = viewModelScope.launch {
        routineRepository.dismiss(routine.id)
    }

    fun snoozeRoutine(routine: Routine) = viewModelScope.launch {
        val snoozeUntil = System.currentTimeMillis() + 24 * 3_600_000L // 24h
        routineRepository.snooze(routine.id, snoozeUntil)
    }

    fun deleteTask(task: Task) = viewModelScope.launch {
        taskRepository.deleteTask(task)
    }
}

// ─── Screen ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: HomeViewModel = hiltViewModel()) {
    val tasks by vm.tasksWithState.collectAsState()
    val proposed by vm.proposedRoutines.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("My Routines", fontWeight = FontWeight.Bold) })
        }
    ) { padding ->
        if (tasks.isEmpty() && proposed.isEmpty()) {
            EmptyState(modifier = Modifier.padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Routine proposals first
                if (proposed.isNotEmpty()) {
                    item {
                        Text(
                            "🎯 Suggested Routines",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    items(proposed, key = { it.id }) { routine ->
                        RoutineProposalCard(
                            routine = routine,
                            tasks = tasks,
                            onActivate = { vm.activateRoutine(routine) },
                            onDismiss = { vm.dismissRoutine(routine) },
                            onSnooze = { vm.snoozeRoutine(routine) }
                        )
                    }
                    item { HorizontalDivider() }
                }

                // Active tasks
                if (tasks.isNotEmpty()) {
                    item {
                        Text(
                            "📌 Tasks",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    items(tasks, key = { it.task.id }) { state ->
                        TaskCard(state = state, onDelete = { vm.deleteTask(state.task) })
                    }
                }

                item {
                    AddWidgetHint()
                }
            }
        }
    }
}

@Composable
fun RoutineProposalCard(
    routine: Routine,
    tasks: List<TaskWithLastLog>,
    onActivate: () -> Unit,
    onDismiss: () -> Unit,
    onSnooze: () -> Unit
) {
    val task = tasks.find { it.task.id == routine.taskId }?.task

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(task?.emoji ?: "📌", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        "Routine detected: ${task?.name ?: "Task"}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        buildRoutineDescription(routine),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            ConfidenceBar(routine.confidence)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                    Text("Skip")
                }
                OutlinedButton(onClick = onSnooze, modifier = Modifier.weight(1f)) {
                    Text("Later")
                }
                Button(onClick = onActivate, modifier = Modifier.weight(1f)) {
                    Text("Set routine")
                }
            }
        }
    }
}

@Composable
fun ConfidenceBar(confidence: Float) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Pattern confidence", style = MaterialTheme.typography.labelSmall)
            Text("${(confidence * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.height(2.dp))
        LinearProgressIndicator(
            progress = { confidence },
            modifier = Modifier.fillMaxWidth(),
            color = when {
                confidence > 0.7f -> Color(0xFF4CAF50)
                confidence > 0.4f -> Color(0xFFFF9800)
                else -> Color(0xFFF44336)
            }
        )
    }
}

@Composable
fun TaskCard(state: TaskWithLastLog, onDelete: () -> Unit) {
    val isOverdue = state.isOverdue
    val hasActiveRoutine = state.routine?.status == RoutineStatus.ACTIVE

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isOverdue)
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(state.task.emoji, style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(state.task.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    buildLastDoneText(state.hoursSinceLastDone),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isOverdue) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (hasActiveRoutine) {
                    Text(
                        "🔁 Every ${state.routine?.intervalHours?.let { "${it}h" } ?: "?"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
            }
        }
    }
}

@Composable
fun AddWidgetHint() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(
                "Long-press your home screen to add task or note widgets",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
fun EmptyState(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🌱", style = MaterialTheme.typography.displayMedium)
            Spacer(Modifier.height(16.dp))
            Text("No tasks yet", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "Add a task widget to your home screen\nand start tapping!",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

private fun buildRoutineDescription(routine: Routine): String {
    val parts = mutableListOf<String>()
    routine.intervalHours?.let { parts.add("every ${it}h") }
    routine.preferredHourOfDay?.let { parts.add("around ${it}:00") }
    routine.locationLabel?.let { parts.add("at $it") }
    return parts.joinToString(" · ").ifBlank { routine.type.name.lowercase() }
}

private fun buildLastDoneText(hoursSince: Long?): String = when {
    hoursSince == null -> "Never done"
    hoursSince < 1 -> "Just now"
    hoursSince < 24 -> "${hoursSince}h ago"
    else -> "${hoursSince / 24}d ago"
}
