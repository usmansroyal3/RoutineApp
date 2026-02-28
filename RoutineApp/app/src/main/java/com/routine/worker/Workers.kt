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
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.AndroidEntryPoint
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
        postRoutineProposalNotification(taskId)
        return Result.success()
    }

    private fun postRoutineProposalNotification(taskId: Long) {
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_tab", "routines")
        }
        val pi = PendingIntent.getActivity(
            applicationContext, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ROUTINE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Routine detected 🎯")
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
// Runs periodically (every hour). Checks if any active routine is overdue
// and sends an escalating notification.

@HiltWorker
class RoutineCheckWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val routineRepository: RoutineRepository,
    private val taskRepository: TaskRepository
) : CoroutineWorker(context, workerParams) {

    companion object {
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
        val active = routineRepository.getActiveRoutines()
        val now = System.currentTimeMillis()

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

            val task = taskRepository.getTask(routine.taskId) ?: return@forEach
            postOverdueNotification(task.name, task.emoji, routine, level)
            routineRepository.markReminded(routine.id)
        }

        return Result.success()
    }

    private fun postOverdueNotification(
        taskName: String,
        emoji: String,
        routine: Routine,
        level: Int
    ) {
        val messages = listOf(
            "Time to $taskName $emoji",
            "Don't forget — $taskName $emoji",
            "⚠️ $taskName is overdue! $emoji"
        )
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            applicationContext, routine.id.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ROUTINE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(messages[level])
            .setContentText("Tap the widget when done ✓")
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setPriority(if (level >= 2) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .build()

        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_OVERDUE + routine.id.toInt(), notification)
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
        // Fire and forget on main thread via coroutine — use goAsync for production
        val pending = goAsync()
        kotlinx.coroutines.GlobalScope.kotlinx.coroutines.launch {
            noteRepository.markReminderDismissed(reminderId)
            pending.finish()
        }
    }
}

// ─── Notification channel IDs ────────────────────────────────────────────────

const val CHANNEL_ROUTINE = "routine_channel"
const val CHANNEL_REMINDER = "reminder_channel"
const val NOTIF_ROUTINE_PROPOSAL = 1000
const val NOTIF_OVERDUE = 2000
const val NOTIF_REMINDER = 3000
