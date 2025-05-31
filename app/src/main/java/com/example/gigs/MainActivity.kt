package com.example.gigs

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.example.gigs.data.util.PerformanceUtils
import com.example.gigs.navigation.AppNavHost
import com.example.gigs.ui.theme.GigsTheme
import com.example.gigs.data.repository.AuthRepository
import com.example.gigs.data.repository.ProfileRepository
import com.example.gigs.viewmodel.JobViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import java.util.*
import javax.inject.Inject
import kotlinx.coroutines.flow.take

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val TAG = "MainActivity"
    private var appStartTime: Long = 0
    private var memoryCallback: (() -> Unit)? = null
    private var performanceTimer: Timer? = null

    // 🚀 PERFORMANCE FIX: Preload critical dependencies
    @Inject lateinit var authRepository: AuthRepository
    @Inject lateinit var profileRepository: ProfileRepository

    // Preloading scope - separate from UI to avoid blocking
    private val preloadScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Track startup but don't block on it
        val appStartTime = PerformanceUtils.PerformanceMetrics.startTiming("app_startup", "general")

        enableEdgeToEdge()

        setContent {
            GigsTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavHost(navController = rememberNavController())

                    // Background operations after UI is rendered
                    LaunchedEffect(Unit) {
                        // Setup monitoring immediately (lightweight)
                        setupPerformanceMonitoring()

                        // Heavy data loading in background
                        launch(Dispatchers.IO) {
                            startBackgroundDataLoading()
                        }

                        // End startup timing
                        PerformanceUtils.PerformanceMetrics.endTiming("app_startup", appStartTime)
                    }
                }
            }
        }
    }


    private suspend fun startBackgroundDataLoading() {
        try {
            withContext(Dispatchers.IO) {
                // Load only critical data, limit timeout
                withTimeoutOrNull(2000) {
                    val userId = authRepository.getCurrentUserId()
                    if (userId != null) {
                        // Preload user profile only if needed
                        profileRepository.getEmployeeProfileByUserId(userId).take(1).collect { }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Background loading error (non-blocking): ${e.message}")
        }
    }



/**
     * 🚀 PERFORMANCE FIX: Preload critical data without blocking UI
     */
    private fun startEssentialDataPreloading() {
        preloadScope.launch {
            val preloadStartTime = PerformanceUtils.PerformanceMetrics.startTiming("preload_data", "database")

            try {
                Log.d(TAG, "Starting essential data preloading...")

                // Preload user authentication state
                val userId = withTimeoutOrNull(2000) { // 2 second timeout
                    authRepository.getCurrentUserId()
                }

                if (userId != null) {
                    // Preload user profile in parallel
                    val profileJob = async {
                        try {
                            profileRepository.getEmployeeProfileByUserId(userId).collect { result ->
                                if (result.isSuccess) {
                                    Log.d(TAG, "Preloaded user profile successfully")
                                } else {
                                    Log.w(TAG, "Failed to preload user profile: ${result.exceptionOrNull()?.message}")
                                }
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Exception preloading profile: ${e.message}")
                        }
                    }

                    // Wait for profile with timeout
                    withTimeoutOrNull(3000) {
                        profileJob.await()
                    }
                }

                Log.d(TAG, "Essential data preloading completed")

            } catch (e: Exception) {
                Log.w(TAG, "Error during preloading (non-blocking): ${e.message}")
            } finally {
                PerformanceUtils.PerformanceMetrics.endTiming("preload_data", preloadStartTime)
            }
        }
    }

    /**
     * 🚀 PERFORMANCE FIX: Optimized navigation setup with lazy initialization
     */
    @Composable
    private fun OptimizedNavigationSetup(onNavigationReady: () -> Unit) {
        val navigationStartTime = remember {
            PerformanceUtils.PerformanceMetrics.startTiming("navigation_setup", "ui")
        }

        // 🚀 FIX: Use lazy initialization for NavController
        val navController = rememberNavController()
        // 🚀 FIX: Initialize navigation immediately without delays
        LaunchedEffect(navController) {
            // End navigation timing immediately
            PerformanceUtils.PerformanceMetrics.endTiming("navigation_setup", navigationStartTime)

            // Call the ready callback immediately
            onNavigationReady()

            // Log performance report after a short delay (non-blocking)
            if (BuildConfig.DEBUG) {
                launch {
                    delay(1000) // Non-blocking delay for debug logging only
                    PerformanceDebugUtils.logPerformanceReport()
                }
            }
        }

        // 🚀 FIX: Direct navigation without wrapper delays
        AppNavHost(navController = navController)

        // PERFORMANCE MONITORING: Clean up when activity is destroyed
        DisposableEffect(Unit) {
            onDispose {
                Log.d(TAG, "MainActivity Compose disposing")
            }
        }
    }

    private fun setupPerformanceMonitoring() {
        Log.d(TAG, "Setting up performance monitoring")

        // Clear any existing metrics from previous sessions
        PerformanceUtils.PerformanceMetrics.clearMetrics()

        // Set up memory monitoring callback
        memoryCallback = {
            Log.w(TAG, "LOW MEMORY DETECTED in MainActivity - triggering cleanup")
            // Memory cleanup will be handled by individual ViewModels and components
        }
        memoryCallback?.let { PerformanceUtils.MemoryMonitor.addLowMemoryCallback(it) }

        // 🚀 PERFORMANCE FIX: Reduced logging frequency to avoid overhead
        if (BuildConfig.DEBUG) {
            performanceTimer = Timer().apply {
                scheduleAtFixedRate(object : TimerTask() {
                    override fun run() {
                        try {
                            Log.d(TAG, "=== PERIODIC PERFORMANCE REPORT ===")
                            PerformanceUtils.PerformanceMetrics.logMetrics()
                            PerformanceUtils.MemoryMonitor.logMemoryUsage("GigsApp")

                            // 🚀 FIX: More efficient performance issue detection
                            checkPerformanceIssues()

                            Log.d(TAG, "=====================================")
                        } catch (e: Exception) {
                            Log.e(TAG, "Error in performance monitoring: ${e.message}")
                        }
                    }
                }, 30000, 30000) // Log every 30 seconds
            }
        }

        Log.d(TAG, "Performance monitoring setup completed")
    }

    /**
     * 🚀 PERFORMANCE FIX: More efficient performance issue detection
     */
    private fun checkPerformanceIssues() {
        val uiMetrics = PerformanceUtils.PerformanceMetrics.getMetricsByCategory("ui")
        val slowOperations = uiMetrics.filter { it.value > 16 } // More than 16ms

        if (slowOperations.isNotEmpty()) {
            Log.w(TAG, "⚠️ SLOW UI OPERATIONS DETECTED:")
            slowOperations.forEach { (operation, time) ->
                Log.w(TAG, "  - $operation: ${String.format("%.1f", time)}ms")
            }
        }

        // Check memory pressure
        if (PerformanceUtils.MemoryMonitor.isMemoryLow()) {
            Log.w(TAG, "⚠️ MEMORY PRESSURE DETECTED")
            PerformanceUtils.MemoryMonitor.triggerLowMemoryCleanup()
        }
    }

    override fun onStart() {
        super.onStart()
        val startTime = PerformanceUtils.PerformanceMetrics.startTiming("activity_start", "general")
        Log.d(TAG, "MainActivity onStart")
        PerformanceUtils.PerformanceMetrics.endTiming("activity_start", startTime)
    }

    override fun onResume() {
        super.onResume()
        val resumeTime = PerformanceUtils.PerformanceMetrics.startTiming("activity_resume", "general")

        Log.d(TAG, "MainActivity onResume")

        // Check memory pressure when app resumes
        if (PerformanceUtils.MemoryMonitor.isMemoryLow()) {
            Log.w(TAG, "Memory pressure detected on resume - triggering cleanup")
            PerformanceUtils.MemoryMonitor.triggerLowMemoryCleanup()
        }

        // Log memory status
        PerformanceUtils.MemoryMonitor.logMemoryUsage(TAG)

        PerformanceUtils.PerformanceMetrics.endTiming("activity_resume", resumeTime)
    }

    override fun onPause() {
        super.onPause()
        val pauseTime = PerformanceUtils.PerformanceMetrics.startTiming("activity_pause", "general")
        Log.d(TAG, "MainActivity onPause")
        PerformanceUtils.PerformanceMetrics.endTiming("activity_pause", pauseTime)
    }

    override fun onStop() {
        super.onStop()
        val stopTime = PerformanceUtils.PerformanceMetrics.startTiming("activity_stop", "general")
        Log.d(TAG, "MainActivity onStop")
        PerformanceUtils.PerformanceMetrics.endTiming("activity_stop", stopTime)
    }

    override fun onDestroy() {
        Log.d(TAG, "MainActivity onDestroy - cleaning up performance monitoring")

        // PERFORMANCE MONITORING: Final cleanup
        try {
            // 🚀 FIX: Cancel preloading to prevent memory leaks
            preloadScope.cancel()

            // Log final performance report
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "=== FINAL PERFORMANCE REPORT ===")
                PerformanceDebugUtils.logPerformanceReport()
                Log.d(TAG, "================================")
            }

            // Cancel periodic performance logging
            performanceTimer?.cancel()
            performanceTimer = null

            // Remove memory callback to prevent leaks
            memoryCallback?.let { PerformanceUtils.MemoryMonitor.removeLowMemoryCallback(it) }
            memoryCallback = null

            Log.d(TAG, "Performance monitoring cleanup completed")

        } catch (e: Exception) {
            Log.e(TAG, "Error during performance cleanup: ${e.message}")
        }

        super.onDestroy()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        Log.w(TAG, "System onLowMemory callback - triggering aggressive cleanup")
        PerformanceUtils.MemoryMonitor.triggerLowMemoryCleanup()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)

        when (level) {
            TRIM_MEMORY_RUNNING_MODERATE -> {
                Log.w(TAG, "Memory trim: RUNNING_MODERATE - light cleanup")
            }
            TRIM_MEMORY_RUNNING_LOW -> {
                Log.w(TAG, "Memory trim: RUNNING_LOW - moderate cleanup")
                PerformanceUtils.MemoryMonitor.triggerLowMemoryCleanup()
            }
            TRIM_MEMORY_RUNNING_CRITICAL -> {
                Log.w(TAG, "Memory trim: RUNNING_CRITICAL - aggressive cleanup")
                PerformanceUtils.MemoryMonitor.triggerLowMemoryCleanup()
            }
            TRIM_MEMORY_UI_HIDDEN -> {
                Log.d(TAG, "Memory trim: UI_HIDDEN - background cleanup")
            }
            TRIM_MEMORY_BACKGROUND -> {
                Log.d(TAG, "Memory trim: BACKGROUND - background cleanup")
            }
            TRIM_MEMORY_MODERATE -> {
                Log.w(TAG, "Memory trim: MODERATE - moderate cleanup")
                PerformanceUtils.MemoryMonitor.triggerLowMemoryCleanup()
            }
            TRIM_MEMORY_COMPLETE -> {
                Log.w(TAG, "Memory trim: COMPLETE - complete cleanup")
                PerformanceUtils.MemoryMonitor.triggerLowMemoryCleanup()
            }
        }

        // Log memory status after trim
        PerformanceUtils.MemoryMonitor.logMemoryUsage(TAG)
    }
}

