package com.ngapp.simplevideofilemanager.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationManagerCompat

object NotificationChannels {

    const val BACKGROUND_CHANNEL_ID = "background"

    fun create(context: Context) {
        if (haveO()) {
            createBackgroundChannel(context)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createBackgroundChannel(context: Context) {
        val name = "Background"
        val channelDescription = "Running background processes"
        val priority = NotificationManager.IMPORTANCE_HIGH

        val channel = NotificationChannel(BACKGROUND_CHANNEL_ID, name, priority).apply {
            description = channelDescription
            setSound(null, null)
        }

        NotificationManagerCompat.from(context).createNotificationChannel(channel)
    }
}