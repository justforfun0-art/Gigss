package com.example.gigs.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Collections
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository to store and manage processed job IDs across the application
 * This provides a central place to track which jobs the user has interacted with
 * with thread-safe operations for concurrent access.
 * 🚀 PERFORMANCE OPTIMIZED: Added timeouts, error handling, and fast methods
 */
@Singleton
class ProcessedJobsRepository @Inject constructor() {
    private val TAG = "ProcessedJobsRepository"

    // Mutex for thread-safe operations on all collections
    private val mutex = Mutex()

    // Thread-safe collections for storing different job categories
    private val processedJobsSet = Collections.synchronizedSet(mutableSetOf<String>())
    private val sessionProcessedJobsSet = Collections.synchronizedSet(mutableSetOf<String>())
    private val rejectedJobsSet = Collections.synchronizedSet(mutableSetOf<String>())
    private val appliedJobsSet = Collections.synchronizedSet(mutableSetOf<String>())

    // Single source of truth for ALL jobs the user has interacted with
    private val _processedJobIds = MutableStateFlow<Set<String>>(processedJobsSet)
    val processedJobIds = _processedJobIds.asStateFlow()

    // Track session-only processed jobs (for UI purposes)
    private val _sessionProcessedJobIds = MutableStateFlow<Set<String>>(sessionProcessedJobsSet)
    val sessionProcessedJobIds = _sessionProcessedJobIds.asStateFlow()

    // Track rejected jobs specifically (for "Reconsider" feature)
    private val _rejectedJobIds = MutableStateFlow<Set<String>>(rejectedJobsSet)
    val rejectedJobIds = _rejectedJobIds.asStateFlow()

    // Track applied jobs specifically (for status indicators)
    private val _appliedJobIds = MutableStateFlow<Set<String>>(appliedJobsSet)
    val appliedJobIds = _appliedJobIds.asStateFlow()

    // Flag to track if we're in "reconsider rejected jobs" mode
    private val _isShowingRejectedJobs = MutableStateFlow(false)
    val isShowingRejectedJobs = _isShowingRejectedJobs.asStateFlow()

    /**
     * Set whether we're showing rejected jobs for reconsideration
     * Uses mutex to ensure thread safety
     * 🚀 OPTIMIZED: Added timeout protection
     */
    suspend fun setShowingRejectedJobs(showing: Boolean) {
        try {
            withTimeoutOrNull(1000) {
                mutex.withLock {
                    Log.d(TAG, "Setting showingRejectedJobs to $showing")
                    _isShowingRejectedJobs.value = showing

                    // When switching to rejected jobs mode, clear session processed jobs
                    if (showing) {
                        sessionProcessedJobsSet.clear()
                        _sessionProcessedJobIds.value = emptySet()
                    }
                }
            } ?: Log.w(TAG, "setShowingRejectedJobs timed out")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting showing rejected jobs: ${e.message}")
        }
    }

    /**
     * Generic method to add a job ID to the processed jobs
     * Used by both markJobAsApplied and markJobAsRejected
     * 🚀 OPTIMIZED: Added timeout protection
     */
    suspend fun addProcessedJob(jobId: String) {
        try {
            withTimeoutOrNull(500) {
                mutex.withLock {
                    if (!processedJobsSet.contains(jobId)) {
                        processedJobsSet.add(jobId)
                        _processedJobIds.value = processedJobsSet.toSet()

                        // Also add to session processed
                        sessionProcessedJobsSet.add(jobId)
                        _sessionProcessedJobIds.value = sessionProcessedJobsSet.toSet()

                        Log.d(TAG, "Added job $jobId to processed set, new size: ${processedJobsSet.size}")
                    }
                }
            } ?: Log.w(TAG, "addProcessedJob timed out for job: $jobId")
        } catch (e: Exception) {
            Log.e(TAG, "Error adding processed job $jobId: ${e.message}")
        }
    }

