package com.example.gigs.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gigs.data.model.Job
import com.example.gigs.data.model.JobStatus
import com.example.gigs.data.model.WorkPreference
import com.example.gigs.data.model.WorkType
import com.example.gigs.data.repository.AuthRepository
import com.example.gigs.data.repository.JobRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class JobPostingViewModel @Inject constructor(
    private val jobRepository: JobRepository,
    private val authRepository: AuthRepository // Add auth repository
) : ViewModel() {
    private val _jobState = MutableStateFlow<JobState>(JobState.Initial)
    val jobState: StateFlow<JobState> = _jobState

    fun getCurrentUserId(): String? {
        return authRepository.getCurrentUserId()
    }

    // Create job with the enhanced fields
    fun createJob(job: Job) {
        viewModelScope.launch {
            _jobState.value = JobState.Loading

            // Convert the Job model to JobCreationData for the repository
            // This is a simplified version - adjust based on your actual implementation
            val jobData = com.example.gigs.data.model.JobCreationData(
                title = job.title,
                description = job.description,
                location = job.location,
                salaryRange = job.salaryRange ?: "",
                jobType = convertWorkTypeToPreference(job.workType),
                skillsRequired = job.skillsRequired,
                requirements = job.skillsRequired, // Using required skills as requirements
                applicationDeadline = null, // You can add this field to your Job model
                tags = emptyList(), // You can add this field to your Job model
                jobCategory = job.district // Using district as job category for now
            )

            jobRepository.createJob(jobData).collect { result ->
                _jobState.value = if (result.isSuccess) {
                    // Update the job status to PENDING_APPROVAL
                    result.getOrNull()?.let { createdJob ->
                        // In a real implementation, you'd update the job status in the database
                        // For now, we'll just return success with the created job
                        JobState.Success(createdJob)
                    } ?: JobState.Error("Failed to create job")
                } else {
                    JobState.Error(result.exceptionOrNull()?.message ?: "Failed to create job")
                }
            }
        }
    }

    // Helper function to convert WorkType to WorkPreference
    private fun convertWorkTypeToPreference(workType: WorkType): WorkPreference {
        return when (workType) {
            WorkType.FULL_TIME -> WorkPreference.FULL_TIME
            WorkType.PART_TIME -> WorkPreference.PART_TIME
            WorkType.TEMPORARY -> WorkPreference.TEMPORARY
            else -> WorkPreference.FULL_TIME // Default
        }
    }

    // States for job posting
    sealed class JobState {
        object Initial : JobState()
        object Loading : JobState()
        data class Success(val job: Job) : JobState()
        data class Error(val message: String) : JobState()
    }
}

sealed class JobPostingState {
    object Initial : JobPostingState()
    object Loading : JobPostingState()
    data class Success(val job: Job) : JobPostingState()
    data class Error(val message: String) : JobPostingState()
}