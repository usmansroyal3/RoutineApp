package com.routine.data.repository

import com.routine.data.db.dao.*
import com.routine.data.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

// ─── TaskRepository ──────────────────────────────────────────────────────────

@Singleton
class TaskRepository @Inject constructor(
    private val taskDao: TaskDao,
    private val taskLogDao: TaskLogDao,
    private val routineDao: RoutineDao
) {
    fun observeAllTasks(): Flow<List<Task>> = taskDao.observeAll()

    suspend fun createTask(name: String, emoji: String): Long =
        taskDao.insert(Task(name = name, emoji = emoji))

    suspend fun getTask(id: Long): Task? = taskDao.getById(id)

    suspend fun getTaskByWidget(widgetId: Int): Task? = taskDao.getByWidgetId(widgetId)

    suspend fun assignWidget(taskId: Long, widgetId: Int) =
        taskDao.assignWidget(taskId, widgetId)

    suspend fun deleteTask(task: Task) = taskDao.delete(task)

    suspend fun logTask(taskId: Long, lat: Double?, lng: Double?, locationLabel: String?): Long {
        return taskLogDao.insert(
            TaskLog(taskId = taskId, lat = lat, lng = lng, locationLabel = locationLabel)
        )
    }

    suspend fun getRecentLogs(taskId: Long, limit: Int = 10): List<TaskLog> =
        taskLogDao.getRecentLogs(taskId, limit)

    suspend fun getLastLog(taskId: Long): TaskLog? = taskLogDao.getLastLog(taskId)

    suspend fun getLogCount(taskId: Long): Int = taskLogDao.getLogCount(taskId)

    fun observeTasksWithState(): Flow<List<TaskWithLastLog>> {
        return combine(
            taskDao.observeAll(),
            taskLogDao.observeAll(),
            routineDao.observeAll()
        ) { tasks, logs, routines ->
            val latestLogByTask = logs.groupBy(TaskLog::taskId)
                .mapValues { (_, taskLogs) -> taskLogs.maxByOrNull(TaskLog::timestamp) }
            val latestRoutineByTask = routines.groupBy(Routine::taskId)
                .mapValues { (_, taskRoutines) -> taskRoutines.maxByOrNull(Routine::createdAt) }

            tasks.map { task ->
                TaskWithLastLog(
                    task = task,
                    lastLog = latestLogByTask[task.id],
                    routine = latestRoutineByTask[task.id]
                )
            }
        }
    }
}

// ─── RoutineRepository ───────────────────────────────────────────────────────

@Singleton
class RoutineRepository @Inject constructor(
    private val routineDao: RoutineDao
) {
    suspend fun insert(routine: Routine): Long = routineDao.insert(routine)

    suspend fun getForTask(taskId: Long): Routine? = routineDao.getForTask(taskId)

    suspend fun activate(routineId: Long) =
        routineDao.updateStatus(routineId, RoutineStatus.ACTIVE)

    suspend fun dismiss(routineId: Long) =
        routineDao.updateStatus(routineId, RoutineStatus.DISMISSED)

    suspend fun snooze(routineId: Long, untilMillis: Long) =
        routineDao.snooze(routineId, untilMillis)

    // Pause reminders without changing the routine's ACTIVE status
    suspend fun snoozeKeepActive(routineId: Long, untilMillis: Long) =
        routineDao.setSnoozeUntil(routineId, untilMillis)

    suspend fun update(routine: Routine) = routineDao.update(routine)

    suspend fun getActiveRoutines(): List<Routine> = routineDao.getActiveRoutines()

    suspend fun markReminded(routineId: Long) =
        routineDao.updateLastReminded(routineId, System.currentTimeMillis())

    fun observeProposed(): Flow<List<Routine>> = routineDao.observeProposed()

    fun observeAll(): Flow<List<Routine>> = routineDao.observeAll()
}

// ─── NoteRepository ──────────────────────────────────────────────────────────

@Singleton
class NoteRepository @Inject constructor(
    private val noteDao: NoteDao,
    private val reminderDao: ReminderDao
) {
    fun observeAllNotes(): Flow<List<Note>> = noteDao.observeAll()

    suspend fun saveNote(note: Note): Long {
        return if (note.id == 0L) {
            noteDao.insert(note)
        } else {
            noteDao.update(note.copy(updatedAt = System.currentTimeMillis()))
            note.id
        }
    }

    suspend fun getNote(id: Long): Note? = noteDao.getById(id)

    suspend fun getNoteByWidget(widgetId: Int): Note? = noteDao.getByWidgetId(widgetId)

    suspend fun assignWidget(noteId: Long, widgetId: Int) =
        noteDao.assignWidget(noteId, widgetId)

    suspend fun deleteNote(note: Note) = noteDao.delete(note)

    suspend fun insertReminders(reminders: List<Reminder>) =
        reminderDao.insertAll(reminders)

    suspend fun getRemindersForNote(noteId: Long): List<Reminder> =
        reminderDao.getForNote(noteId)

    suspend fun getPendingReminders(): List<Reminder> = reminderDao.getPending()

    suspend fun markReminderFired(id: Long) = reminderDao.markFired(id)

    suspend fun markReminderDismissed(id: Long) = reminderDao.markDismissed(id)

    suspend fun getReminder(id: Long): Reminder? = reminderDao.getById(id)

    fun observePendingReminders(): Flow<List<Reminder>> = reminderDao.observePending()

    suspend fun getNoteWithReminders(noteId: Long): NoteWithReminders? {
        val note = noteDao.getById(noteId) ?: return null
        val reminders = reminderDao.getForNote(noteId)
        return NoteWithReminders(note, reminders)
    }
}
