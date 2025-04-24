package com.example.gigs

import android.app.Application
import android.content.Context
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class GigsApp : Application() {

    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext // ✅ assign the global app context
    }

    companion object {
        lateinit var appContext: Context
            private set
    }
}
