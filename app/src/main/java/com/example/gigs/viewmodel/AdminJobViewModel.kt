package com.example.gigs.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gigs.data.model.Job
import com.example.gigs.data.model.JobStatus
import com.example.gigs.data.repository.JobRepository
import com.example.gigs.data.repository.NotificationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AdminJobViewModel @Inject constructor(
    private val jobRepository: JobRepository,
    private val notificationRepository: NotificationRepository,
    private val authRepository: com.example.gigs.data.repository.AuthRepository
) : ViewModel() {
    private val _pendingJobs = MutableStateFlow<List<Job>>(emptyList())
    val pendingJobs: StateFlow<List<Job>> = _pendingJobs

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _actionState = MutableStateFlow<ActionState>(ActionState.Idle)
    val actionState: StateFlow<ActionState> = _actionState

    // Load all jobs pending approval
    fun loadPendingJobs() {
        viewModelScope.launch {
            _isLoading.value = true

            // Add try-catch for better error detection
            try {
                // Check if current user is admin
                val userId = authRepository.getCurrentUserId()
                if (userId == null) {
                    _isLoading.value = false
                    _actionState.value = ActionState.Error("User not authenticated")
                    return@launch
                }

                val isAdmin = authRepository.isUserAdmin()
                Log.d("AdminJobViewModel", "User $userId isAdmin: $isAdmin")

                if (!isAdmin) {
                    _isLoading.value = false
                    _actionState.value = ActionState.Error("Unauthorized access")
                    return@launch
                }

                jobRepository.getPendingJobs().collect { result ->
                    _isLoading.value = false
                    if (result.isSuccess) {
                        val jobs = result.getOrNull() ?: emptyList()
                        Log.d("AdminJobViewModel", "Fetched ${jobs.size} pending jobs")
                        _pendingJobs.value = jobs
                    } else {
                        val error = result.exceptionOrNull()?.message ?: "Failed to load pending jobs"
                        Log.e("AdminJobViewModel", "Error: $error")
                        _actionState.value = ActionState.Error(error)
                    }
                }
            } catch (e: Exception) {
                Log.e("AdminJobViewModel", "Exception in loadPendingJobs: ${e.message}")
                _isLoading.value = false
                _actionState.value = ActionState.Error(e.message ?: "Unknown error")
            }
        }
    }

    // Approve a job
    fun approveJob(jobId: String) {
        viewModelScope.launch {
            _isLoading.value = true

            // Check if current user is admin
            val userId = authRepository.getCurrentUserId()
            if (userId == null) {
                _isLoading.value = false
                _actionState.value = ActionState.Error("User not authenticated")
                return@launch
            }

            if (!authRepository.isUserAdmin()) {
                _isLoading.value = false
                _actionState.value = ActionState.Error("Unauthorized access")
                return@launch
            }

            // First get the job to access employer ID and title
            jobRepository.getJobById(jobId).collect { jobResult ->
                if (jobResult.isSuccess) {
                    val job = jobResult.getOrNull()

                    if (job != null) {
                        // Update job status to approved
                        jobRepository.updateJobStatus(jobId, JobStatus.APPROVED).collect { result ->
                            if (result.isSuccess) {
                                // Send notification to employer
                                notificationRepository.createJobApprovalNotification(
                                    userId = job.employerId,
                                    jobId = jobId,
                                    jobTitle = job.title
                                ).collect { notificationResult ->
                                    _isLoading.value = false

                                    if (notificationResult.isFailure) {
                                        // Log notification error but still consider job approved
                                        println("Failed to send notification: ${notificationResult.exceptionOrNull()?.message}")
                                    }

                                    _actionState.value = ActionState.Success("approve")
                                }
                            } else {
                                _isLoading.value = false
                                _actionState.value = ActionState.Error(
                                    result.exceptionOrNull()?.message ?: "Failed to approve job"
                                )
                            }
                        }
                    } else {
                        _isLoading.value = false
                        _actionState.value = ActionState.Error("Job not found")
                    }
                } else {
                    _isLoading.value = false
                    _actionState.value = ActionState.Error(
                        jobResult.exceptionOrNull()?.message ?: "Failed to get job details"
                    )
                }
            }
        }
    }

    // Reject a job
    fun rejectJob(jobId: String, reason: String) {
        viewModelScope.launch {
            _isLoading.value = true

            // Check if current user is admin
            val userId = authRepository.getCurrentUserId()
            if (userId == null) {
                _isLoading.value = false
                _actionState.value = ActionState.Error("User not authenticated")
                return@launch
            }

            if (!authRepository.isUserAdmin()) {
                _isLoading.value = false
                _actionState.value = ActionState.Error("Unauthorized access")
                return@launch
            }

            // First get the job to access employer ID and title
            jobRepository.getJobById(jobId).collect { jobResult ->
                if (jobResult.isSuccess) {
                    val job = jobResult.getOrNull()

                    if (job != null) {
                        // Update job status to rejected
                        jobRepository.updateJobStatus(jobId, JobStatus.REJECTED).collect { result ->
                            if (result.isSuccess) {
                                // Send notification to employer
                                notificationRepository.createJobRejectionNotification(
                                    userId = job.employerId,
                                    jobId = jobId,
                                    jobTitle = job.title,
                                    reason = reason
                                ).collect { notificationResult ->
                                    _isLoading.value = false

                                    if (notificationResult.isFailure) {
                                        // Log notification error but still consider job rejected
                                        println("Failed to send notification: ${notificationResult.exceptionOrNull()?.message}")
                                    }

                                    _actionState.value = ActionState.Success("reject")
                                }
                            } else {
                                _isLoading.value = false
                                _actionState.value = ActionState.Error(
                                    result.exceptionOrNull()?.message ?: "Failed to reject job"
                                )
                            }
                        }
                    } else {
                        _isLoading.value = false
                        _actionState.value = ActionState.Error("Job not found")
                    }
                } else {
                    _isLoading.value = false
                    _actionState.value = ActionState.Error(
                        jobResult.exceptionOrNull()?.message ?: "Failed to get job details"
                    )
                }
            }
        }
    }

    // Reset action state
    fun resetActionState() {
        _actionState.value = ActionState.Idle
    }

    // States for job actions
    sealed class ActionState {
        object Idle : ActionState()
        object Loading : ActionState()
        data class Success(val action: String) : ActionState()
        data class Error(val message: String) : ActionState()
    }
}