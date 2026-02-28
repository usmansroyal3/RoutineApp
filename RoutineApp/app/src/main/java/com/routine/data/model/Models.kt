package com.routine.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// ─── Task ────────────────────────────────────────────────────────────────────

@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val emoji: String = "🌱",         // visual identity for the widget
    val widgetId: Int = -1,
    val createdAt: Long = System.currentTimeMillis()
)

// ─── TaskLog ─────────────────────────────────────────────────────────────────

@Entity(
    tableName = "task_logs",
    foreignKeys = [ForeignKey(
        entity = Task::class,
        parentColumns = ["id"],
        childColumns = ["taskId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("taskId")]
)
data class TaskLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long,
    val timestamp: Long = System.currentTimeMillis(),
    val lat: Double? = null,
    val lng: Double? = null,
    val locationLabel: String? = null   // "Home", "Work", inferred from reverse geocode
)

// ─── Routine ─────────────────────────────────────────────────────────────────

enum class RoutineType { STRICT, LOCATION_TRIGGERED, LOOSE }
enum class RoutineStatus { PROPOSED, ACTIVE, SNOOZED, DISMISSED }

@Entity(
    tableName = "routines",
    foreignKeys = [ForeignKey(
        entity = Task::class,
        parentColumns = ["id"],
        childColumns = ["taskId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("taskId")]
)
data class Routine(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long,
    val type: RoutineType,
    val intervalHours: Int? = null,         // e.g. 48 = every 2 days
    val preferredHourOfDay: Int? = null,    // e.g. 8 = 8am
    val locationLabel: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val radiusMeters: Float = 200f,
    val status: RoutineStatus = RoutineStatus.PROPOSED,
    val confidence: Float = 0f,             // 0.0 – 1.0
    val lastRemindedAt: Long? = null,
    val snoozeUntil: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

// ─── Note ────────────────────────────────────────────────────────────────────

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val widgetId: Int = -1,
    val content: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

// ─── Reminder ────────────────────────────────────────────────────────────────

@Entity(
    tableName = "reminders",
    foreignKeys = [ForeignKey(
        entity = Note::class,
        parentColumns = ["id"],
        childColumns = ["noteId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("noteId")]
)
data class Reminder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val noteId: Long,
    val taskDescription: String,
    val triggerAt: Long,
    val isRecurring: Boolean = false,
    val recurringIntervalHours: Int? = null,
    val fired: Boolean = false,
    val dismissed: Boolean = false,
    val originalPhrase: String? = null     // "EOD", "in 2 hours" — for display
)

// ─── Domain helpers ──────────────────────────────────────────────────────────

data class TaskWithLastLog(
    val task: Task,
    val lastLog: TaskLog?,
    val routine: Routine?
) {
    val hoursSinceLastDone: Long?
        get() = lastLog?.let {
            (System.currentTimeMillis() - it.timestamp) / 3_600_000
        }

    val isOverdue: Boolean
        get() {
            val routine = routine ?: return false
            val hours = hoursSinceLastDone ?: return false
            val interval = routine.intervalHours ?: return false
            return routine.status == RoutineStatus.ACTIVE && hours > interval
        }
}

data class NoteWithReminders(
    val note: Note,
    val reminders: List<Reminder>
)

data class ParsedReminder(
    val task: String,
    val date: String,        // "today", "tomorrow", "YYYY-MM-DD"
    val time: String?,       // "HH:mm" 24hr
    val relative: String?,   // original phrase
    val recurring: Boolean
)
