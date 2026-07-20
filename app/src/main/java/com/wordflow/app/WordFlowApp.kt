package com.wordflow.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.wordflow.app.di.AppContainer

class WordFlowApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        createReminderChannel()
    }

    private fun createReminderChannel() {
        val channel = NotificationChannel(
            REMINDER_CHANNEL_ID,
            getString(R.string.channel_reminder_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = getString(R.string.channel_reminder_desc)
        }
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    companion object {
        const val REMINDER_CHANNEL_ID = "daily_reminder"
    }
}
