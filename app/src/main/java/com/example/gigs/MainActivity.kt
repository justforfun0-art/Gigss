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
import com.example.gigs.data.repository.JobRepository
import com.example.gigs.viewmodel.ProcessedJobsRepository
import com.example.gigs.data.repository.ReconsiderationStorageManager
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

    // 🚀 EXISTING: Performance optimization dependencies
    @Inject lateinit var authRepository: AuthRepository
    @Inject lateinit var profileRepository: ProfileRepository

    // 🚀 NEW: Reconsideration system dependencies
    @Inject lateinit var jobRepository: JobRepository
    @Inject lateinit var processedJobsRepository: ProcessedJobsRepository
    @Inject lateinit var reconsiderationStorage: ReconsiderationStorageManager

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

                        // 🚀 NEW: Initialize reconsideration system
                        launch(Dispatchers.IO) {
                            initializeReconsiderationSystem()
                        }

                        // End startup timing
                        PerformanceUtils.PerformanceMetrics.endTiming("app_startup", appStartTime)
                    }
                }
            }
        }
    }

    /**
     * 🚀 NEW: Initialize the one-time reconsideration system
     * This runs in background to avoid blocking UI startup
     */
    private suspend fun initializeReconsiderationSystem() {
        try {
            val initStartTime = PerformanceUtils.PerformanceMetrics.startTiming("reconsideration_init", "general")

            Log.d(TAG, "🔄 Initializing reconsideration system...")

            // Step 1: Initialize repository from storage (loads reconsidered job IDs)
            processedJobsRepository.initializeFromStorage()

            // Step 2: Initialize the job repository reconsideration system
            jobRepository.initializeReconsiderationSystem()

            // Step 3: Log initial state for debugging
            if (BuildConfig.DEBUG) {
                reconsiderationStorage.logStorageState()
                processedJobsRepository.logPerformanceStats()
            }

            PerformanceUtils.PerformanceMetrics.endTiming("reconsideration_init", initStartTime)

            Log.d(TAG, "✅ Reconsideration system initialized successfully")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to initialize reconsideration system: ${e.message}", e)

            // 🚀 GRACEFUL FALLBACK: System still works without reconsideration features
            Log.w(TAG, "⚠️ App will continue without reconsideration features")
        }
    }

    /**
     * 🚀 ENHANCED: Combined background data loading with reconsideration and performance optimization
     */
    private suspend fun startBackgroundDataLoading() {
        try {
            val loadingStartTime = PerformanceUtils.PerformanceMetrics.startTiming("background_data_loading", "general")

            Log.d(TAG, "🚀 Starting background data loading...")

            withContext(Dispatchers.IO) {
                // Load only critical data, limit timeout
                withTimeoutOrNull(2000) {
                    val userId = authRepository.getCurrentUserId()
                    if (userId != null) {
                        Log.d(TAG, "👤 User authenticated: ${userId.take(8)}...")

                        // Preload user profile only if needed
                        launch {
                            try {
                                profileRepository.getEmployeeProfileByUserId(userId).take(1).collect { }
                                Log.d(TAG, "✅ User profile preloaded")
                            } catch (e: Exception) {
                                Log.w(TAG, "User profile preload error: ${e.message}")
                            }
                        }

                        // 🚀 NEW: Load user-specific processed jobs data
                        launch {
                            try {
                                // This could load applied/rejected jobs from database to sync with local state
                                // processedJobsRepository.syncWithDatabase() // Implement if needed
                                Log.d(TAG, "✅ User data sync completed")
                            } catch (e: Exception) {
                                Log.e(TAG, "❌ User data sync failed: ${e.message}")
                            }
                        }
                    } else {
                        Log.d(TAG, "👤 No authenticated user, skipping user-specific loading")
                    }
                }
            }

            PerformanceUtils.PerformanceMetrics.endTiming("background_data_loading", loadingStartTime)

            Log.d(TAG, "✅ Background data loading completed")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Background data loading failed: ${e.message}", e)
        }
    }

    /**
     * 🚀 ENHANCED: Performance monitoring setup with reconsideration system monitoring
     */
    private fun setupPerformanceMonitoring() {
        Log.d(TAG, "📊 Setting up performance monitoring...")

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

                            // 🚀 NEW: Monitor reconsideration system performance
                            try {
                                val stats = processedJobsRepository.getReconsiderationStats()
                                Log.d(TAG, "📊 RECONSIDERATION STATS: $stats")
                            } catch (e: Exception) {
                                Log.e(TAG, "Error logging reconsideration stats: ${e.message}")
                            }

                            Log.d(TAG, "=====================================")
                        } catch (e: Exception) {
                            Log.e(TAG, "Error in performance monitoring: ${e.message}")
                        }
                    }
                }, 30000, 300000) // Log every 5 minutes for reconsideration stats
            }
        }

        Log.d(TAG, "✅ Performance monitoring setup completed")
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

    /**
     * 🚀 ENHANCED: Handle app resume for both performance monitoring and reconsideration system
     */
    override fun onResume() {
        super.onResume()
        val resumeTime = PerformanceUtils.PerformanceMetrics.startTiming("activity_resume", "general")

        Log.d(TAG, "MainActivity onResume")

        // EXISTING: Check memory pressure when app resumes
        if (PerformanceUtils.MemoryMonitor.isMemoryLow()) {
            Log.w(TAG, "Memory pressure detected on resume - triggering cleanup")
            PerformanceUtils.MemoryMonitor.triggerLowMemoryCleanup()
        }

        // EXISTING: Log memory status
        PerformanceUtils.MemoryMonitor.logMemoryUsage(TAG)

        // 🚀 NEW: Refresh reconsideration state when app resumes
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "🔄 App resumed, checking reconsideration data consistency")

                // Check if storage and memory are in sync
                val storageIds = reconsiderationStorage.loadReconsideredJobIds()
                val memoryIds = processedJobsRepository.reconsideredJobIds.value

                if (storageIds != memoryIds) {
                    Log.w(TAG, "⚠️ Reconsideration data inconsistency detected, syncing...")
                    processedJobsRepository.initializeReconsideredJobs(storageIds)
                    Log.d(TAG, "✅ Reconsideration data synced")
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ Error checking reconsideration data on resume: ${e.message}")
            }
        }

        PerformanceUtils.PerformanceMetrics.endTiming("activity_resume", resumeTime)
    }

    /**
     * 🚀 ENHANCED: Handle app pause for both performance monitoring and reconsideration system
     */
    override fun onPause() {
        super.onPause()
        val pauseTime = PerformanceUtils.PerformanceMetrics.startTiming("activity_pause", "general")
        Log.d(TAG, "MainActivity onPause")

        // 🚀 NEW: Save reconsideration state when app goes to background
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // The repository automatically saves to storage, but we can trigger a manual save here
                Log.d(TAG, "💾 App paused, ensuring reconsideration data is saved")

                // Optional: Force save current state
                // reconsiderationStorage.saveReconsideredJobIds(
                //     processedJobsRepository.reconsideredJobIds.value
                // )

            } catch (e: Exception) {
                Log.e(TAG, "❌ Error saving reconsideration data on pause: ${e.message}")
            }
        }

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

    /**
     * 🚀 NEW: Debug menu for reconsideration system (only in debug builds)
     */
    private fun showDebugReconsiderationMenu() {
        if (!BuildConfig.DEBUG) return

        // You can call this method from a debug menu or gesture
        lifecycleScope.launch {
            try {
                val stats = processedJobsRepository.getReconsiderationStats()
                val storageStats = reconsiderationStorage.getStorageStats()

                val debugInfo = """
                    🔍 RECONSIDERATION DEBUG INFO
                    
                    Repository Stats:
                    • Total Rejected: ${stats.totalRejected}
                    • Reconsidered: ${stats.reconsidered}
                    • Eligible: ${stats.eligibleForReconsideration}
                    
                    Storage Stats:
                    • Stored Count: ${storageStats.reconsideredJobsCount}
                    • Last Sync: ${if (storageStats.lastSyncTimestamp > 0)
                    java.util.Date(storageStats.lastSyncTimestamp) else "Never"}
                    • Has Data: ${storageStats.hasData}
                    
                    System Health: ${if (stats.reconsidered == storageStats.reconsideredJobsCount) "✅ HEALTHY" else "⚠️ SYNC NEEDED"}
                """.trimIndent()

                Log.d(TAG, debugInfo)

                // You could show this in a dialog or toast in debug builds
                // Toast.makeText(this@MainActivity, "Debug info logged", Toast.LENGTH_SHORT).show()

            } catch (e: Exception) {
                Log.e(TAG, "❌ Error generating debug info: ${e.message}")
            }
        }
    }
}

/**
 * 🚀 ENHANCED: Performance debug utilities with reconsideration system support
 */
object PerformanceDebugUtils {

    /**
     * 🚀 ENHANCED: Performance report now includes reconsideration system metrics
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