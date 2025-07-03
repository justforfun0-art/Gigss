package com.example.gigs.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import com.example.gigs.data.repository.ReconsiderationStorageManager
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
 * 🚀 COMPLETE Enhanced ProcessedJobsRepository with One-time Reconsideration System
 *
 * Repository to store and manage processed job IDs across the application.
 * This provides a central place to track which jobs the user has interacted with
 * with thread-safe operations for concurrent access.
 *
 * NEW FEATURES:
 * - One-time reconsideration tracking (each rejected job shown only once for reconsideration)
 * - Persistent storage integration
 * - Enhanced session management
 * - Performance optimizations with timeouts and error handling
 */
@Singleton
class ProcessedJobsRepository @Inject constructor(
    private val reconsiderationStorage: ReconsiderationStorageManager
) {
    private val TAG = "ProcessedJobsRepository"

    // Mutex for thread-safe operations on all collections
    private val mutex = Mutex()

    // 🚀 Thread-safe collections for storing different job categories
    private val processedJobsSet = Collections.synchronizedSet(mutableSetOf<String>())
    private val sessionProcessedJobsSet = Collections.synchronizedSet(mutableSetOf<String>())
    private val rejectedJobsSet = Collections.synchronizedSet(mutableSetOf<String>())
    private val appliedJobsSet = Collections.synchronizedSet(mutableSetOf<String>())

    // 🚀 NEW: Track jobs that have been reconsidered (shown once in rejected mode)
    private val reconsideredJobsSet = Collections.synchronizedSet(mutableSetOf<String>())

    // 🚀 StateFlows for UI observation - Single source of truth for ALL jobs the user has interacted with
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

    // 🚀 NEW: StateFlow for reconsidered jobs
    private val _reconsideredJobIds = MutableStateFlow<Set<String>>(reconsideredJobsSet)
    val reconsideredJobIds = _reconsideredJobIds.asStateFlow()

    // Flag to track if we're in "reconsider rejected jobs" mode
    private val _isShowingRejectedJobs = MutableStateFlow(false)
    val isShowingRejectedJobs = _isShowingRejectedJobs.asStateFlow()

    // =============================================================================
    // 🚀 INITIALIZATION METHODS
    // =============================================================================

    /**
     * 🚀 NEW: Initialize from persistent storage on app start
     */
    suspend fun initializeFromStorage() {
        try {
            val storedReconsideredIds = reconsiderationStorage.loadReconsideredJobIds()

            withTimeoutOrNull(2000) {
                mutex.withLock {
                    reconsideredJobsSet.clear()
                    reconsideredJobsSet.addAll(storedReconsideredIds)
                    _reconsideredJobIds.value = reconsideredJobsSet.toSet()
                }
            }

            Log.d(TAG, "✅ Initialized from storage: ${storedReconsideredIds.size} reconsidered jobs")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error initializing from storage: ${e.message}")
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
     * 🚀 NEW: Initialize reconsidered jobs from storage
     */
    suspend fun initializeReconsideredJobs(reconsideredIds: Set<String>) {
        try {
            withTimeoutOrNull(2000) {
                mutex.withLock {
                    reconsideredJobsSet.clear()
                    reconsideredJobsSet.addAll(reconsideredIds)
                    _reconsideredJobIds.value = reconsideredJobsSet.toSet()

                    Log.d(TAG, "Initialized with ${reconsideredIds.size} reconsidered jobs")
                }
            } ?: Log.w(TAG, "initializeReconsideredJobs timed out")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing reconsidered jobs: ${e.message}")
        }
    }

    // =============================================================================
    // 🚀 MODE MANAGEMENT (Regular vs Reconsideration)
    // =============================================================================

    /**
     * 🚀 ENHANCED: Set whether we're showing rejected jobs for reconsideration
     * Uses mutex to ensure thread safety with complete session management
     */
    suspend fun setShowingRejectedJobs(showing: Boolean) {
        try {
            withTimeoutOrNull(1000) {
                mutex.withLock {
                    Log.d(TAG, "🚀 SETTING showingRejectedJobs to $showing")
                    _isShowingRejectedJobs.value = showing

                    // 🚀 CRITICAL FIX: Handle both directions of mode switching
                    if (showing) {
                        // Entering rejected jobs mode: clear session to show all rejected jobs
                        Log.d(TAG, "🚀 CLEARING session processed jobs for rejected jobs mode")
                        sessionProcessedJobsSet.clear()
                        _sessionProcessedJobIds.value = emptySet()

                        // 🚀 NEW: Log reconsideration stats
                        val eligibleCount = getEligibleForReconsideration().size
                        val totalRejected = rejectedJobsSet.size
                        val alreadyReconsidered = reconsideredJobsSet.size

                        Log.d(TAG, "📊 RECONSIDERATION STATS:")
                        Log.d(TAG, "   Total rejected jobs: $totalRejected")
                        Log.d(TAG, "   Already reconsidered: $alreadyReconsidered")
                        Log.d(TAG, "   Eligible for reconsideration: $eligibleCount")
                    } else {
                        // 🚀 CRITICAL FIX: Returning to regular mode: restore session from processed jobs
                        Log.d(TAG, "🚀 RESTORING session processed jobs for regular mode")
                        sessionProcessedJobsSet.clear()
                        sessionProcessedJobsSet.addAll(processedJobsSet)
                        _sessionProcessedJobIds.value = sessionProcessedJobsSet.toSet()
                    }
                }
            } ?: Log.w(TAG, "setShowingRejectedJobs timed out")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting showing rejected jobs: ${e.message}")
        }
    }

    // =============================================================================
    // 🚀 RECONSIDERATION SYSTEM (One-time Only)
    // =============================================================================

    /**
     * 🚀 NEW: Get jobs that are eligible for reconsideration
     * Returns rejected jobs that have NOT been reconsidered yet
     */
    fun getEligibleForReconsideration(): Set<String> {
        return rejectedJobsSet.filter { jobId ->
            !reconsideredJobsSet.contains(jobId)
        }.toSet()
    }

    /**
     * 🚀 NEW: Mark a job as reconsidered (prevents future reconsideration)
     */
    suspend fun markJobAsReconsidered(jobId: String) {
        try {
            withTimeoutOrNull(1000) {
                mutex.withLock {
                    reconsideredJobsSet.add(jobId)
                    _reconsideredJobIds.value = reconsideredJobsSet.toSet()

                    Log.d(TAG, "✅ Marked job $jobId as reconsidered - will not appear again")
                }
            } ?: Log.w(TAG, "markJobAsReconsidered timed out for job: $jobId")

            // Save to persistent storage
            reconsiderationStorage.addReconsideredJobId(jobId)

        } catch (e: Exception) {
            Log.e(TAG, "Error marking job as reconsidered $jobId: ${e.message}")
        }
    }

    /**
     * 🚀 NEW: Mark multiple jobs as reconsidered (batch operation)
     */
    suspend fun markMultipleJobsAsReconsidered(jobIds: Collection<String>) {
        if (jobIds.isEmpty()) return

        try {
            withTimeoutOrNull(2000) {
                mutex.withLock {
                    reconsideredJobsSet.addAll(jobIds)
                    _reconsideredJobIds.value = reconsideredJobsSet.toSet()

                    Log.d(TAG, "✅ Marked ${jobIds.size} jobs as reconsidered")
                }
            } ?: Log.w(TAG, "markMultipleJobsAsReconsidered timed out")

            // Save to persistent storage
            reconsiderationStorage.addMultipleReconsideredJobIds(jobIds)

        } catch (e: Exception) {
            Log.e(TAG, "Error marking multiple jobs as reconsidered: ${e.message}")
        }
    }

    /**
     * 🚀 NEW: Check if a job has been reconsidered
     */
    fun isJobReconsidered(jobId: String): Boolean {
        return reconsideredJobsSet.contains(jobId)
    }

    /**
     * 🚀 NEW: Get reconsideration statistics for debugging
     */
    fun getReconsiderationStats(): ReconsiderationStats {
        return ReconsiderationStats(
            totalRejected = rejectedJobsSet.size,
            reconsidered = reconsideredJobsSet.size,
            eligibleForReconsideration = getEligibleForReconsideration().size
        )
    }

    // =============================================================================
    // 🚀 JOB PROCESSING METHODS
    // =============================================================================

    /**
     * 🚀 ENHANCED: Mark a job as applied with reconsideration tracking
     * Uses mutex for thread safety
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

                    // 🚀 NEW: If we're in rejected jobs mode, mark as reconsidered
                    if (_isShowingRejectedJobs.value) {
                        reconsideredJobsSet.add(jobId)
                        _reconsideredJobIds.value = reconsideredJobsSet.toSet()
                        Log.d(TAG, "🔄 Job $jobId reconsidered and applied")
                    }

                    // IMPORTANT: Remove from rejected jobs if present
                    if (rejectedJobsSet.contains(jobId)) {
                        rejectedJobsSet.remove(jobId)
                        _rejectedJobIds.value = rejectedJobsSet.toSet()
                        Log.d(TAG, "Removed job $jobId from rejected jobs as it's now applied")
                    }

                    Log.d(TAG, "Marked job $jobId as applied")
                }
            } ?: Log.w(TAG, "markJobAsApplied timed out for job: $jobId")

            // 🚀 NEW: Save to storage if reconsidered
            if (_isShowingRejectedJobs.value) {
                reconsiderationStorage.addReconsideredJobId(jobId)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error marking job as applied $jobId: ${e.message}")
        }
    }

    /**
     * 🚀 ENHANCED: Mark a job as not interested with reconsideration tracking
     * Uses mutex for thread safety
     */
    suspend fun markJobAsNotInterested(jobId: String) {
        try {
            withTimeoutOrNull(1000) {
                mutex.withLock {
                    // Add to processed jobs
                    processedJobsSet.add(jobId)
                    _processedJobIds.value = processedJobsSet.toSet()

                    // Add to rejected jobs (this represents NOT_INTERESTED jobs)
                    rejectedJobsSet.add(jobId)
                    _rejectedJobIds.value = rejectedJobsSet.toSet()

                    // 🚀 NEW: If we're in rejected jobs mode, mark as reconsidered
                    if (_isShowingRejectedJobs.value) {
                        reconsideredJobsSet.add(jobId)
                        _reconsideredJobIds.value = reconsideredJobsSet.toSet()
                        Log.d(TAG, "🔄 Job $jobId reconsidered and marked as NOT_INTERESTED again")
                    }

                    // 🚀 FIX: Only add to session processed if NOT in rejected jobs mode
                    if (!_isShowingRejectedJobs.value) {
                        sessionProcessedJobsSet.add(jobId)
                        _sessionProcessedJobIds.value = sessionProcessedJobsSet.toSet()
                        Log.d(TAG, "Added job $jobId to session processed (regular mode)")
                    } else {
                        Log.d(TAG, "Skipped adding job $jobId to session processed (rejected mode)")
                    }

                    // Remove from applied if present
                    if (appliedJobsSet.contains(jobId)) {
                        appliedJobsSet.remove(jobId)
                        _appliedJobIds.value = appliedJobsSet.toSet()
                    }

                    Log.d(TAG, "Marked job $jobId as NOT_INTERESTED")
                }
            } ?: Log.w(TAG, "markJobAsNotInterested timed out for job: $jobId")

            // 🚀 NEW: Save to storage if reconsidered
            if (_isShowingRejectedJobs.value) {
                reconsiderationStorage.addReconsideredJobId(jobId)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error marking job as NOT_INTERESTED $jobId: ${e.message}")
        }
    }

    /**
     * 🚀 ENHANCED: Ultra-fast NOT_INTERESTED marking with reconsideration tracking
     */
    fun markJobAsNotInterestedUltraFast(jobId: String) {
        try {
            // 🚀 CRITICAL: Atomic operations only - no mutex, no timeouts, no await
            rejectedJobsSet.add(jobId) // This represents NOT_INTERESTED jobs
            processedJobsSet.add(jobId)

            // 🚀 NEW: Mark as reconsidered if in rejected mode
            if (_isShowingRejectedJobs.value) {
                reconsideredJobsSet.add(jobId)
            }

            // 🚀 FIX: Only add to session processed if NOT in rejected jobs mode
            if (!_isShowingRejectedJobs.value) {
                sessionProcessedJobsSet.add(jobId)
            }

            // Remove from applied if present (atomic)
            appliedJobsSet.remove(jobId)

            // 🚀 BATCH: Single state flow update for all changes (more efficient)
            _rejectedJobIds.value = rejectedJobsSet.toSet()
            _processedJobIds.value = processedJobsSet.toSet()
            _appliedJobIds.value = appliedJobsSet.toSet()

            // 🚀 NEW: Update reconsidered state
            if (_isShowingRejectedJobs.value) {
                _reconsideredJobIds.value = reconsideredJobsSet.toSet()
            }

            // 🚀 FIX: Only update session processed if not in rejected mode
            if (!_isShowingRejectedJobs.value) {
                _sessionProcessedJobIds.value = sessionProcessedJobsSet.toSet()
            }

            Log.d(TAG, "⚡ INSTANT: Marked job $jobId as NOT_INTERESTED in <1ms")

            // 🚀 NEW: Save to storage asynchronously if reconsidered
            if (_isShowingRejectedJobs.value) {
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        reconsiderationStorage.addReconsideredJobId(jobId)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to save reconsidered job to storage: ${e.message}")
                    }
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Ultra-fast NOT_INTERESTED marking failed, falling back to regular method: ${e.message}")
            // Fallback to regular method
            CoroutineScope(Dispatchers.IO).launch { markJobAsNotInterested(jobId) }
        }
    }

    /**
     * Mark a job as rejected - delegates to markJobAsNotInterested
     */
    suspend fun markJobAsRejected(jobId: String) {
        markJobAsNotInterested(jobId)
    }

    /**
     * Ultra-fast rejected marking - delegates to ultra-fast NOT_INTERESTED
     */
    fun markJobAsRejectedUltraFast(jobId: String) {
        markJobAsNotInterestedUltraFast(jobId)
    }

    // =============================================================================
    // 🚀 BATCH OPERATIONS
    // =============================================================================

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
     * 🚀 ENHANCED: Batch NOT_INTERESTED marking with reconsideration tracking
     */
    fun markMultipleJobsAsNotInterested(jobIds: List<String>) {
        if (jobIds.isEmpty()) return

        try {
            // Batch atomic operations
            rejectedJobsSet.addAll(jobIds)
            processedJobsSet.addAll(jobIds)

            // 🚀 NEW: Mark as reconsidered if in rejected mode
            if (_isShowingRejectedJobs.value) {
                reconsideredJobsSet.addAll(jobIds)
            }

            // Only add to session if not in rejected mode
            if (!_isShowingRejectedJobs.value) {
                sessionProcessedJobsSet.addAll(jobIds)
            }

            // Remove from applied set if present
            jobIds.forEach { appliedJobsSet.remove(it) }

            // Single state flow update for all changes
            _rejectedJobIds.value = rejectedJobsSet.toSet()
            _processedJobIds.value = processedJobsSet.toSet()
            _appliedJobIds.value = appliedJobsSet.toSet()

            // 🚀 NEW: Update reconsidered state
            if (_isShowingRejectedJobs.value) {
                _reconsideredJobIds.value = reconsideredJobsSet.toSet()
            }

            if (!_isShowingRejectedJobs.value) {
                _sessionProcessedJobIds.value = sessionProcessedJobsSet.toSet()
            }

            Log.d(TAG, "⚡ BATCH: Marked ${jobIds.size} jobs as NOT_INTERESTED instantly")

            // 🚀 NEW: Save to storage asynchronously if reconsidered
            if (_isShowingRejectedJobs.value) {
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        reconsiderationStorage.addMultipleReconsideredJobIds(jobIds)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to save reconsidered jobs to storage: ${e.message}")
                    }
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error in batch NOT_INTERESTED marking: ${e.message}")
        }
    }

    /**
     * 🚀 Batch rejected marking - delegates to batch NOT_INTERESTED
     */
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

    /**
     * 🚀 NEW: Bulk update all job states including reconsidered
     */
    suspend fun updateAllJobIds(
        appliedIds: Set<String>,
        rejectedIds: Set<String>,
        reconsideredIds: Set<String> = emptySet()
    ) {
        try {
            withTimeoutOrNull(3000) {
                mutex.withLock {
                    Log.d(TAG, "Bulk updating: ${appliedIds.size} applied, ${rejectedIds.size} rejected, ${reconsideredIds.size} reconsidered")

                    // Update applied jobs
                    appliedJobsSet.clear()
                    appliedJobsSet.addAll(appliedIds)
                    _appliedJobIds.value = appliedJobsSet.toSet()

                    // Update rejected jobs
                    rejectedJobsSet.clear()
                    rejectedJobsSet.addAll(rejectedIds)
                    _rejectedJobIds.value = rejectedJobsSet.toSet()

                    // 🚀 NEW: Update reconsidered jobs
                    reconsideredJobsSet.clear()
                    reconsideredJobsSet.addAll(reconsideredIds)
                    _reconsideredJobIds.value = reconsideredJobsSet.toSet()

                    // Update processed jobs to union of applied and rejected
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

    // =============================================================================
    // 🚀 UPDATE METHODS
    // =============================================================================

    /**
     * Update the rejected job IDs
     * Uses mutex for thread safety
     * 🚀 OPTIMIZED: Added timeout and improved state management
     */
    suspend fun updateRejectedJobIds(notInterestedIds: Set<String>) {
        try {
            withTimeoutOrNull(2000) {
                mutex.withLock {
                    Log.d(TAG, "🚀 Updating NOT_INTERESTED job IDs in repository: ${notInterestedIds.size} ids")

                    rejectedJobsSet.clear()
                    rejectedJobsSet.addAll(notInterestedIds)
                    _rejectedJobIds.value = rejectedJobsSet.toSet()

                    // Update processed jobs to union of applied and not interested
                    processedJobsSet.clear()
                    processedJobsSet.addAll(appliedJobsSet)
                    processedJobsSet.addAll(rejectedJobsSet)
                    _processedJobIds.value = processedJobsSet.toSet()
                }
            } ?: Log.w(TAG, "updateRejectedJobIds timed out")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating NOT_INTERESTED job IDs: ${e.message}")
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

    // =============================================================================
    // 🚀 SESSION MANAGEMENT
    // =============================================================================

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
     * 🚀 ENHANCED: Session restoration with mode awareness
     */
    suspend fun restoreSessionState() {
        try {
            withTimeoutOrNull(2000) {
                mutex.withLock {
                    Log.d(TAG, "🚀 RESTORING session state")

                    if (_isShowingRejectedJobs.value) {
                        // 🚀 FIX: In rejected jobs mode, keep session clear to show all rejected jobs
                        sessionProcessedJobsSet.clear()
                        _sessionProcessedJobIds.value = emptySet()
                        Log.d(TAG, "Rejected jobs mode: keeping session processed jobs clear")
                    } else {
                        // 🚀 Regular mode: restore session from processed jobs
                        sessionProcessedJobsSet.clear()
                        sessionProcessedJobsSet.addAll(processedJobsSet)
                        _sessionProcessedJobIds.value = sessionProcessedJobsSet.toSet()
                        Log.d(TAG, "Regular mode: restored session state with ${sessionProcessedJobsSet.size} processed jobs")
                    }
                }
            } ?: run {
                Log.w(TAG, "Session restoration timed out, using current state")
                _sessionProcessedJobIds.value = sessionProcessedJobsSet.toSet()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring session state: ${e.message}")
            try {
                _sessionProcessedJobIds.value = sessionProcessedJobsSet.toSet()
            } catch (fallbackError: Exception) {
                Log.e(TAG, "Fallback session restoration failed: ${fallbackError.message}")
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

    // =============================================================================
    // 🚀 CLEANUP AND RESET METHODS
    // =============================================================================

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
     * 🚀 ENHANCED: Clear all processed job IDs including reconsidered
     * Uses mutex to prevent race conditions
     */
    suspend fun clearProcessedJobs() {
        try {
            withTimeoutOrNull(2000) {
                mutex.withLock {
                    appliedJobsSet.clear()
                    rejectedJobsSet.clear()
                    processedJobsSet.clear()
                    sessionProcessedJobsSet.clear()
                    reconsideredJobsSet.clear() // 🚀 NEW

                    _appliedJobIds.value = emptySet()
                    _rejectedJobIds.value = emptySet()
                    _processedJobIds.value = emptySet()
                    _sessionProcessedJobIds.value = emptySet()
                    _reconsideredJobIds.value = emptySet() // 🚀 NEW

                    Log.d(TAG, "Cleared all processed jobs including reconsidered")
                }
            } ?: Log.w(TAG, "clearProcessedJobs timed out")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing processed jobs: ${e.message}")
        }
    }

    // =============================================================================
    // 🚀 QUERY METHODS
    // =============================================================================

    /**
     * Check if a job has been processed in the current session
     * 🚀 OPTIMIZED: No mutex needed for read-only operation
     */
    fun isJobProcessedInCurrentSession(jobId: String): Boolean {
        return sessionProcessedJobsSet.contains(jobId)
    }

    /**
     * Check if a job has been applied to
     * 🚀 OPTIMIZED: No mutex needed for read-only operation
     */
    fun isJobApplied(jobId: String): Boolean {
        return appliedJobsSet.contains(jobId)
    }

    /**
     * Check if a job has been rejected/not interested
     * 🚀 OPTIMIZED: No mutex needed for read-only operation
     */
    fun isJobRejected(jobId: String): Boolean {
        return isJobNotInterested(jobId)
    }

    /**
     * Check if a job is marked as not interested
     * 🚀 OPTIMIZED: No mutex needed for read-only operation
     */
    fun isJobNotInterested(jobId: String): Boolean {
        return rejectedJobsSet.contains(jobId)
    }

    /**
     * Check if a job has been processed in any way (applied or rejected)
     * 🚀 OPTIMIZED: No mutex needed for read-only operation
     */
    fun isJobProcessed(jobId: String): Boolean {
        return processedJobsSet.contains(jobId)
    }

    /**
     * Get all jobs that have been processed (applied or rejected)
     * 🚀 OPTIMIZED: No mutex needed for read-only operation
     */
    fun getProcessedJobIds(): Set<String> {
        return processedJobsSet.toSet()
    }

    /**
     * Get rejected job IDs
     * 🚀 OPTIMIZED: No mutex needed for read-only operation
     */
    fun getRejectedJobIds(): Set<String> {
        return getNotInterestedJobIds()
    }

    /**
     * Get not interested job IDs
     * 🚀 OPTIMIZED: No mutex needed for read-only operation
     */
    fun getNotInterestedJobIds(): Set<String> {
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
     * Get unprocessed jobs count for UI display
     * 🚀 OPTIMIZED: No mutex needed for read-only operation
     */
    fun getUnprocessedJobsCount(totalJobs: Int): Int {
        return maxOf(0, totalJobs - processedJobsSet.size)
    }

    // =============================================================================
    // 🚀 DEBUGGING AND MONITORING
    // =============================================================================

    /**
     * 🚀 ENHANCED: Performance stats with reconsideration data
     */
    fun logPerformanceStats() {
        Log.d(TAG, "=== PROCESSED JOBS REPOSITORY STATS ===")
        Log.d(TAG, "Applied jobs: ${appliedJobsSet.size}")
        Log.d(TAG, "NOT_INTERESTED jobs: ${rejectedJobsSet.size}")
        Log.d(TAG, "Reconsidered jobs: ${reconsideredJobsSet.size}")
        Log.d(TAG, "Eligible for reconsideration: ${getEligibleForReconsideration().size}")
        Log.d(TAG, "Total processed: ${processedJobsSet.size}")
        Log.d(TAG, "Session processed: ${sessionProcessedJobsSet.size}")
        Log.d(TAG, "Showing rejected jobs: ${_isShowingRejectedJobs.value}")
        Log.d(TAG, "Session restoration needed: ${isSessionRestorationNeeded()}")
        Log.d(TAG, "==========================================")
    }
}

/**
 * 🚀 NEW: Data class for reconsideration statistics
 */
data class ReconsiderationStats(
    val totalRejected: Int,
    val reconsidered: Int,
    val eligibleForReconsideration: Int
) {
    val reconsiderationRate: Double
        get() = if (totalRejected > 0) reconsidered.toDouble() / totalRejected else 0.0

    val hasEligibleJobs: Boolean
        get() = eligibleForReconsideration > 0
}