package com.routine.worker

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.google.android.gms.location.GeofencingEvent
import com.routine.R
import com.routine.data.model.Routine
import com.routine.data.model.RoutineStatus
import com.routine.data.model.RoutineType
import com.routine.data.repository.NoteRepository
import com.routine.data.repository.RoutineRepository
import com.routine.data.repository.TaskRepository
import com.routine.domain.PatternAnalyzer
import com.routine.domain.ReminderScheduler
import com.routine.ui.MainActivity
import com.routine.widget.task.TaskWidgetUpdateService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.concurrent.TimeUnit
import javax.inject.Inject

// ─── Pattern Analysis Worker ─────────────────────────────────────────────────
// Runs after each tap (if log count is a multiple of 3).
// Detects behavioral patterns and proposes routines.

@HiltWorker
class PatternAnalysisWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val taskRepository: TaskRepository,
    private val routineRepository: RoutineRepository,
    private val patternAnalyzer: PatternAnalyzer
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val KEY_TASK_ID = "task_id"

        fun enqueue(context: Context, taskId: Long) {
            val data = workDataOf(KEY_TASK_ID to taskId)
            val request = OneTimeWorkRequestBuilder<PatternAnalysisWorker>()
                .setInputData(data)
                .setConstraints(Constraints.Builder().build())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork("pattern_$taskId", ExistingWorkPolicy.REPLACE, request)
        }
    }

    override suspend fun doWork(): Result {
        val taskId = inputData.getLong(KEY_TASK_ID, -1L)
        if (taskId == -1L) return Result.failure()

        val logs = taskRepository.getRecentLogs(taskId, 10)
        val result = patternAnalyzer.analyze(logs) ?: return Result.success()

        if (!patternAnalyzer.shouldPropose(result)) return Result.success()

        // Only insert a new proposed routine if none exists or current is dismissed
        val existing = routineRepository.getForTask(taskId)
        if (existing != null && existing.status != RoutineStatus.DISMISSED) return Result.success()

        routineRepository.insert(
            Routine(
                taskId = taskId,
                type = result.suggestedType,
                intervalHours = result.intervalHours,
                preferredHourOfDay = result.preferredHourOfDay,
                locationLabel = result.locationLabel,
                lat = result.lat,
                lng = result.lng,
                status = RoutineStatus.PROPOSED,
                confidence = result.confidence
            )
        )

        // Notify the user that a routine has been proposed
        val task = taskRepository.getTask(taskId)
        postRoutineProposalNotification(taskId, task?.name, task?.emoji)
        return Result.success()
    }

    private fun postRoutineProposalNotification(taskId: Long, taskName: String?, emoji: String?) {
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_tab", "routines")
        }
        val pi = PendingIntent.getActivity(
            applicationContext, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val title = if (taskName != null) "Found a rhythm for ${taskName} ${emoji ?: ""}".trim()
        else "Routine detected 🎯"
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ROUTINE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText("Tap to review and confirm your new routine")
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()

        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ROUTINE_PROPOSAL + taskId.toInt(), notification)
    }
}

// ─── Reminder Fire Worker ────────────────────────────────────────────────────
// Fires at the scheduled reminder time and posts a notification.

@HiltWorker
class ReminderFireWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val noteRepository: NoteRepository
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val KEY_REMINDER_ID = "reminder_id"
        const val KEY_TASK_DESCRIPTION = "task_description"
        const val KEY_NOTE_ID = "note_id"
    }

    override suspend fun doWork(): Result {
        val reminderId = inputData.getLong(KEY_REMINDER_ID, -1L)
        val taskDescription = inputData.getString(KEY_TASK_DESCRIPTION) ?: return Result.failure()
        val noteId = inputData.getLong(KEY_NOTE_ID, -1L)

        noteRepository.markReminderFired(reminderId)

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_note", noteId)
        }
        val pi = PendingIntent.getActivity(
            applicationContext, reminderId.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val dismissIntent = Intent(applicationContext, ReminderDismissReceiver::class.java).apply {
            putExtra("reminder_id", reminderId)
        }
        val dismissPi = PendingIntent.getBroadcast(
            applicationContext, reminderId.toInt(), dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_REMINDER)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("⏰ Reminder")
            .setContentText(taskDescription)
            .setStyle(NotificationCompat.BigTextStyle().bigText(taskDescription))
            .setContentIntent(pi)
            .addAction(0, "Done", dismissPi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_REMINDER + reminderId.toInt(), notification)

        return Result.success()
    }
}