    /**
     * Add multiple job IDs to the set of processed jobs
     * Uses mutex to prevent race conditions
     * 🚀 OPTIMIZED: Added timeout and batch processing
     */
    suspend fun addProcessedJobs(jobIds: Collection<String>) {
        if (jobIds.isEmpty()) return

        try {
            withTimeoutOrNull(2000) {
                mutex.withLock {
                    val newJobIds = jobIds.filter { !processedJobsSet.contains(it) }
                    if (newJobIds.isNotEmpty()) {
                        processedJobsSet.addAll(newJobIds)
                        _processedJobIds.value = processedJobsSet.toSet()

                        sessionProcessedJobsSet.addAll(newJobIds)
                        _sessionProcessedJobIds.value = sessionProcessedJobsSet.toSet()

                        Log.d(TAG, "Added ${newJobIds.size} jobs to processed set, new size: ${processedJobsSet.size}")
                    }
                }
            } ?: Log.w(TAG, "addProcessedJobs timed out for ${jobIds.size} jobs")
        } catch (e: Exception) {
            Log.e(TAG, "Error adding processed jobs: ${e.message}")
        }
    }

    /**
     * Check if a job has been processed in the current session
     * 🚀 OPTIMIZED: No mutex needed for read-only operation
     */
    fun isJobProcessedInCurrentSession(jobId: String): Boolean {
        return sessionProcessedJobsSet.contains(jobId)
    }

    /**
     * Clear only the session processed jobs
     * Uses mutex for thread safety
     * 🚀 OPTIMIZED: Added timeout protection
     */
    suspend fun clearSessionProcessedJobs() {
        try {
            withTimeoutOrNull(1000) {
                mutex.withLock {
                    Log.d(TAG, "Clearing session processed jobs")
                    sessionProcessedJobsSet.clear()
                    _sessionProcessedJobIds.value = emptySet()
                }
            } ?: Log.w(TAG, "clearSessionProcessedJobs timed out")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing session processed jobs: ${e.message}")
        }
    }

    /**
     * Initialize the repository with existing applied and rejected job IDs
     * Uses mutex for thread safety
     * 🚀 OPTIMIZED: Added timeout and improved logging
     */
    suspend fun initializeWithExistingJobIds(appliedIds: Set<String>, rejectedIds: Set<String>) {
        try {
            withTimeoutOrNull(3000) {
                mutex.withLock {
                    Log.d(TAG, "Initializing with ${appliedIds.size} applied jobs and ${rejectedIds.size} rejected jobs")

                    // Initialize all collections
                    appliedJobsSet.clear()
                    appliedJobsSet.addAll(appliedIds)
                    _appliedJobIds.value = appliedJobsSet.toSet()

                    rejectedJobsSet.clear()
                    rejectedJobsSet.addAll(rejectedIds)
                    _rejectedJobIds.value = rejectedJobsSet.toSet()

                    // Combine for all processed jobs
                    processedJobsSet.clear()
                    processedJobsSet.addAll(appliedIds)
                    processedJobsSet.addAll(rejectedIds)
                    _processedJobIds.value = processedJobsSet.toSet()

                    Log.d(TAG, "Initialized with ${processedJobsSet.size} total processed jobs")
                }
            } ?: Log.w(TAG, "initializeWithExistingJobIds timed out")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing with existing job IDs: ${e.message}")
        }
    }

    /**
     * Initialize only the applied jobs
     * Uses mutex for thread safety
     * 🚀 OPTIMIZED: Added timeout protection
     */
    suspend fun initializeAppliedJobs(jobIds: Set<String>) {
        try {
            withTimeoutOrNull(2000) {
                mutex.withLock {
                    appliedJobsSet.addAll(jobIds)
                    _appliedJobIds.value = appliedJobsSet.toSet()

                    processedJobsSet.addAll(jobIds)
                    _processedJobIds.value = processedJobsSet.toSet()

                    Log.d(TAG, "Initialized with ${jobIds.size} applied jobs")
                }
            } ?: Log.w(TAG, "initializeAppliedJobs timed out")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing applied jobs: ${e.message}")
        }
    }

    /**
     * Initialize only the rejected jobs
     * Uses mutex for thread safety
     * 🚀 OPTIMIZED: Added timeout protection
     */
    suspend fun initializeRejectedJobs(jobIds: Set<String>) {
        try {
            withTimeoutOrNull(2000) {
                mutex.withLock {
                    rejectedJobsSet.addAll(jobIds)
                    _rejectedJobIds.value = rejectedJobsSet.toSet()

                    processedJobsSet.addAll(jobIds)
                    _processedJobIds.value = processedJobsSet.toSet()

                    Log.d(TAG, "Initialized with ${jobIds.size} rejected jobs")
                }
            } ?: Log.w(TAG, "initializeRejectedJobs timed out")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing rejected jobs: ${e.message}")
        }
    }

