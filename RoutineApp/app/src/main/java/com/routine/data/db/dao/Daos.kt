package com.routine.data.db.dao

import androidx.room.*
import com.routine.data.model.*
import kotlinx.coroutines.flow.Flow

// ─── TaskDao ─────────────────────────────────────────────────────────────────

@Dao
interface TaskDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: Task): Long

    @Update
    suspend fun update(task: Task)

    @Delete
    suspend fun delete(task: Task)

    @Query("SELECT * FROM tasks ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getById(id: Long): Task?

    @Query("SELECT * FROM tasks WHERE widgetId = :widgetId LIMIT 1")
    suspend fun getByWidgetId(widgetId: Int): Task?

    @Query("UPDATE tasks SET widgetId = :widgetId WHERE id = :taskId")
    suspend fun assignWidget(taskId: Long, widgetId: Int)
}

// ─── TaskLogDao ───────────────────────────────────────────────────────────────

@Dao
interface TaskLogDao {
    @Insert
    suspend fun insert(log: TaskLog): Long

    @Query("SELECT * FROM task_logs WHERE taskId = :taskId ORDER BY timestamp DESC")
    suspend fun getLogsForTask(taskId: Long): List<TaskLog>

    @Query("SELECT * FROM task_logs WHERE taskId = :taskId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentLogs(taskId: Long, limit: Int): List<TaskLog>

    @Query("SELECT * FROM task_logs WHERE taskId = :taskId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastLog(taskId: Long): TaskLog?

    @Query("SELECT COUNT(*) FROM task_logs WHERE taskId = :taskId")
    suspend fun getLogCount(taskId: Long): Int

    @Query("SELECT * FROM task_logs ORDER BY timestamp DESC LIMIT 1")
    fun observeLastLog(taskId: Long): Flow<TaskLog?>
}

// ─── RoutineDao ───────────────────────────────────────────────────────────────

@Dao
interface RoutineDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(routine: Routine): Long

    @Update
    suspend fun update(routine: Routine)

    @Query("SELECT * FROM routines WHERE taskId = :taskId ORDER BY createdAt DESC LIMIT 1")
    suspend fun getForTask(taskId: Long): Routine?

    @Query("SELECT * FROM routines WHERE status = 'ACTIVE'")
    suspend fun getActiveRoutines(): List<Routine>

    @Query("SELECT * FROM routines WHERE status = 'PROPOSED'")
    fun observeProposed(): Flow<List<Routine>>

    @Query("UPDATE routines SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: RoutineStatus)

    @Query("UPDATE routines SET lastRemindedAt = :time WHERE id = :id")
    suspend fun updateLastReminded(id: Long, time: Long)

    @Query("UPDATE routines SET snoozeUntil = :until, status = 'SNOOZED' WHERE id = :id")
    suspend fun snooze(id: Long, until: Long)

    @Query("UPDATE routines SET snoozeUntil = :until WHERE id = :id")
    suspend fun setSnoozeUntil(id: Long, until: Long)

    @Query("SELECT * FROM routines")
    fun observeAll(): Flow<List<Routine>>
}

// ─── NoteDao ─────────────────────────────────────────────────────────────────

@Dao
interface NoteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: Note): Long

    @Update
    suspend fun update(note: Note)

    @Delete
    suspend fun delete(note: Note)

    @Query("SELECT * FROM notes ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getById(id: Long): Note?

    @Query("SELECT * FROM notes WHERE widgetId = :widgetId LIMIT 1")
    suspend fun getByWidgetId(widgetId: Int): Note?

    @Query("UPDATE notes SET widgetId = :widgetId WHERE id = :noteId")
    suspend fun assignWidget(noteId: Long, widgetId: Int)
}

// ─── ReminderDao ─────────────────────────────────────────────────────────────

@Dao
interface ReminderDao {
    @Insert
    suspend fun insert(reminder: Reminder): Long

    @Insert
    suspend fun insertAll(reminders: List<Reminder>)

    @Update
    suspend fun update(reminder: Reminder)

    @Query("SELECT * FROM reminders WHERE noteId = :noteId ORDER BY triggerAt ASC")
    suspend fun getForNote(noteId: Long): List<Reminder>

    @Query("SELECT * FROM reminders WHERE fired = 0 AND dismissed = 0 ORDER BY triggerAt ASC")
    suspend fun getPending(): List<Reminder>

    @Query("UPDATE reminders SET fired = 1 WHERE id = :id")
    suspend fun markFired(id: Long)

    @Query("UPDATE reminders SET dismissed = 1 WHERE id = :id")
    suspend fun markDismissed(id: Long)

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun getById(id: Long): Reminder?

    @Query("SELECT * FROM reminders WHERE fired = 0 AND dismissed = 0")
    fun observePending(): Flow<List<Reminder>>
}