// ─── Routine Check Worker ────────────────────────────────────────────────────
// Runs periodically (every hour) but nudges sparingly:
//  - never during quiet hours (22:00–08:00)
//  - the first nudge waits for the hour you usually do the task
//  - after a nudge, stays silent for a cooldown period instead of re-firing
//    every hour (the old behavior that made notifications feel spammy)

@HiltWorker
class RoutineCheckWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val routineRepository: RoutineRepository,
    private val taskRepository: TaskRepository
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val QUIET_HOUR_START = 22
        const val QUIET_HOUR_END = 8

        fun scheduleRepeating(context: Context) {
            val request = PeriodicWorkRequestBuilder<RoutineCheckWorker>(1, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "routine_check",
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }

    override suspend fun doWork(): Result {
        val now = System.currentTimeMillis()
        val hourOfDay = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

        if (hourOfDay >= QUIET_HOUR_START || hourOfDay < QUIET_HOUR_END) return Result.success()

        val active = routineRepository.getActiveRoutines()

        active.forEach { routine ->
            // Skip if snoozed
            if (routine.snoozeUntil != null && now < routine.snoozeUntil) return@forEach

            val lastLog = taskRepository.getLastLog(routine.taskId) ?: return@forEach
            val hoursSince = (now - lastLog.timestamp) / 3_600_000

            val interval = routine.intervalHours ?: return@forEach
            if (hoursSince < interval) return@forEach

            // Calculate escalation level based on how overdue
            val overdueHours = hoursSince - interval
            val level = when {
                overdueHours > interval -> 2     // very overdue
                overdueHours > interval / 2 -> 1 // moderately overdue
                else -> 0                         // just overdue
            }

            // Cooldown: if we already nudged for this cycle, wait before nudging
            // again. Very overdue tasks back off further to twice a day at most.
            val lastReminded = routine.lastRemindedAt
            if (lastReminded != null && lastReminded > lastLog.timestamp) {
                val hoursSinceReminder = (now - lastReminded) / 3_600_000
                val cooldownHours =
                    if (level >= 2) 48L
                    else (interval / 2L).coerceIn(8L, 24L)
                if (hoursSinceReminder < cooldownHours) return@forEach
            }

            // First nudge waits for the time of day the user usually does the task
            if (level == 0) {
                val preferred = routine.preferredHourOfDay
                if (preferred != null && hourOfDay < preferred) return@forEach
            }

            val task = taskRepository.getTask(routine.taskId) ?: return@forEach
            postOverdueNotification(task.id, task.name, task.emoji, routine, level, interval.toLong(), hoursSince)
            routineRepository.markReminded(routine.id)
        }

        return Result.success()
    }

    private fun postOverdueNotification(
        taskId: Long,
        taskName: String,
        emoji: String,
        routine: Routine,
        level: Int,
        intervalHours: Long,
        hoursSince: Long
    ) {
        val notificationId = NOTIF_OVERDUE + routine.id.toInt()
        val title = when (level) {
            0 -> "$emoji Time for $taskName"
            1 -> "$emoji Still pending: $taskName"
            else -> "$emoji $taskName is overdue"
        }
        val body = when (level) {
            0 -> "You usually do this ${everyText(intervalHours)} — it's been ${spanText(hoursSince)}."
            1 -> "It's been ${spanText(hoursSince)}, a bit longer than your usual ${cadenceText(intervalHours)}."
            else -> "It's been ${spanText(hoursSince)}. You usually do this ${everyText(intervalHours)}."
        }

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            applicationContext, routine.id.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val doneIntent = Intent(applicationContext, RoutineDoneReceiver::class.java).apply {
            putExtra("task_id", taskId)
            putExtra("notification_id", notificationId)
        }
        val donePi = PendingIntent.getBroadcast(
            applicationContext, notificationId, doneIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snoozeIntent = Intent(applicationContext, RoutineSnoozeReceiver::class.java).apply {
            putExtra("routine_id", routine.id)
            putExtra("notification_id", notificationId)
        }
        val snoozePi = PendingIntent.getBroadcast(
            applicationContext, notificationId + 1, snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ROUTINE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pi)
            .addAction(0, "Done ✓", donePi)
            .addAction(0, "Snooze 1 day", snoozePi)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setPriority(if (level >= 2) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .build()

        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(notificationId, notification)
    }

    // "it's been 5 days" / "it's been 18 hours"
    private fun spanText(hours: Long): String = when {
        hours < 2 -> "about an hour"
        hours < 24 -> "$hours hours"
        hours < 48 -> "a day"
        else -> "${hours / 24} days"
    }

    // "every 6 hours" / "daily" / "every 4 days"
    private fun everyText(hours: Long): String = when {
        hours < 24 -> "every $hours hours"
        hours < 48 -> "daily"
        else -> "every ${hours / 24} days"
    }

    // "6-hour rhythm" / "daily rhythm" / "4-day rhythm"
    private fun cadenceText(hours: Long): String = when {
        hours < 24 -> "$hours-hour rhythm"
        hours < 48 -> "daily rhythm"
        else -> "${hours / 24}-day rhythm"
    }
}

// ─── Geofence Receiver ───────────────────────────────────────────────────────

@AndroidEntryPoint
class GeofenceBroadcastReceiver : BroadcastReceiver() {

    @Inject lateinit var routineRepository: RoutineRepository
    @Inject lateinit var taskRepository: TaskRepository

    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) {
            Log.e("Geofence", "Error code: ${event.errorCode}")
            return
        }

        val triggeringGeofences = event.triggeringGeofences ?: return
        // Each geofence ID is "routine_{routineId}"
        triggeringGeofences.forEach { geofence ->
            val routineId = geofence.requestId.removePrefix("routine_").toLongOrNull() ?: return@forEach
            // Post a reminder notification (handled by RoutineCheckWorker on next run)
            Log.d("Geofence", "Entered zone for routine $routineId")
        }
    }
}