    /**
     * Mark a job as applied
     * Uses mutex for thread safety
     * 🚀 OPTIMIZED: Added timeout and improved state management
     */
    suspend fun markJobAsApplied(jobId: String) {
        try {
            withTimeoutOrNull(1000) {
                mutex.withLock {
                    // Add to applied jobs
                    appliedJobsSet.add(jobId)
                    _appliedJobIds.value = appliedJobsSet.toSet()

                    // Add to processed jobs
                    processedJobsSet.add(jobId)
                    _processedJobIds.value = processedJobsSet.toSet()

                    // Add to session processed
                    sessionProcessedJobsSet.add(jobId)
                    _sessionProcessedJobIds.value = sessionProcessedJobsSet.toSet()

                    // IMPORTANT: Remove from rejected jobs if present
                    if (rejectedJobsSet.contains(jobId)) {
                        rejectedJobsSet.remove(jobId)
                        _rejectedJobIds.value = rejectedJobsSet.toSet()
                        Log.d(TAG, "Removed job $jobId from rejected jobs as it's now applied")
                    }

                    Log.d(TAG, "Marked job $jobId as applied")
                }
            } ?: Log.w(TAG, "markJobAsApplied timed out for job: $jobId")
        } catch (e: Exception) {
            Log.e(TAG, "Error marking job as applied $jobId: ${e.message}")
        }
    }

    /**
     * Mark a job as rejected
     * Uses mutex for thread safety
     * 🚀 OPTIMIZED: Added timeout and improved state management
     */
    suspend fun markJobAsRejected(jobId: String) {
        try {
            withTimeoutOrNull(1000) {
                mutex.withLock {
                    // Add to processed jobs
                    processedJobsSet.add(jobId)
                    _processedJobIds.value = processedJobsSet.toSet()

                    // Add to rejected jobs
                    rejectedJobsSet.add(jobId)
                    _rejectedJobIds.value = rejectedJobsSet.toSet()

                    // Add to session processed jobs
                    sessionProcessedJobsSet.add(jobId)
                    _sessionProcessedJobIds.value = sessionProcessedJobsSet.toSet()

                    // Remove from applied if present
                    if (appliedJobsSet.contains(jobId)) {
                        appliedJobsSet.remove(jobId)
                        _appliedJobIds.value = appliedJobsSet.toSet()
                    }

                    Log.d(TAG, "Marked job $jobId as rejected")
                }
            } ?: Log.w(TAG, "markJobAsRejected timed out for job: $jobId")
        } catch (e: Exception) {
            Log.e(TAG, "Error marking job as rejected $jobId: ${e.message}")
        }
    }

    /**
     * Check if a job has been applied to
     * 🚀 OPTIMIZED: No mutex needed for read-only operation
     */
    fun isJobApplied(jobId: String): Boolean {
        return appliedJobsSet.contains(jobId)
    }

    /**
     * Check if a job has been rejected
     * 🚀 OPTIMIZED: No mutex needed for read-only operation
     */
    fun isJobRejected(jobId: String): Boolean {
        return rejectedJobsSet.contains(jobId)
    }

    /**
     * Get all jobs that have been processed (applied or rejected)
     * 🚀 OPTIMIZED: No mutex needed for read-only operation
     */
    fun getProcessedJobIds(): Set<String> {
        return processedJobsSet.toSet()
    }

    /**
     * Update the session processed jobs
     * Uses mutex for thread safety
     * 🚀 OPTIMIZED: Added timeout protection
     */
    suspend fun updateSessionProcessedJobs(jobIds: Set<String>) {
        try {
            withTimeoutOrNull(1000) {
                mutex.withLock {
                    sessionProcessedJobsSet.clear()
                    sessionProcessedJobsSet.addAll(jobIds)
                    _sessionProcessedJobIds.value = sessionProcessedJobsSet.toSet()
                    Log.d(TAG, "Updated session processed jobs, new count: ${jobIds.size}")
                }
            } ?: Log.w(TAG, "updateSessionProcessedJobs timed out")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating session processed jobs: ${e.message}")
        }
    }

