package com.example.gigs.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel to expose processed job IDs from the repository
 * Enhanced with session management and state restoration
 */
@HiltViewModel
class ProcessedJobsViewModel @Inject constructor(
    private val repository: ProcessedJobsRepository
) : ViewModel() {

    companion object {
        private const val TAG = "ProcessedJobsViewModel"
    }

    // Simply expose all repository state flows
    val processedJobIds = repository.processedJobIds
    val sessionProcessedJobIds = repository.sessionProcessedJobIds
    val appliedJobIds = repository.appliedJobIds
    val rejectedJobIds = repository.rejectedJobIds
    val isShowingRejectedJobs = repository.isShowingRejectedJobs
    val reconsideredJobIds = repository.reconsideredJobIds // 🚀 ADD THIS LINE

    // Additional state for UI
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        // Restore session state on initialization
        viewModelScope.launch {
            restoreSessionState()
        }
    }

    // Delegate all actions to the repository
    suspend fun markJobAsApplied(jobId: String) = repository.markJobAsApplied(jobId)
    suspend fun markJobAsRejected(jobId: String) = repository.markJobAsRejected(jobId)
    suspend fun setShowingRejectedJobs(showing: Boolean) = repository.setShowingRejectedJobs(showing)
    suspend fun clearSessionProcessedJobs() = repository.clearSessionProcessedJobs()
    fun isJobProcessedInCurrentSession(jobId: String) = repository.isJobProcessedInCurrentSession(jobId)
    fun isJobApplied(jobId: String) = repository.isJobApplied(jobId)
    fun isJobRejected(jobId: String) = repository.isJobRejected(jobId)

    suspend fun addToSessionProcessedJobs(jobId: String) {
        val current = sessionProcessedJobIds.value.toMutableSet()
        current.add(jobId)
        repository.updateSessionProcessedJobs(current)
    }

    /**
     * Restore session state when the screen is resumed
     * This ensures continuity when the app is backgrounded/resumed
     */
    suspend fun restoreSessionState() {
        _isLoading.value = true
        try {
            Log.d(TAG, "Restoring session state")
            // Your repository already has this method!
            repository.restoreSessionState()

            // Log current state after restoration
            Log.d(TAG, "Session restored - Applied: ${appliedJobIds.value.size}, Rejected: ${rejectedJobIds.value.size}")
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring session state", e)
        } finally {
            _isLoading.value = false
        }
    }

    /**
     * Add a processed job with a specific status
     * Used for backward compatibility with existing code
     */
    fun addProcessedJob(jobId: String, isApplied: Boolean) {
        viewModelScope.launch {
            if (isApplied) {
                markJobAsApplied(jobId)
            } else {
                markJobAsRejected(jobId)
            }
        }
    }

    /**
     * Toggle between showing all jobs and showing only rejected jobs
     */
    fun toggleRejectedJobsView() {
        viewModelScope.launch {
            val currentState = isShowingRejectedJobs.value
            setShowingRejectedJobs(!currentState)
        }
    }

    /**
     * Get statistics about processed jobs
     */
    fun getProcessedJobsStats(): ProcessedJobsStats {
        return ProcessedJobsStats(
            totalProcessed = processedJobIds.value.size,
            totalApplied = appliedJobIds.value.size,
            totalRejected = rejectedJobIds.value.size,
            sessionProcessed = sessionProcessedJobIds.value.size
        )
    }

    /**
     * Check if there are any rejected jobs that can be reconsidered
     */
    fun hasRejectedJobs(): Boolean = rejectedJobIds.value.isNotEmpty()

    /**
     * Clear all processed jobs (for testing or reset functionality)
     */
    suspend fun clearAllProcessedJobs() {
        Log.w(TAG, "Clearing all processed jobs")
        repository.clearProcessedJobs() // Using your existing method name
    }

    /**
     * Get unprocessed jobs count for UI display
     */
    fun getUnprocessedJobsCount(totalJobs: Int): Int {
        return repository.getUnprocessedJobsCount(totalJobs)
    }

    /**
     * Initialize with existing job IDs (e.g., from Firebase)
     */
    suspend fun initializeWithExistingJobIds(appliedIds: Set<String>, rejectedIds: Set<String>) {
        repository.initializeWithExistingJobIds(appliedIds, rejectedIds)
    }

    /**
     * Update rejected job IDs
     */
    suspend fun updateRejectedJobIds(rejectedIds: Set<String>) {
        repository.updateRejectedJobIds(rejectedIds)
    }

    /**
     * Update applied job IDs
     */
    suspend fun updateAppliedJobIds(appliedIds: Set<String>) {
        repository.updateAppliedJobIds(appliedIds)
    }
}

/**
 * Data class for processed jobs statistics
 */
data class ProcessedJobsStats(
    val totalProcessed: Int,
    val totalApplied: Int,
    val totalRejected: Int,
    val sessionProcessed: Int
)