/**
 * Performance debug utilities for MainActivity
 * 🚀 PERFORMANCE FIX: Enhanced with better memory tracking
 */
object PerformanceDebugUtils {

    /**
     * Call this from your debug menu or developer options
     */
    fun generatePerformanceReport(): String {
        val report = StringBuilder()

        report.appendLine("=== GIGS APP PERFORMANCE REPORT ===")
        report.appendLine("Generated at: ${System.currentTimeMillis()}")
        report.appendLine()

        // 🚀 FIX: More detailed memory stats
        val runtime = Runtime.getRuntime()
        val usedMemory = runtime.totalMemory() - runtime.freeMemory()
        val maxMemory = runtime.maxMemory()
        val memoryUsagePercent = (usedMemory.toFloat() / maxMemory.toFloat()) * 100

        report.appendLine("MEMORY STATUS:")
        report.appendLine("  Used: ${usedMemory / 1024 / 1024}MB")
        report.appendLine("  Free: ${runtime.freeMemory() / 1024 / 1024}MB")
        report.appendLine("  Total: ${runtime.totalMemory() / 1024 / 1024}MB")
        report.appendLine("  Max: ${maxMemory / 1024 / 1024}MB")
        report.appendLine("  Usage: ${String.format("%.1f", memoryUsagePercent)}%")
        report.appendLine("  Status: ${if (memoryUsagePercent > 80) "⚠️ HIGH" else "✅ OK"}")
        report.appendLine()

        // 🚀 FIX: Better performance thresholds based on category
        val categories = listOf("ui", "network", "database", "general")
        categories.forEach { category ->
            report.appendLine("${category.uppercase()} OPERATIONS:")
            val metrics = PerformanceUtils.PerformanceMetrics.getMetricsByCategory(category)
            if (metrics.isEmpty()) {
                report.appendLine("  No operations recorded")
            } else {
                metrics.forEach { (operation, avgTime) ->
                    val threshold = when (category) {
                        "ui" -> 16.0 // 60fps = 16ms per frame
                        "network" -> 1000.0 // 1 second
                        "database" -> 100.0 // 100ms
                        else -> 500.0 // 500ms
                    }

                    val status = if (avgTime > threshold) "⚠️ SLOW" else "✅ FAST"
                    report.appendLine("  $operation: ${String.format("%.1f", avgTime)}ms $status")
                }
            }
            report.appendLine()
        }

        report.appendLine("RECOMMENDATIONS:")
        if (memoryUsagePercent > 80) {
            report.appendLine("• Memory usage is high - consider clearing caches")
        }

        val uiMetrics = PerformanceUtils.PerformanceMetrics.getMetricsByCategory("ui")
        val slowUiOps = uiMetrics.filter { it.value > 16 }
        if (slowUiOps.isNotEmpty()) {
            report.appendLine("• UI operations are slow - check for excessive recompositions")
            slowUiOps.forEach { (op, time) ->
                report.appendLine("  - $op: ${String.format("%.1f", time)}ms")
            }
        }

        val networkMetrics = PerformanceUtils.PerformanceMetrics.getMetricsByCategory("network")
        val slowNetworkOps = networkMetrics.filter { it.value > 1000 }
        if (slowNetworkOps.isNotEmpty()) {
            report.appendLine("• Network operations are slow - check throttling and caching")
        }

        val databaseMetrics = PerformanceUtils.PerformanceMetrics.getMetricsByCategory("database")
        val slowDbOps = databaseMetrics.filter { it.value > 100 }
        if (slowDbOps.isNotEmpty()) {
            report.appendLine("• Database operations are slow - implement caching and batching")
            slowDbOps.forEach { (op, time) ->
                report.appendLine("  - $op: ${String.format("%.1f", time)}ms")
            }
        }

        if (memoryUsagePercent < 60 && slowUiOps.isEmpty() && slowNetworkOps.isEmpty() && slowDbOps.isEmpty()) {
            report.appendLine("✅ Performance looks good!")
        }

        return report.toString()
    }

