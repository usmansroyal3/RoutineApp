package com.routine.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.routine.data.model.*
import com.routine.data.repository.RoutineRepository
import com.routine.data.repository.TaskRepository
import com.routine.domain.RelativeTimeFormatter
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

@Composable
fun HomeScreen(vm: HomeViewModel = hiltViewModel()) {
    val tasks by vm.tasksWithState.collectAsStateWithLifecycle()
    val proposed by vm.proposedRoutines.collectAsStateWithLifecycle()

    if (tasks.isEmpty() && proposed.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            ScreenHeader()
            EmptyState(modifier = Modifier.weight(1f))
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { ScreenHeader() }

        // Routine proposals first
        if (proposed.isNotEmpty()) {
            item { SectionLabel("SUGGESTED ROUTINES") }
            items(proposed, key = { "proposal_${it.id}" }) { routine ->
                RoutineProposalCard(
                    routine = routine,
                    tasks = tasks,
                    onActivate = { vm.activateRoutine(routine) },
                    onDismiss = { vm.dismissRoutine(routine) },
                    onSnooze = { vm.snoozeRoutine(routine) }
                )
            }
            item { Spacer(Modifier.height(4.dp)) }
        }

        // Active tasks
        if (tasks.isNotEmpty()) {
            if (proposed.isNotEmpty()) {
                item { SectionLabel("YOUR ROUTINES") }
            }
            items(tasks, key = { it.task.id }) { state ->
                TaskCard(state = state, onDelete = { vm.deleteTask(state.task) })
            }
        }

        item { AddWidgetHint() }
    }
}

@Composable
private fun ScreenHeader() {
    Column(modifier = Modifier.padding(top = 24.dp, bottom = 12.dp)) {
        Text(
            "Routine",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(4.dp))
        SectionLabel("YOUR ROUTINES", padding = 0.dp)
    }
}

@Composable
private fun SectionLabel(text: String, padding: androidx.compose.ui.unit.Dp = 4.dp) {
    Text(
        text,
        modifier = Modifier.padding(top = padding),
        style = MaterialTheme.typography.labelSmall,
        letterSpacing = 2.5.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
    )
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
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                EmojiBadge(task?.emoji ?: "📌")
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        "Pattern detected: ${task?.name ?: "Task"}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        buildRoutineDescription(routine),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Enable reminders for this pattern?",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.height(10.dp))
            ConfidenceBar(routine.confidence)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                    Text("Skip")
                }
                FilledTonalButton(onClick = onSnooze, modifier = Modifier.weight(1f)) {
                    Text("Later")
                }
                Button(onClick = onActivate, modifier = Modifier.weight(1.2f)) {
                    Text("Enable")
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
            Text(
                "Pattern confidence",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
            )
            Text(
                "${(confidence * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { confidence },
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(50)),
            color = when {
                confidence > 0.7f -> MaterialTheme.colorScheme.primary
                confidence > 0.4f -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.error
            },
            trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
        )
    }
}

@Composable
fun TaskCard(state: TaskWithLastLog, onDelete: () -> Unit) {
    val isOverdue = state.isOverdue
    val routine = state.routine
    val hasActiveRoutine = routine?.status == RoutineStatus.ACTIVE
    val interval = routine?.intervalHours

    // Fraction of the interval already elapsed (null when no active routine)
    val elapsedFraction: Float? =
        if (hasActiveRoutine && interval != null && state.hoursSinceLastDone != null)
            (state.hoursSinceLastDone!!.toFloat() / interval).coerceIn(0f, 1f)
        else null

    val statusColor = when {
        isOverdue -> MaterialTheme.colorScheme.error
        elapsedFraction != null && elapsedFraction >= 0.75f -> MaterialTheme.colorScheme.tertiary
        elapsedFraction != null -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isOverdue)
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        )
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                EmojiBadge(state.task.emoji)
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        state.task.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(statusColor)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            RelativeTimeFormatter.format(state.lastLog?.timestamp),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isOverdue) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (hasActiveRoutine && interval != null) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "🔁 ${buildIntervalText(interval)}" +
                                (routine.preferredHourOfDay?.let { " · around $it:00" } ?: ""),
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
            // Thin progress strip: how far through the routine interval we are
            if (elapsedFraction != null) {
                LinearProgressIndicator(
                    progress = { elapsedFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                    color = statusColor,
                    trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)
                )
            }
        }
    }
}

@Composable
private fun EmojiBadge(emoji: String) {
    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)),
        contentAlignment = Alignment.Center
    ) {
        Text(emoji, fontSize = 22.sp)
    }
}

@Composable
fun AddWidgetHint() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "Long-press your home screen to add task or note widgets",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun EmptyState(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Text("🌱", fontSize = 44.sp)
            }
            Spacer(Modifier.height(20.dp))
            Text("No tasks yet", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "Add a task widget to your home screen\nand start tapping!",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun buildRoutineDescription(routine: Routine): String {
    val parts = mutableListOf<String>()
    routine.intervalHours?.let { parts.add(buildIntervalText(it).lowercase()) }
    routine.preferredHourOfDay?.let { parts.add("around ${it}:00") }
    routine.locationLabel?.let { parts.add("at $it") }
    return parts.joinToString(" · ").ifBlank { routine.type.name.lowercase() }
}

private fun buildIntervalText(hours: Int): String = when {
    hours < 24 -> "Every ${hours}h"
    hours < 48 -> "Daily"
    else -> "Every ${hours / 24} days"
}