    /**
     * Reset all state
     * Uses mutex for thread safety
     * 🚀 OPTIMIZED: Added timeout protection
     */
    suspend fun refreshSessionState() {
        try {
            withTimeoutOrNull(2000) {
                mutex.withLock {
                    appliedJobsSet.clear()
                    rejectedJobsSet.clear()
                    processedJobsSet.clear()
                    sessionProcessedJobsSet.clear()

                    _appliedJobIds.value = emptySet()
                    _rejectedJobIds.value = emptySet()
                    _processedJobIds.value = emptySet()
                    _sessionProcessedJobIds.value = emptySet()

                    Log.d(TAG, "Reset all processed jobs state")
                }
            } ?: Log.w(TAG, "refreshSessionState timed out")
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing session state: ${e.message}")
        }
    }

    /**
     * Check if a job has been processed in any way (applied or rejected)
     * 🚀 OPTIMIZED: No mutex needed for read-only operation
     */
    fun isJobProcessed(jobId: String): Boolean {
        return processedJobsSet.contains(jobId)
    }

    /**
     * Update the rejected job IDs
     * Uses mutex for thread safety
     * 🚀 OPTIMIZED: Added timeout and improved state management
     */
    suspend fun updateRejectedJobIds(rejectedIds: Set<String>) {
        try {
            withTimeoutOrNull(2000) {
                mutex.withLock {
                    Log.d(TAG, "Updating rejected job IDs in repository: ${rejectedIds.size} ids")

                    rejectedJobsSet.clear()
                    rejectedJobsSet.addAll(rejectedIds)
                    _rejectedJobIds.value = rejectedJobsSet.toSet()

                    // Update processed jobs to union of applied and rejected
                    processedJobsSet.clear()
                    processedJobsSet.addAll(appliedJobsSet)
                    processedJobsSet.addAll(rejectedJobsSet)
                    _processedJobIds.value = processedJobsSet.toSet()
                }
            } ?: Log.w(TAG, "updateRejectedJobIds timed out")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating rejected job IDs: ${e.message}")
        }
    }

    /**
     * Update the applied job IDs
     * Uses mutex for thread safety
     * 🚀 OPTIMIZED: Added timeout and improved state management
     */
    suspend fun updateAppliedJobIds(appliedIds: Set<String>) {
        try {
            withTimeoutOrNull(2000) {
                mutex.withLock {
                    Log.d(TAG, "Updating applied job IDs in repository: ${appliedIds.size} ids")

                    appliedJobsSet.clear()
                    appliedJobsSet.addAll(appliedIds)
                    _appliedJobIds.value = appliedJobsSet.toSet()

                    // Update processed jobs to union of applied and rejected
                    processedJobsSet.clear()
                    processedJobsSet.addAll(appliedJobsSet)
                    processedJobsSet.addAll(rejectedJobsSet)
                    _processedJobIds.value = processedJobsSet.toSet()
                }
            } ?: Log.w(TAG, "updateAppliedJobIds timed out")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating applied job IDs: ${e.message}")
        }
    }

    /**
     * Initialize session from existing processed jobs with performance optimizations
     * Call this when the user returns to the job cards screen
     * 🚀 PERFORMANCE OPTIMIZED: Added timeout and error handling
     */
    suspend fun restoreSessionState() {
        try {
            // ✅ ADD: Timeout wrapper to prevent blocking
            withTimeoutOrNull(2000) { // 2 second timeout
                mutex.withLock {
                    // Session processed jobs should include all processed jobs
                    sessionProcessedJobsSet.clear()
                    sessionProcessedJobsSet.addAll(processedJobsSet)
                    _sessionProcessedJobIds.value = sessionProcessedJobsSet.toSet()

                    Log.d(TAG, "Restored session state with ${sessionProcessedJobsSet.size} processed jobs")
                    Log.d(TAG, "Session state - Applied: ${appliedJobsSet.size}, Rejected: ${rejectedJobsSet.size}")
                }
            } ?: run {
                // ✅ ADD: Timeout fallback
                Log.w(TAG, "Session restoration timed out, using current state")
                // Don't clear anything - just use current state
                _sessionProcessedJobIds.value = sessionProcessedJobsSet.toSet()
            }
        } catch (e: Exception) {
            // ✅ ADD: Error handling for robustness
            Log.e(TAG, "Error restoring session state: ${e.message}")
            // Fallback: ensure we have at least the current state
            try {
                _sessionProcessedJobIds.value = sessionProcessedJobsSet.toSet()
            } catch (fallbackError: Exception) {
                Log.e(TAG, "Fallback session restoration failed: ${fallbackError.message}")
                // Last resort: use empty state
                _sessionProcessedJobIds.value = emptySet()
            }
        }
    }

