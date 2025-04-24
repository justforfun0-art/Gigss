package com.example.gigs.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gigs.data.model.EmployerProfile
import com.example.gigs.data.model.Job
import com.example.gigs.data.repository.ApplicationRepository
import com.example.gigs.data.repository.EmployerProfileRepository
import com.example.gigs.data.repository.JobRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class JobViewModel @Inject constructor(
    private val jobRepository: JobRepository,
    private val employerProfileRepository: EmployerProfileRepository,
    private val applicationRepository: ApplicationRepository
) : ViewModel() {

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

    private val _applicationStatus = MutableStateFlow<ApplicationStatus>(ApplicationStatus.IDLE)
    val applicationStatus: StateFlow<ApplicationStatus> = _applicationStatus

    // Load the user's applications when the ViewModel is created
    init {
        loadUserApplications()
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
                        Log.d("JobViewModel", "Loaded ${jobIds.size} applied job IDs")
                    }
                }
            } catch (e: Exception) {
                Log.e("JobViewModel", "Error loading user applications: ${e.message}")
            }
        }
    }

    // Helper function to check if the user has applied to a job
    fun hasAppliedToJob(jobId: String): Boolean {
        return _appliedJobIds.value.contains(jobId)
    }

    fun applyForJob(jobId: String) {
        viewModelScope.launch {
            _isLoading.value = true

            try {
                Log.d("JobViewModel", "Applying for job: $jobId")
                applicationRepository.applyForJob(jobId).collect { result ->
                    if (result.isSuccess) {
                        Log.d("JobViewModel", "Successfully applied for job: $jobId")

                        // Update local state to reflect the user has applied
                        _hasApplied.value = true

                        // Add the job ID to the applied jobs set
                        val updatedAppliedJobs = _appliedJobIds.value.toMutableSet()
                        updatedAppliedJobs.add(jobId)
                        _appliedJobIds.value = updatedAppliedJobs

                        _applicationStatus.value = ApplicationStatus.SUCCESS
                    } else {
                        val errorMessage = result.exceptionOrNull()?.message ?: "Unknown error"
                        Log.e("JobViewModel", "Error applying for job: $errorMessage")

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
                Log.e("JobViewModel", "Exception applying for job: ${e.message}", e)
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

    fun getFeaturedJobs(limit: Int = 5) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                jobRepository.getFeaturedJobs(limit).collect { result ->
                    if (result.isSuccess) {
                        _featuredJobs.value = result.getOrNull() ?: emptyList()
                    }
                    _isLoading.value = false
                }
            } catch (e: Exception) {
                _isLoading.value = false
                _featuredJobs.value = emptyList()
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
                }
            }
        }
    }

    fun getJobsByDistrict(district: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                Log.d("JobViewModel", "Getting jobs for district: $district")
                jobRepository.getJobsByLocation(district).collect { result ->
                    if (result.isSuccess) {
                        val jobs = result.getOrNull() ?: emptyList()
                        Log.d("JobViewModel", "Retrieved ${jobs.size} jobs for district $district")
                        _jobs.value = jobs
                    } else {
                        Log.e("JobViewModel", "Error: ${result.exceptionOrNull()?.message}")
                        _jobs.value = emptyList()
                    }
                    _isLoading.value = false
                }
            } catch (e: Exception) {
                Log.e("JobViewModel", "Exception: ${e.message}")
                _isLoading.value = false
                _jobs.value = emptyList()
            }
        }
    }
}