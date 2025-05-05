package com.example.gigs.viewmodel

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * ViewModel to expose processed job IDs from the repository
 */
@HiltViewModel
class ProcessedJobsViewModel @Inject constructor(
    private val processedJobsRepository: ProcessedJobsRepository
) : ViewModel() {

    // Expose the repository's StateFlow
    val processedJobIds = processedJobsRepository.processedJobIds

    /**
     * Add a job ID to the set of processed jobs
     */
    fun addProcessedJob(jobId: String) {
        processedJobsRepository.addProcessedJob(jobId)
    }

    /**
     * Add multiple job IDs to the set of processed jobs
     */
    fun addProcessedJobs(jobIds: Collection<String>) {
        processedJobsRepository.addProcessedJobs(jobIds)
    }

    /**
     * Check if a job ID has been processed
     */
    fun isJobProcessed(jobId: String): Boolean {
        return processedJobsRepository.isJobProcessed(jobId)
    }

    /**
     * Get all processed job IDs
     */
    fun getProcessedJobIds(): Set<String> {
        return processedJobsRepository.getProcessedJobIds()
    }

    /**
     * Clear all processed job IDs
     */
    fun clearProcessedJobs() {
        processedJobsRepository.clearProcessedJobs()
    }

    /**
     * Initialize with rejected and applied job IDs
     */
    fun initializeWithExistingJobIds(appliedJobIds: Set<String>, rejectedJobIds: Set<String>) {
        processedJobsRepository.initializeWithExistingJobIds(appliedJobIds, rejectedJobIds)
    }
}