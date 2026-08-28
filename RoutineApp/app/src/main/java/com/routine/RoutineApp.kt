package com.routine

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.routine.worker.CHANNEL_REMINDER
import com.routine.worker.CHANNEL_ROUTINE
import com.routine.worker.RoutineCheckWorker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class RoutineApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        RoutineCheckWorker.scheduleRepeating(this)
    }

    private fun createNotificationChannels() {
        val routineChannel = NotificationChannel(
            CHANNEL_ROUTINE,
            "Routine Reminders",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Notifications for overdue routines and pattern proposals"
        }

        val reminderChannel = NotificationChannel(
            CHANNEL_REMINDER,
            "Note Reminders",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Reminders extracted from your notes"
            enableVibration(true)
        }

        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(routineChannel)
        nm.createNotificationChannel(reminderChannel)
    }
}
