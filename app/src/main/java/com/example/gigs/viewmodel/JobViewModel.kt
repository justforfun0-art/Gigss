package com.example.gigs.viewmodel

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gigs.data.model.EmployeeProfile
import com.example.gigs.data.model.EmployerProfile
import com.example.gigs.data.model.Job
import com.example.gigs.data.repository.ApplicationRepository
import com.example.gigs.data.repository.AuthRepository
import com.example.gigs.data.repository.EmployerProfileRepository
import com.example.gigs.data.repository.JobRepository
import com.example.gigs.data.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class JobViewModel @Inject constructor(
    private val jobRepository: JobRepository,
    private val employerProfileRepository: EmployerProfileRepository,
    private val applicationRepository: ApplicationRepository,
    private val profileRepository: ProfileRepository,
    private val authRepository: AuthRepository,
    private val processedJobsRepository: ProcessedJobsRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val TAG = "JobViewModel"

    private val _jobs = MutableStateFlow<List<Job>>(emptyList())
    val jobs: StateFlow<List<Job>> = _jobs

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _selectedJob = MutableStateFlow<Job?>(null)
    val selectedJob: StateFlow<Job?> = _selectedJob

    private val _employerProfile = MutableStateFlow<EmployerProfile?>(null)
    val employerProfile: StateFlow<EmployerProfile?> = _employerProfile

    private val _hasApplied = MutableStateFlow(false)
    val hasApplied: StateFlow<Boolean> = _hasApplied

    private val _featuredJobs = MutableStateFlow<List<Job>>(emptyList())
    val featuredJobs: StateFlow<List<Job>> = _featuredJobs

    private val _applicationStatus = MutableStateFlow<ApplicationStatus>(ApplicationStatus.IDLE)
    val applicationStatus: StateFlow<ApplicationStatus> = _applicationStatus

    private val _employeeProfile = MutableStateFlow<EmployeeProfile?>(null)
    val employeeProfile: StateFlow<EmployeeProfile?> = _employeeProfile

    // Use the repository's state for showing rejected jobs
    val isShowingRejectedJobs = processedJobsRepository.isShowingRejectedJobs

    // Load the user's applications when the ViewModel is created
    init {
        loadUserApplications()
        loadRejectedJobs()
    }

    fun setShowingRejectedJobs(showing: Boolean) {
        processedJobsRepository.setShowingRejectedJobs(showing)
    }

    // Add this function to load all jobs the user has applied to
    private fun loadUserApplications() {
        viewModelScope.launch {
            try {
                applicationRepository.getMyApplications().collect { result ->
                    if (result.isSuccess) {
                        val applications = result.getOrNull() ?: emptyList()
                        val jobIds = applications.map { it.jobId }.toSet()

                        // Update the repository instead of local state
                        processedJobsRepository.initializeAppliedJobs(jobIds)

                        Log.d(TAG, "Loaded ${jobIds.size} applied job IDs")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading user applications: ${e.message}")
            }
        }
    }

    // Load rejected jobs from local storage or remote database
    private fun loadRejectedJobs() {
        viewModelScope.launch {
            try {
                jobRepository.getRejectedJobs().collect { result ->
                    if (result.isSuccess) {
                        val jobIds = result.getOrNull() ?: emptySet()

                        // Update the repository instead of local state
                        processedJobsRepository.initializeRejectedJobs(jobIds)

                        Log.d(TAG, "Loaded ${jobIds.size} rejected job IDs")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading rejected jobs: ${e.message}")
            }
        }
    }

    fun getMyJobs(limit: Int = 10) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                Log.d(TAG, "Loading my jobs with limit: $limit")
                jobRepository.getMyJobs(limit).collect { result ->
                    if (result.isSuccess) {
                        val myJobs = result.getOrNull() ?: emptyList()
                        Log.d(TAG, "Retrieved ${myJobs.size} jobs")
                        _jobs.value = myJobs
                    } else {
                        Log.e(TAG, "Error: ${result.exceptionOrNull()?.message}")
                        _jobs.value = emptyList()
                    }
                    _isLoading.value = false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception: ${e.message}")
                _isLoading.value = false
                _jobs.value = emptyList()
            }
        }
    }

    // Mark a job as not interested/rejected
    fun markJobAsNotInterested(jobId: String) {
        viewModelScope.launch {
            try {
                // Mark job as rejected in the repository
                processedJobsRepository.markJobAsRejected(jobId)

                // Save to backend repository
                jobRepository.markJobAsNotInterested(jobId).collect { result ->
                    if (result.isSuccess) {
                        Log.d(TAG, "Successfully marked job as not interested: $jobId")
                    } else {
                        Log.e(TAG, "Error marking job as not interested: ${result.exceptionOrNull()?.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception marking job as not interested: ${e.message}", e)
            }
        }
    }

    // Load employee profile
    fun getEmployeeProfile() {
        viewModelScope.launch {
            val userId = authRepository.getCurrentUserId() ?: return@launch
            try {
                profileRepository.getEmployeeProfileByUserId(userId).collect { result ->
                    if (result.isSuccess) {
                        _employeeProfile.value = result.getOrNull()
                        Log.d(TAG, "Loaded employee profile: ${_employeeProfile.value?.district}")
                    } else {
                        Log.e(TAG, "Error loading employee profile: ${result.exceptionOrNull()?.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception loading employee profile: ${e.message}")
            }
        }
    }

    // Get featured jobs filtered by district
    fun getLocalizedFeaturedJobs(district: String, limit: Int = 5) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                Log.d(TAG, "Getting localized featured jobs for district: $district")
                jobRepository.getJobsByLocation(district).collect { result ->
                    if (result.isSuccess) {
                        val allJobs = result.getOrNull() ?: emptyList()
                        Log.d(TAG, "Retrieved ${allJobs.size} jobs for district $district")

                        // Get processed jobs from the repository
                        val processedJobs = processedJobsRepository.getProcessedJobIds()

                        // Filter out jobs the user has already interacted with
                        val filteredJobs = allJobs.filter { job ->
                            !processedJobs.contains(job.id)
                        }

                        // Take only the requested limit
                        _featuredJobs.value = filteredJobs.take(limit)
                        Log.d(TAG, "Filtered to ${_featuredJobs.value.size} featured jobs")
                    } else {
                        Log.e(TAG, "Error: ${result.exceptionOrNull()?.message}")
                        // If there's an error getting localized jobs, fallback to regular featured jobs
                        getFeaturedJobs(limit)
                    }
                    _isLoading.value = false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception: ${e.message}")
                _isLoading.value = false
                // If there's an exception, fallback to regular featured jobs
                getFeaturedJobs(limit)
            }
        }
    }

    // Helper function to check if the user has applied to a job
    fun hasAppliedToJob(jobId: String): Boolean {
        return processedJobsRepository.isJobApplied(jobId)
    }

    // Helper function to check if the user has rejected a job
    fun hasRejectedJob(jobId: String): Boolean {
        return processedJobsRepository.isJobRejected(jobId)
    }

    // Helper function to check if job has been processed (applied or rejected)
    fun isJobProcessed(jobId: String): Boolean {
        return processedJobsRepository.isJobProcessed(jobId)
    }

    // Expose the repository's appliedJobIds StateFlow
    val appliedJobIds = processedJobsRepository.appliedJobIds

    // Apply for a job with Tinder-like swiping
    fun applyForJob(jobId: String) {
        viewModelScope.launch {
            _isLoading.value = true

            try {
                // Mark the job as applied in the repository
                processedJobsRepository.markJobAsApplied(jobId)

                Log.d(TAG, "Applying for job: $jobId")

                // If we're in "reconsidering rejected jobs" mode, update existing application
                if (processedJobsRepository.isShowingRejectedJobs.value) {
                    applicationRepository.updateApplicationStatus(jobId, "APPLIED").collect { result ->
                        handleApplicationResult(result, jobId)
                    }
                } else {
                    // Normal application flow
                    applicationRepository.applyForJob(jobId).collect { result ->
                        handleApplicationResult(result, jobId)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception applying for job: ${e.message}", e)
                _applicationStatus.value = ApplicationStatus.ERROR(e.message ?: "Unknown error")
                _isLoading.value = false
            }
        }
    }

    // Helper method to avoid code duplication
    private fun handleApplicationResult(result: Result<Any>, jobId: String) {
        if (result.isSuccess) {
            Log.d(TAG, "Successfully applied for job: $jobId")

            // Update local state to reflect the user has applied
            _hasApplied.value = true
            _applicationStatus.value = ApplicationStatus.SUCCESS

            // Repository already knows this job is applied

            // Get the employee's district for refreshing
            val district = _employeeProfile.value?.district

            // Refresh featured jobs to remove the applied one
            if (!district.isNullOrBlank()) {
                // If we have a district, get localized jobs
                getLocalizedFeaturedJobs(district, _featuredJobs.value.size + 1)
            } else {
                // Otherwise use regular featured jobs
                refreshFeaturedJobs()
            }
        } else {
            val errorMessage = result.exceptionOrNull()?.message ?: "Unknown error"
            Log.e(TAG, "Error applying for job: $errorMessage")

            // If the error is that the user already applied, update the UI state
            if (errorMessage.contains("already applied")) {
                _hasApplied.value = true
                // Make sure the repository knows this too
                processedJobsRepository.markJobAsApplied(jobId)
            }

            _applicationStatus.value = ApplicationStatus.ERROR(errorMessage)
        }
        _isLoading.value = false
    }

    sealed class ApplicationStatus {
        object IDLE : ApplicationStatus()
        object SUCCESS : ApplicationStatus()
        data class ERROR(val message: String) : ApplicationStatus()
    }

    // Refresh featured jobs (useful after applying or rejecting)
    private fun refreshFeaturedJobs() {
        // Get the current limit or use default
        val currentLimit = if (_featuredJobs.value.isNotEmpty()) {
            _featuredJobs.value.size
        } else {
            5
        }

        // Reload with same limit
        getFeaturedJobs(currentLimit)
    }

    // Get featured jobs and filter out processed jobs
    fun getFeaturedJobs(limit: Int = 5) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                jobRepository.getFeaturedJobs(limit * 2).collect { result -> // Get more than needed to account for filtering
                    if (result.isSuccess) {
                        val allJobs = result.getOrNull() ?: emptyList()

                        // Get all processed jobs from the repository
                        val processedJobs = processedJobsRepository.getProcessedJobIds()

                        // Filter out jobs the user has already interacted with
                        val filteredJobs = allJobs.filter { job ->
                            !processedJobs.contains(job.id)
                        }

                        // Take only the requested limit
                        _featuredJobs.value = filteredJobs.take(limit)

                        Log.d(TAG, "Loaded ${_featuredJobs.value.size} featured jobs after filtering")
                    }
                    _isLoading.value = false
                }
            } catch (e: Exception) {
                _isLoading.value = false
                _featuredJobs.value = emptyList()
                Log.e(TAG, "Error loading featured jobs: ${e.message}")
            }
        }
    }

    fun getJobDetails(jobId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Get job details
                jobRepository.getJobById(jobId).collect { result ->
                    if (result.isSuccess) {
                        val job = result.getOrNull()
                        _selectedJob.value = job

                        // Also load employer profile
                        job?.let { loadEmployerProfile(it.employerId) }
                    }
                    _isLoading.value = false
                }
            } catch (e: Exception) {
                _isLoading.value = false
            }
        }
    }

    private fun loadEmployerProfile(employerId: String) {
        viewModelScope.launch {
            employerProfileRepository.getEmployerProfile(employerId).collect { result ->
                if (result.isSuccess) {
                    _employerProfile.value = result.getOrNull()
                }
            }
        }
    }

    fun checkIfApplied(jobId: String) {
        // First check in repository
        if (processedJobsRepository.isJobApplied(jobId)) {
            _hasApplied.value = true
            return
        }

        // If not found in repository, check in database
        viewModelScope.launch {
            applicationRepository.hasUserAppliedToJob(jobId).collect { result ->
                val hasApplied = result.getOrNull() ?: false
                _hasApplied.value = hasApplied

                // If user has applied, update the repository
                if (hasApplied) {
                    processedJobsRepository.markJobAsApplied(jobId)
                }
            }
        }
    }

    // Add this new function to JobViewModel.kt
    fun getAllJobsWithoutFiltering(district: String, limit: Int = 10) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                Log.d(TAG, "Getting ALL jobs for district: $district (without filtering)")
                jobRepository.getJobsByLocation(district).collect { result ->
                    if (result.isSuccess) {
                        val allJobs = result.getOrNull() ?: emptyList()
                        Log.d(TAG, "Retrieved ${allJobs.size} jobs for district $district (no filtering)")

                        // IMPORTANT: Don't filter processed jobs here!
                        _featuredJobs.value = allJobs.take(limit)

                        Log.d(TAG, "Returning ${_featuredJobs.value.size} jobs without filtering")
                    } else {
                        Log.e(TAG, "Error: ${result.exceptionOrNull()?.message}")
                        _featuredJobs.value = emptyList()
                    }
                    _isLoading.value = false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception: ${e.message}")
                _isLoading.value = false
                _featuredJobs.value = emptyList()
            }
        }
    }

    /**
     * Get only jobs that the user has previously rejected
     */
    fun getOnlyRejectedJobs(district: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                Log.d(TAG, "Getting only rejected jobs for district: $district (isShowingRejectedJobs=${isShowingRejectedJobs.value})")
                applicationRepository.getMyApplications().collect {
                    // Update processed jobs after refresh
                    processedJobsRepository.refreshSessionState()
                jobRepository.getRejectedJobsDetails(district).collect { result ->
                    if (result.isSuccess) {
                        val rejectedJobs = result.getOrNull() ?: emptyList()
                        Log.d(TAG, "Retrieved ${rejectedJobs.size} rejected jobs: ${rejectedJobs.map { it.id }}")

                        // Update featured jobs with the rejected jobs
                        _featuredJobs.value = rejectedJobs
                    } else {
                        Log.e(TAG, "Error: ${result.exceptionOrNull()?.message}")
                        _featuredJobs.value = emptyList()
                    }
                    _isLoading.value = false
                }}
            } catch (e: Exception) {
                Log.e(TAG, "Exception: ${e.message}")
                _isLoading.value = false
                _featuredJobs.value = emptyList()
            }
        }
    }

    fun getJobsByDistrict(district: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                Log.d(TAG, "Getting jobs for district: $district")
                jobRepository.getJobsByLocation(district).collect { result ->
                    if (result.isSuccess) {
                        val allJobs = result.getOrNull() ?: emptyList()
                        Log.d(TAG, "Retrieved ${allJobs.size} jobs for district $district")

                        // Get processed jobs from the repository
                        val processedJobs = processedJobsRepository.getProcessedJobIds()

                        // Filter out processed jobs
                        val filteredJobs = allJobs.filter { job ->
                            !processedJobs.contains(job.id)
                        }

                        _jobs.value = filteredJobs
                    } else {
                        Log.e(TAG, "Error: ${result.exceptionOrNull()?.message}")
                        _jobs.value = emptyList()
                    }
                    _isLoading.value = false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception: ${e.message}")
                _isLoading.value = false
                _jobs.value = emptyList()
            }
        }
    }
}