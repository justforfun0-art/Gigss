package com.example.gigs.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
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
     */
    suspend fun setShowingRejectedJobs(showing: Boolean) {
        mutex.withLock {
            Log.d(TAG, "Setting showingRejectedJobs to $showing")
            _isShowingRejectedJobs.value = showing

            // When switching to rejected jobs mode, clear session processed jobs
            if (showing) {
                sessionProcessedJobsSet.clear()
                _sessionProcessedJobIds.value = emptySet()
            }
        }
    }

    /**
     * Generic method to add a job ID to the processed jobs
     * Used by both markJobAsApplied and markJobAsRejected
     */
    suspend fun addProcessedJob(jobId: String) {
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
    }

    /**
     * Add multiple job IDs to the set of processed jobs
     * Uses mutex to prevent race conditions
     */
    suspend fun addProcessedJobs(jobIds: Collection<String>) {
        if (jobIds.isEmpty()) return

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
    }

    /**
     * Check if a job has been processed in the current session
     */
    fun isJobProcessedInCurrentSession(jobId: String): Boolean {
        return sessionProcessedJobsSet.contains(jobId)
    }

    /**
     * Clear only the session processed jobs
     * Uses mutex for thread safety
     */
    suspend fun clearSessionProcessedJobs() {
        mutex.withLock {
            Log.d(TAG, "Clearing session processed jobs")
            sessionProcessedJobsSet.clear()
            _sessionProcessedJobIds.value = emptySet()
        }
    }

    /**
     * Initialize the repository with existing applied and rejected job IDs
     * Uses mutex for thread safety
     */
    suspend fun initializeWithExistingJobIds(appliedIds: Set<String>, rejectedIds: Set<String>) {
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
    }

    /**
     * Initialize only the applied jobs
     * Uses mutex for thread safety
     */
    suspend fun initializeAppliedJobs(jobIds: Set<String>) {
        mutex.withLock {
            appliedJobsSet.addAll(jobIds)
            _appliedJobIds.value = appliedJobsSet.toSet()

            processedJobsSet.addAll(jobIds)
            _processedJobIds.value = processedJobsSet.toSet()

            Log.d(TAG, "Initialized with ${jobIds.size} applied jobs")
        }
    }

    /**
     * Initialize only the rejected jobs
     * Uses mutex for thread safety
     */
    suspend fun initializeRejectedJobs(jobIds: Set<String>) {
        mutex.withLock {
            rejectedJobsSet.addAll(jobIds)
            _rejectedJobIds.value = rejectedJobsSet.toSet()

            processedJobsSet.addAll(jobIds)
            _processedJobIds.value = processedJobsSet.toSet()

            Log.d(TAG, "Initialized with ${jobIds.size} rejected jobs")
        }
    }

    /**
     * Mark a job as applied
     * Uses mutex for thread safety
     */
    suspend fun markJobAsApplied(jobId: String) {
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
    }

    /**
     * Mark a job as rejected
     * Uses mutex for thread safety
     */
    suspend fun markJobAsRejected(jobId: String) {
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
    }

    /**
     * Check if a job has been applied to
     */
    fun isJobApplied(jobId: String): Boolean {
        return appliedJobsSet.contains(jobId)
    }

    /**
     * Check if a job has been rejected
     */
    fun isJobRejected(jobId: String): Boolean {
        return rejectedJobsSet.contains(jobId)
    }

    /**
     * Get all jobs that have been processed (applied or rejected)
     */
    fun getProcessedJobIds(): Set<String> {
        return processedJobsSet.toSet()
    }

    /**
     * Update the session processed jobs
     * Uses mutex for thread safety
     */
    suspend fun updateSessionProcessedJobs(jobIds: Set<String>) {
        mutex.withLock {
            sessionProcessedJobsSet.clear()
            sessionProcessedJobsSet.addAll(jobIds)
            _sessionProcessedJobIds.value = sessionProcessedJobsSet.toSet()
            Log.d(TAG, "Updated session processed jobs, new count: ${jobIds.size}")
        }
    }

    /**
     * Reset all state
     * Uses mutex for thread safety
     */
    suspend fun refreshSessionState() {
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
    }

    /**
     * Check if a job has been processed in any way (applied or rejected)
     */
    fun isJobProcessed(jobId: String): Boolean {
        return processedJobsSet.contains(jobId)
    }

    /**
     * Update the rejected job IDs
     * Uses mutex for thread safety
     */
    suspend fun updateRejectedJobIds(rejectedIds: Set<String>) {
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
    }

    /**
     * Update the applied job IDs
     * Uses mutex for thread safety
     */
    suspend fun updateAppliedJobIds(appliedIds: Set<String>) {
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
    }

    /**
     * Clear all processed job IDs
     * Uses mutex to prevent race conditions
     */
    suspend fun clearProcessedJobs() {
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
    }
}