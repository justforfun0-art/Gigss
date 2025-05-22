package com.example.gigs.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/**
 * ViewModel to expose processed job IDs from the repository
 */

@HiltViewModel
class ProcessedJobsViewModel @Inject constructor(
    private val repository: ProcessedJobsRepository
) : ViewModel() {
    // Simply expose all repository state flows
    val processedJobIds = repository.processedJobIds
    val sessionProcessedJobIds = repository.sessionProcessedJobIds
    val appliedJobIds = repository.appliedJobIds
    val rejectedJobIds = repository.rejectedJobIds
    val isShowingRejectedJobs = repository.isShowingRejectedJobs

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
}