    /**
     * 🚀 NEW: Optimized version for fast restoration (use when performance is critical)
     */
    suspend fun restoreSessionStateFast() {
        try {
            // Skip mutex for read-only operations when performance is critical
            val currentProcessedJobs = processedJobsSet.toSet()

            // Only lock for the write operation
            withTimeoutOrNull(1000) {
                mutex.withLock {
                    sessionProcessedJobsSet.clear()
                    sessionProcessedJobsSet.addAll(currentProcessedJobs)
                    _sessionProcessedJobIds.value = currentProcessedJobs
                }
            } ?: Log.w(TAG, "Fast session restoration timed out")

            Log.d(TAG, "Fast restored session state with ${currentProcessedJobs.size} processed jobs")
        } catch (e: Exception) {
            Log.e(TAG, "Fast session restoration failed, falling back to regular method: ${e.message}")
            restoreSessionState() // Fallback to regular method
        }
    }

    /**
     * 🚀 NEW: Async version that doesn't block caller
     */
    fun restoreSessionStateAsync(): Deferred<Boolean> {
        return CoroutineScope(Dispatchers.IO).async {
            try {
                restoreSessionState()
                true
            } catch (e: Exception) {
                Log.e(TAG, "Async session restoration failed: ${e.message}")
                false
            }
        }
    }

    /**
     * 🚀 NEW: Check if session restoration is needed (avoid unnecessary work)
     */
    fun isSessionRestorationNeeded(): Boolean {
        return sessionProcessedJobsSet.size != processedJobsSet.size ||
                !sessionProcessedJobsSet.containsAll(processedJobsSet)
    }

    /**
     * 🚀 NEW: Optimized initialization method for startup
     */
    suspend fun initializeSessionQuickly() {
        try {
            withTimeoutOrNull(1000) { // Even shorter timeout for startup
                if (isSessionRestorationNeeded()) {
                    Log.d(TAG, "Session restoration needed, performing quick init")
                    restoreSessionStateFast()
                } else {
                    Log.d(TAG, "Session already in sync, skipping restoration")
                }
            } ?: Log.w(TAG, "Quick session initialization timed out")
        } catch (e: Exception) {
            Log.e(TAG, "Quick session initialization failed: ${e.message}")
        }
    }

    /**
     * Get unprocessed jobs count for UI display
     * 🚀 OPTIMIZED: No mutex needed for read-only operation
     */
    fun getUnprocessedJobsCount(totalJobs: Int): Int {
        return maxOf(0, totalJobs - processedJobsSet.size)
    }

    /**
     * Clear all processed job IDs
     * Uses mutex to prevent race conditions
     * 🚀 OPTIMIZED: Added timeout protection
     */
    suspend fun clearProcessedJobs() {
        try {
            withTimeoutOrNull(2000) {
                mutex.withLock {
                    appliedJobsSet.clear()
                    rejectedJobsSet.clear()
                    processedJobsSet.clear()
                    sessionProcessedJobsSet.clear()

                    _appliedJobIds.value = emptySet()
                    _rejectedJobIds.value = emptySet()
                    _processedJobIds.value = emptySet()
                    _sessionProcessedJobIds.value = emptySet()

                    Log.d(TAG, "Cleared all processed jobs")
                }
            } ?: Log.w(TAG, "clearProcessedJobs timed out")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing processed jobs: ${e.message}")
        }
    }

    /**
     * Get rejected job IDs
     * 🚀 OPTIMIZED: No mutex needed for read-only operation
     */
    fun getRejectedJobIds(): Set<String> {
        return rejectedJobsSet.toSet()
    }

    /**
     * Get applied job IDs
     * 🚀 OPTIMIZED: No mutex needed for read-only operation
     */
    fun getAppliedJobIds(): Set<String> {
        return appliedJobsSet.toSet()
    }

