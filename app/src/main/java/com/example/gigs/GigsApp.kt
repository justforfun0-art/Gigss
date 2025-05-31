package com.example.gigs

import android.app.Application
import android.content.Context
import android.util.Log
import com.example.gigs.data.util.PerformanceUtils
import dagger.hilt.android.HiltAndroidApp
import java.util.Timer
import java.util.TimerTask

@HiltAndroidApp
class GigsApp : Application() {

    override fun onCreate() {
        super.onCreate()
        setupPerformanceMonitoring()

        appContext = applicationContext // ✅ assign the global app context
    }

    companion object {
        lateinit var appContext: Context
            private set
    }

    private fun setupPerformanceMonitoring() {
        // Clear any existing metrics
        PerformanceUtils.PerformanceMetrics.clearMetrics()

        // Set up periodic performance logging (debug builds only)
        if (BuildConfig.DEBUG) {
            // Log performance metrics every 30 seconds
            Timer().schedule(object : TimerTask() {
                override fun run() {
                    Log.d("PerformanceMonitor", "=== PERFORMANCE REPORT ===")
                    PerformanceUtils.PerformanceMetrics.logMetrics()
                    PerformanceUtils.MemoryMonitor.logMemoryUsage("GigsApp")
                    Log.d("PerformanceMonitor", "========================")
                }
            }, 30000, 30000)
        }

        // Set up memory monitoring
        PerformanceUtils.MemoryMonitor.addLowMemoryCallback {
            Log.w("PerformanceMonitor", "LOW MEMORY DETECTED - Triggering cleanup")
            // The individual components will handle their own cleanup
        }
    }
}
