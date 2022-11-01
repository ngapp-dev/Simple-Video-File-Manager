package com.ngapp.simplevideofilemanager.app

import android.app.Application
import com.ngapp.simplevideofilemanager.utils.NotificationChannels

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.create(this)
    }
}