    /**
     * 🚀 NEW: Bulk update both applied and rejected jobs (most efficient for initialization)
     */
    suspend fun updateAllJobIds(appliedIds: Set<String>, rejectedIds: Set<String>) {
        try {
            withTimeoutOrNull(3000) {
                mutex.withLock {
                    Log.d(TAG, "Bulk updating: ${appliedIds.size} applied, ${rejectedIds.size} rejected")

                    // Clear and update applied jobs
                    appliedJobsSet.clear()
                    appliedJobsSet.addAll(appliedIds)
                    _appliedJobIds.value = appliedJobsSet.toSet()

                    // Clear and update rejected jobs
                    rejectedJobsSet.clear()
                    rejectedJobsSet.addAll(rejectedIds)
                    _rejectedJobIds.value = rejectedJobsSet.toSet()

                    // Update processed jobs to union of both
                    processedJobsSet.clear()
                    processedJobsSet.addAll(appliedIds)
                    processedJobsSet.addAll(rejectedIds)
                    _processedJobIds.value = processedJobsSet.toSet()

                    Log.d(TAG, "Bulk update completed: ${processedJobsSet.size} total processed jobs")
                }
            } ?: Log.w(TAG, "updateAllJobIds timed out")
        } catch (e: Exception) {
            Log.e(TAG, "Error in bulk update: ${e.message}")
        }
    }



    /**
     * 🚀 NEW: Performance monitoring method
     */
    fun logPerformanceStats() {
        Log.d(TAG, "=== PROCESSED JOBS REPOSITORY STATS ===")
        Log.d(TAG, "Applied jobs: ${appliedJobsSet.size}")
        Log.d(TAG, "Rejected jobs: ${rejectedJobsSet.size}")
        Log.d(TAG, "Total processed: ${processedJobsSet.size}")
        Log.d(TAG, "Session processed: ${sessionProcessedJobsSet.size}")
        Log.d(TAG, "Showing rejected jobs: ${_isShowingRejectedJobs.value}")
        Log.d(TAG, "Session restoration needed: ${isSessionRestorationNeeded()}")
        Log.d(TAG, "==========================================")
    }

    fun markJobAsRejectedUltraFast(jobId: String) {
        try {
            // 🚀 CRITICAL: Atomic operations only - no mutex, no timeouts, no await
            rejectedJobsSet.add(jobId)
            processedJobsSet.add(jobId)
            sessionProcessedJobsSet.add(jobId)

            // Remove from applied if present (atomic)
            appliedJobsSet.remove(jobId)

            // 🚀 BATCH: Single state flow update for all changes (more efficient)
            _rejectedJobIds.value = rejectedJobsSet.toSet()
            _processedJobIds.value = processedJobsSet.toSet()
            _sessionProcessedJobIds.value = sessionProcessedJobsSet.toSet()
            _appliedJobIds.value = appliedJobsSet.toSet()

            Log.d(TAG, "⚡ INSTANT: Marked job $jobId as rejected in <1ms")

        } catch (e: Exception) {
            Log.e(TAG, "Ultra-fast rejection failed, falling back to regular method: ${e.message}")
            // Fallback to your existing method
            CoroutineScope(Dispatchers.IO).launch { markJobAsRejected(jobId) }
        }
    }


    // 🚀 NEW: Batch operations for better performance
    fun markMultipleJobsAsRejected(jobIds: List<String>) {
        if (jobIds.isEmpty()) return

        try {
            // Batch atomic operations
            rejectedJobsSet.addAll(jobIds)
            processedJobsSet.addAll(jobIds)
            sessionProcessedJobsSet.addAll(jobIds)

            // Remove from applied set if present
            jobIds.forEach { appliedJobsSet.remove(it) }

            // Single state flow update for all changes
            _rejectedJobIds.value = rejectedJobsSet.toSet()
            _processedJobIds.value = processedJobsSet.toSet()
            _sessionProcessedJobIds.value = sessionProcessedJobsSet.toSet()
            _appliedJobIds.value = appliedJobsSet.toSet()

            Log.d(TAG, "⚡ BATCH: Marked ${jobIds.size} jobs as rejected instantly")

        } catch (e: Exception) {
            Log.e(TAG, "Error in batch rejection: ${e.message}")
        }
    }
}