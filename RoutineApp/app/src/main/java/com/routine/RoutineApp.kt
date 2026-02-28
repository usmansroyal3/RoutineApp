package com.routine

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.routine.worker.CHANNEL_REMINDER
import com.routine.worker.CHANNEL_ROUTINE
import com.routine.worker.RoutineCheckWorker
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class RoutineApp : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        RoutineCheckWorker.scheduleRepeating(this)
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
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
}
