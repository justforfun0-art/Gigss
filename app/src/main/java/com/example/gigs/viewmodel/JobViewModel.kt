package com.example.gigs.viewmodel

import android.util.Log
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
    private val processedJobsRepository: ProcessedJobsRepository // Now inject the repository instead of ViewModel
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

    // Track jobs the user has already applied to
    private val _appliedJobIds = MutableStateFlow<Set<String>>(emptySet())
    val appliedJobIds: StateFlow<Set<String>> = _appliedJobIds

    // Track jobs the user has rejected/marked as not interested
    private val _rejectedJobIds = MutableStateFlow<Set<String>>(emptySet())
    val rejectedJobIds: StateFlow<Set<String>> = _rejectedJobIds

    private val _applicationStatus = MutableStateFlow<ApplicationStatus>(ApplicationStatus.IDLE)
    val applicationStatus: StateFlow<ApplicationStatus> = _applicationStatus

    private val _employeeProfile = MutableStateFlow<EmployeeProfile?>(null)
    val employeeProfile: StateFlow<EmployeeProfile?> = _employeeProfile

    // Load the user's applications when the ViewModel is created
    init {
        loadUserApplications()
        loadRejectedJobs()
    }

    // Add this function to load all jobs the user has applied to
    private fun loadUserApplications() {
        viewModelScope.launch {
            try {
                applicationRepository.getMyApplications().collect { result ->
                    if (result.isSuccess) {
                        val applications = result.getOrNull() ?: emptyList()
                        val jobIds = applications.map { it.jobId }.toSet()
                        _appliedJobIds.value = jobIds
                        Log.d(TAG, "Loaded ${jobIds.size} applied job IDs")

                        // Sync with ProcessedJobsViewModel
                        processedJobsRepository.addProcessedJobs(jobIds)
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
                // You could store these in a local database or user preferences
                // For now, we'll try to get them from the repository if implemented
                jobRepository.getRejectedJobs().collect { result ->
                    if (result.isSuccess) {
                        val jobIds = result.getOrNull() ?: emptySet()
                        _rejectedJobIds.value = jobIds
                        Log.d(TAG, "Loaded ${jobIds.size} rejected job IDs")

                        // Sync with ProcessedJobsViewModel
                        processedJobsRepository.addProcessedJobs(jobIds)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading rejected jobs: ${e.message}")
                // If there's an error, just use an empty set
                _rejectedJobIds.value = emptySet()
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
                // Add to rejected jobs set locally
                val updatedRejectedJobs = _rejectedJobIds.value.toMutableSet()
                updatedRejectedJobs.add(jobId)
                _rejectedJobIds.value = updatedRejectedJobs

                // Add to ProcessedJobsViewModel for persistent tracking
                processedJobsRepository.addProcessedJob(jobId)

                // Save to repository if implemented
                jobRepository.markJobAsNotInterested(jobId).collect { result ->
                    if (result.isSuccess) {
                        Log.d(TAG, "Successfully marked job as not interested: $jobId")

                        // Get the employee's district for refreshing
                        val district = _employeeProfile.value?.district

                        // Refresh featured jobs to remove the rejected one
                        if (!district.isNullOrBlank()) {
                            // If we have a district, get localized jobs
                            getLocalizedFeaturedJobs(district, _featuredJobs.value.size + 1)
                        } else {
                            // Otherwise use regular featured jobs
                            refreshFeaturedJobs()
                        }
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
                // Use the proper repository to get employee profile
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

                        // Get combined processed jobs from the ViewModel instead of separate sets
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
        return _appliedJobIds.value.contains(jobId)
    }

    // Helper function to check if the user has rejected a job
    fun hasRejectedJob(jobId: String): Boolean {
        return _rejectedJobIds.value.contains(jobId)
    }

    // Helper function to check if job has been processed (applied or rejected)
    fun isJobProcessed(jobId: String): Boolean {
        return processedJobsRepository.isJobProcessed(jobId)
    }

    // Apply for a job with Tinder-like swiping
    fun applyForJob(jobId: String) {
        viewModelScope.launch {
            _isLoading.value = true

            try {
                // Mark the job as processed immediately in the persistent ViewModel
                processedJobsRepository.addProcessedJob(jobId)

                Log.d(TAG, "Applying for job: $jobId")
                applicationRepository.applyForJob(jobId).collect { result ->
                    if (result.isSuccess) {
                        Log.d(TAG, "Successfully applied for job: $jobId")

                        // Update local state to reflect the user has applied
                        _hasApplied.value = true

                        // Add the job ID to the applied jobs set
                        val updatedAppliedJobs = _appliedJobIds.value.toMutableSet()
                        updatedAppliedJobs.add(jobId)
                        _appliedJobIds.value = updatedAppliedJobs

                        _applicationStatus.value = ApplicationStatus.SUCCESS

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

                            // Also update the applied jobs set
                            val updatedAppliedJobs = _appliedJobIds.value.toMutableSet()
                            updatedAppliedJobs.add(jobId)
                            _appliedJobIds.value = updatedAppliedJobs
                        }

                        _applicationStatus.value = ApplicationStatus.ERROR(errorMessage)
                    }
                    _isLoading.value = false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception applying for job: ${e.message}", e)
                _applicationStatus.value = ApplicationStatus.ERROR(e.message ?: "Unknown error")
                _isLoading.value = false
            }
        }
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

                        // Get all processed jobs from the centralized ViewModel
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
        // First check in-memory cache
        if (_appliedJobIds.value.contains(jobId)) {
            _hasApplied.value = true
            return
        }

        // If not found in cache, check in database
        viewModelScope.launch {
            applicationRepository.hasUserAppliedToJob(jobId).collect { result ->
                val hasApplied = result.getOrNull() ?: false
                _hasApplied.value = hasApplied

                // If user has applied, update the cache
                if (hasApplied) {
                    val updatedAppliedJobs = _appliedJobIds.value.toMutableSet()
                    updatedAppliedJobs.add(jobId)
                    _appliedJobIds.value = updatedAppliedJobs

                    // Also update the ProcessedJobsViewModel
                    processedJobsRepository.addProcessedJob(jobId)
                }
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

                        // Get processed jobs from the centralized ViewModel
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