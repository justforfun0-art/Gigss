package com.example.gigs.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository to store and manage processed job IDs across the application
 * This provides a central place to track which jobs the user has interacted with
 */
@Singleton
class ProcessedJobsRepository @Inject constructor() {
    private val TAG = "ProcessedJobsRepository"

    // Single source of truth for ALL jobs the user has interacted with
    private val _processedJobIds = MutableStateFlow<Set<String>>(emptySet())
    val processedJobIds = _processedJobIds.asStateFlow()

    // Track session-only processed jobs (for UI purposes)
    private val _sessionProcessedJobIds = MutableStateFlow<Set<String>>(emptySet())
    val sessionProcessedJobIds = _sessionProcessedJobIds.asStateFlow()

    // Track rejected jobs specifically (for "Reconsider" feature)
    private val _rejectedJobIds = MutableStateFlow<Set<String>>(emptySet())
    val rejectedJobIds = _sessionProcessedJobIds.asStateFlow()

    // Track applied jobs specifically (for status indicators)
    private val _appliedJobIds = MutableStateFlow<Set<String>>(emptySet())
    val appliedJobIds = _appliedJobIds.asStateFlow()

    // Flag to track if we're in "reconsider rejected jobs" mode
    private val _isShowingRejectedJobs = MutableStateFlow(false)
    val isShowingRejectedJobs = _isShowingRejectedJobs.asStateFlow()

    fun setShowingRejectedJobs(showing: Boolean) {
        Log.d(TAG, "Setting showingRejectedJobs to $showing")
        _isShowingRejectedJobs.value = showing

        // When switching to rejected jobs mode, clear session processed jobs
        if (showing) {
            _sessionProcessedJobIds.value = emptySet()
        }
    }

    fun isJobProcessedInCurrentSession(jobId: String): Boolean {
        return _sessionProcessedJobIds.value.contains(jobId)
    }

    fun clearSessionProcessedJobs() {
        Log.d(TAG, "Clearing session processed jobs")
        _sessionProcessedJobIds.value = emptySet()
    }

    fun initializeWithExistingJobIds(appliedIds: Set<String>, rejectedIds: Set<String>) {
        Log.d(TAG, "Initializing with ${appliedIds.size} applied jobs and ${rejectedIds.size} rejected jobs")

        // Initialize all relevant sets
        _appliedJobIds.value = appliedIds
        _rejectedJobIds.value = rejectedIds

        // Combined set for all processed jobs
        val allProcessed = appliedIds.union(rejectedIds)
        _processedJobIds.value = allProcessed

        Log.d(TAG, "Initialized with ${allProcessed.size} total processed jobs")
    }

    // Add these to your ProcessedJobsRepository
    fun initializeAppliedJobs(jobIds: Set<String>) {
        val currentProcessed = _processedJobIds.value.toMutableSet()
        currentProcessed.addAll(jobIds)
        _processedJobIds.value = currentProcessed

        val currentApplied = _appliedJobIds.value.toMutableSet()
        currentApplied.addAll(jobIds)
        _appliedJobIds.value = currentApplied

        Log.d(TAG, "Initialized with ${jobIds.size} applied jobs")
    }

    fun initializeRejectedJobs(jobIds: Set<String>) {
        val currentProcessed = _processedJobIds.value.toMutableSet()
        currentProcessed.addAll(jobIds)
        _processedJobIds.value = currentProcessed

        val currentRejected = _rejectedJobIds.value.toMutableSet()
        currentRejected.addAll(jobIds)
        _rejectedJobIds.value = currentRejected

        Log.d(TAG, "Initialized with ${jobIds.size} rejected jobs")
    }


    fun markJobAsApplied(jobId: String) {
        // Add to applied jobs
        val currentApplied = _appliedJobIds.value.toMutableSet()
        currentApplied.add(jobId)
        _appliedJobIds.value = currentApplied

        // IMPORTANT: Remove from rejected jobs if present
        val currentRejected = _rejectedJobIds.value.toMutableSet()
        if (currentRejected.contains(jobId)) {
            currentRejected.remove(jobId)
            _rejectedJobIds.value = currentRejected
            Log.d(TAG, "Removed job $jobId from rejected jobs as it's now applied")
        }
    }

        fun markJobAsRejected(jobId: String) {
        // Add to processed jobs
        val currentProcessed = _processedJobIds.value.toMutableSet()
        currentProcessed.add(jobId)
        _processedJobIds.value = currentProcessed

        // Add to rejected jobs
        val currentRejected = _rejectedJobIds.value.toMutableSet()
        currentRejected.add(jobId)
        _rejectedJobIds.value = currentRejected

        // Add to session processed jobs
        val currentSession = _sessionProcessedJobIds.value.toMutableSet()
        currentSession.add(jobId)
        _sessionProcessedJobIds.value = currentSession

        // Remove from applied if present
        val currentApplied = _appliedJobIds.value.toMutableSet()
        if (currentApplied.contains(jobId)) {
            currentApplied.remove(jobId)
            _appliedJobIds.value = currentApplied
        }

        Log.d(TAG, "Marked job $jobId as rejected")
    }

    fun isJobApplied(jobId: String): Boolean {
        return _appliedJobIds.value.contains(jobId)
    }

    fun isJobRejected(jobId: String): Boolean {
        return _rejectedJobIds.value.contains(jobId)
    }

    /**
     * Get all jobs that have been processed (applied or rejected)
     */
    fun getProcessedJobIds(): Set<String> {
        return _processedJobIds.value
    }

    fun updateSessionProcessedJobs(jobIds: Set<String>) {
        _sessionProcessedJobIds.value = jobIds
        Log.d(TAG, "Updated session processed jobs, new count: ${jobIds.size}")
    }

    // In ProcessedJobsRepository.kt
    fun refreshSessionState() {
        _appliedJobIds.value = emptySet()
        _rejectedJobIds.value = emptySet()
        _processedJobIds.value = emptySet()
        Log.d(TAG, "Reset all processed jobs state")
    }

    /**
     * Check if a job has been processed in any way (applied or rejected)
     */
    fun isJobProcessed(jobId: String): Boolean {
        return _processedJobIds.value.contains(jobId)
    }

    fun updateRejectedJobIds(rejectedIds: Set<String>) {
        Log.d(TAG, "Updating rejected job IDs in repository: ${rejectedIds.size} ids")
        _rejectedJobIds.value = rejectedIds

        // Update processed jobs to union of applied and rejected
        _processedJobIds.value = _appliedJobIds.value.union(rejectedIds)
    }

    fun updateAppliedJobIds(appliedIds: Set<String>) {
        Log.d(TAG, "Updating applied job IDs in repository: ${appliedIds.size} ids")
        _appliedJobIds.value = appliedIds

        // Update processed jobs to union of applied and rejected
        _processedJobIds.value = appliedIds.union(_rejectedJobIds.value)
    }
}