    /**
     * Log the performance report to console
     */
    fun logPerformanceReport() {
        val report = generatePerformanceReport()
        Log.i("PerformanceReport", report)
    }

    /**
     * Reset all performance metrics (useful for testing)
     */
    fun resetMetrics() {
        PerformanceUtils.PerformanceMetrics.clearMetrics()
        Log.d("PerformanceDebug", "All performance metrics cleared")
    }

    /**
     * Get a quick performance summary for notifications or debug UI
     */
    fun getPerformanceSummary(): String {
        val runtime = Runtime.getRuntime()
        val usedMemory = runtime.totalMemory() - runtime.freeMemory()
        val maxMemory = runtime.maxMemory()
        val memoryUsagePercent = (usedMemory.toFloat() / maxMemory.toFloat()) * 100

        val uiMetrics = PerformanceUtils.PerformanceMetrics.getMetricsByCategory("ui")
        val slowUiOps = uiMetrics.filter { it.value > 16 }.size

        val networkMetrics = PerformanceUtils.PerformanceMetrics.getMetricsByCategory("network")
        val slowNetworkOps = networkMetrics.filter { it.value > 1000 }.size

        val databaseMetrics = PerformanceUtils.PerformanceMetrics.getMetricsByCategory("database")
        val slowDbOps = databaseMetrics.filter { it.value > 100 }.size

        return "Memory: ${String.format("%.1f", memoryUsagePercent)}% | Slow UI: $slowUiOps | Slow Network: $slowNetworkOps | Slow DB: $slowDbOps"
    }
}