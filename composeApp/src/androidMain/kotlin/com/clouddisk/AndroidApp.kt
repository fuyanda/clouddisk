package com.clouddisk

import android.app.Application

class AndroidApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: AndroidApp
            private set
    }
}
