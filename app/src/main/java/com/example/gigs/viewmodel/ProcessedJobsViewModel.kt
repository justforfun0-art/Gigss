package com.example.gigs.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * ViewModel to store and manage processed job IDs across the application
 * This provides a central place to track which jobs the user has interacted with
 */
@HiltViewModel
class ProcessedJobsViewModel @Inject constructor() : ViewModel() {

    private val TAG = "ProcessedJobsViewModel"

    // MutableStateFlow to store the set of processed job IDs
    private val _processedJobIds = MutableStateFlow<Set<String>>(mutableSetOf())

    // Public StateFlow to expose the processed job IDs (immutable)
    val processedJobIds: StateFlow<Set<String>> = _processedJobIds.asStateFlow()

    /**
     * Add a job ID to the set of processed jobs
     */
    fun addProcessedJob(jobId: String) {
        val currentSet = _processedJobIds.value.toMutableSet()
        currentSet.add(jobId)
        _processedJobIds.value = currentSet
        Log.d(TAG, "Added job $jobId to processed set, new size: ${currentSet.size}")
    }

    /**
     * Add multiple job IDs to the set of processed jobs
     */
    fun addProcessedJobs(jobIds: Collection<String>) {
        val currentSet = _processedJobIds.value.toMutableSet()
        currentSet.addAll(jobIds)
        _processedJobIds.value = currentSet
        Log.d(TAG, "Added ${jobIds.size} jobs to processed set, new size: ${currentSet.size}")
    }

    /**
     * Check if a job ID has been processed
     */
    fun isJobProcessed(jobId: String): Boolean {
        return _processedJobIds.value.contains(jobId)
    }

    /**
     * Get all processed job IDs
     */
    fun getProcessedJobIds(): Set<String> {
        return _processedJobIds.value
    }

    /**
     * Clear all processed job IDs
     */
    fun clearProcessedJobs() {
        _processedJobIds.value = emptySet()
        Log.d(TAG, "Cleared all processed jobs")
    }

    /**
     * Initialize with rejected and applied job IDs
     */
    fun initializeWithExistingJobIds(appliedJobIds: Set<String>, rejectedJobIds: Set<String>) {
        val combinedSet = mutableSetOf<String>()
        combinedSet.addAll(appliedJobIds)
        combinedSet.addAll(rejectedJobIds)
        _processedJobIds.value = combinedSet
        Log.d(TAG, "Initialized processed jobs with ${appliedJobIds.size} applied jobs and ${rejectedJobIds.size} rejected jobs")
    }
}