// ─── Reminder Dismiss Receiver ───────────────────────────────────────────────

@AndroidEntryPoint
class ReminderDismissReceiver : BroadcastReceiver() {

    @Inject lateinit var noteRepository: NoteRepository

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getLongExtra("reminder_id", -1L)
        if (reminderId == -1L) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                noteRepository.markReminderDismissed(reminderId)
            } finally {
                pending.finish()
            }
        }
    }
}

// ─── Routine "Done" Receiver ─────────────────────────────────────────────────
// "Done ✓" on an overdue notification logs the task just like tapping the
// widget would, then refreshes the widget so it shows "Just now".

@AndroidEntryPoint
class RoutineDoneReceiver : BroadcastReceiver() {

    @Inject lateinit var taskRepository: TaskRepository

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra("task_id", -1L)
        val notificationId = intent.getIntExtra("notification_id", -1)
        if (taskId == -1L) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                taskRepository.logTask(taskId, null, null, null)
                TaskWidgetUpdateService.refreshTask(context, taskId)
            } finally {
                if (notificationId != -1) {
                    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    nm.cancel(notificationId)
                }
                pending.finish()
            }
        }
    }
}

// ─── Routine Snooze Receiver ─────────────────────────────────────────────────
// Pauses reminders for the routine for 24h without deactivating it.

@AndroidEntryPoint
class RoutineSnoozeReceiver : BroadcastReceiver() {

    @Inject lateinit var routineRepository: RoutineRepository

    override fun onReceive(context: Context, intent: Intent) {
        val routineId = intent.getLongExtra("routine_id", -1L)
        val notificationId = intent.getIntExtra("notification_id", -1)
        if (routineId == -1L) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                routineRepository.snoozeKeepActive(routineId, System.currentTimeMillis() + 24 * 3_600_000L)
            } finally {
                if (notificationId != -1) {
                    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    nm.cancel(notificationId)
                }
                pending.finish()
            }
        }
    }
}

// ─── Notification channel IDs ────────────────────────────────────────────────

const val CHANNEL_ROUTINE = "routine_channel"
const val CHANNEL_REMINDER = "reminder_channel"
const val NOTIF_ROUTINE_PROPOSAL = 1000
const val NOTIF_OVERDUE = 2000
const val NOTIF_REMINDER = 3000
