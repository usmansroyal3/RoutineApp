package com.routine.domain

import android.content.Context
import androidx.work.*
import com.routine.data.model.ParsedReminder
import com.routine.data.model.Reminder
import com.routine.data.repository.NoteRepository
import com.routine.worker.ReminderFireWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val noteRepository: NoteRepository
) {
    private val workManager = WorkManager.getInstance(context)

    /**
     * Convert ParsedReminders → Reminder entities → schedule WorkManager jobs
     */
    suspend fun scheduleFromParsed(noteId: Long, parsed: List<ParsedReminder>) {
        val reminders = parsed.mapNotNull { pr ->
            val triggerAt = resolveTriggerMillis(pr) ?: return@mapNotNull null
            Reminder(
                noteId = noteId,
                taskDescription = pr.task,
                triggerAt = triggerAt,
                isRecurring = pr.recurring,
                originalPhrase = pr.relative
            )
        }

        noteRepository.insertReminders(reminders)

        // Re-fetch to get IDs
        val saved = noteRepository.getRemindersForNote(noteId)
        saved.filter { !it.fired && !it.dismissed }.forEach { scheduleReminder(it) }
    }

    fun scheduleReminder(reminder: Reminder) {
        val delay = reminder.triggerAt - System.currentTimeMillis()
        if (delay <= 0) return

        val data = workDataOf(
            ReminderFireWorker.KEY_REMINDER_ID to reminder.id,
            ReminderFireWorker.KEY_TASK_DESCRIPTION to reminder.taskDescription,
            ReminderFireWorker.KEY_NOTE_ID to reminder.noteId
        )

        val request = OneTimeWorkRequestBuilder<ReminderFireWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(data)
            .addTag("reminder_${reminder.id}")
            .build()

        workManager.enqueueUniqueWork(
            "reminder_${reminder.id}",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun cancelReminder(reminderId: Long) {
        workManager.cancelUniqueWork("reminder_$reminderId")
    }

    private fun resolveTriggerMillis(pr: ParsedReminder): Long? {
        val cal = Calendar.getInstance()

        // Set date
        when (pr.date) {
            "today" -> { /* current date */ }
            "tomorrow" -> cal.add(Calendar.DAY_OF_YEAR, 1)
            else -> {
                return try {
                    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    val parsed = sdf.parse(pr.date) ?: return null
                    cal.time = parsed
                    if (pr.time != null) applyTime(cal, pr.time)
                    cal.timeInMillis
                } catch (e: Exception) { null }
            }
        }

        // Set time
        if (pr.time != null) {
            applyTime(cal, pr.time)
        } else {
            // Default to now + 1 hour if no time specified
            cal.add(Calendar.HOUR_OF_DAY, 1)
        }

        return cal.timeInMillis
    }

    private fun applyTime(cal: Calendar, time: String) {
        val parts = time.split(":")
        if (parts.size >= 2) {
            cal.set(Calendar.HOUR_OF_DAY, parts[0].toIntOrNull() ?: 9)
            cal.set(Calendar.MINUTE, parts[1].toIntOrNull() ?: 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
        }
    }
}
