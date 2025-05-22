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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import androidx.lifecycle.viewModelScope
import com.example.gigs.ui.screens.jobs.JobFilters
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SortOption {
    DATE_NEWEST,
    DATE_OLDEST,
    SALARY_HIGH_LOW,
    SALARY_LOW_HIGH,
    ALPHABETICAL
}


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

    private val _currentSortOption = MutableStateFlow(SortOption.DATE_NEWEST)
    val currentSortOption: StateFlow<SortOption> = _currentSortOption

    private val _jobFilters = MutableStateFlow(JobFilters())
    val jobFilters: StateFlow<JobFilters> = _jobFilters



    // Use the repository's state for showing rejected jobs
    val isShowingRejectedJobs = processedJobsRepository.isShowingRejectedJobs

    // Load the user's applications when the ViewModel is created
    init {
        loadUserApplications()
        loadRejectedJobs()
    }


    // Add these methods to your JobViewModel class
    fun setSortOption(sortOption:SortOption) {
        _currentSortOption.value = sortOption
        // Apply sorting to current jobs
        applyFiltersAndSort()
    }

    fun setJobFilters(filters: JobFilters) {
        _jobFilters.value = filters
        // Apply filters to current jobs
        applyFiltersAndSort()
    }

    // This applies both filters and sorting without making a new network request
    private fun applyFiltersAndSort() {
        viewModelScope.launch {
            val allJobs = _jobs.value
            val filters = _jobFilters.value
            val sortOption = _currentSortOption.value

            // First filter the jobs
            val filteredJobs = applyFilters(allJobs, filters)

            // Then sort the filtered jobs
            val sortedJobs = applySorting(filteredJobs, sortOption)

            // Update the jobs state flow with the result
            _featuredJobs.value = sortedJobs
        }
    }

    // Modify your existing getJobsByDistrict method to incorporate sorting and filtering
    fun getJobsByDistrict(district: String, applyFiltersAndSort: Boolean = true) {
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

                        // If requested, apply the current filters and sorting
                        if (applyFiltersAndSort) {
                            val finalJobs = applySorting(
                                applyFilters(filteredJobs, _jobFilters.value),
                                _currentSortOption.value
                            )
                            _featuredJobs.value = finalJobs
                        } else {
                            _featuredJobs.value = filteredJobs
                        }
                    } else {
                        Log.e(TAG, "Error: ${result.exceptionOrNull()?.message}")
                        _jobs.value = emptyList()
                        _featuredJobs.value = emptyList()
                    }
                    _isLoading.value = false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception: ${e.message}")
                _isLoading.value = false
                _jobs.value = emptyList()
                _featuredJobs.value = emptyList()
            }
        }
    }

    // Helper methods for filtering and sorting
    private fun applyFilters(jobs: List<Job>, filters: JobFilters): List<Job> {
        return jobs.filter { job ->
            var matches = true

            // Filter by job type
            if (filters.jobType != null && job.jobType != filters.jobType) {
                matches = false
            }

            // Filter by salary range
            val jobSalary = extractSalaryAverage(job.salaryRange)
            if (jobSalary < filters.minSalary || jobSalary > filters.maxSalary) {
                matches = false
            }

            // Filter by location
            if (filters.location.isNotBlank() &&
                !job.location.contains(filters.location, ignoreCase = true) &&
                !job.district.contains(filters.location, ignoreCase = true) &&
                !job.state.contains(filters.location, ignoreCase = true)) {
                matches = false
            }

            // Filter by categories
            if (filters.categories.isNotEmpty() &&
                job.jobCategory?.let { !filters.categories.contains(it) } != false) {
                matches = false
            }

            // Add other filters as needed

            matches
        }
    }

    private fun applySorting(jobs: List<Job>, sortOption: SortOption): List<Job> {
        return when (sortOption) {
            SortOption.DATE_NEWEST -> jobs.sortedByDescending { it.createdAt }
            SortOption.DATE_OLDEST -> jobs.sortedBy { it.createdAt }
            SortOption.SALARY_HIGH_LOW -> jobs.sortedByDescending { extractSalaryAverage(it.salaryRange) }
            SortOption.SALARY_LOW_HIGH -> jobs.sortedBy { extractSalaryAverage(it.salaryRange) }
            SortOption.ALPHABETICAL -> jobs.sortedBy { it.title }
        }
    }

    private fun extractSalaryAverage(salaryRange: String?): Double {
        if (salaryRange.isNullOrEmpty()) return 0.0

        // Parse salary range like "₹500 - ₹1000" to get average
        val numbers = salaryRange.replace("[^0-9-]".toRegex(), "")
            .split("-")
            .mapNotNull { it.trim().toDoubleOrNull() }

        return if (numbers.size >= 2) {
            (numbers[0] + numbers[1]) / 2
        } else if (numbers.isNotEmpty()) {
            numbers[0]
        } else {
            0.0
        }
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
    // Load rejected jobs from remote database and sync with repository
    private fun loadRejectedJobs() {
        viewModelScope.launch {
            try {
                // Use the new method to fetch and sync rejected jobs
                jobRepository.fetchAndSyncRejectedJobs(processedJobsRepository).collect { result ->
                    if (result.isSuccess) {
                        Log.d(TAG, "Successfully synced ${result.getOrNull()?.size ?: 0} rejected jobs")
                    } else {
                        Log.e(TAG, "Error syncing rejected jobs: ${result.exceptionOrNull()?.message}")

                        // Fallback to local data if remote fetch fails
                        jobRepository.getRejectedJobs().collect { localResult ->
                            if (localResult.isSuccess) {
                                val jobIds = localResult.getOrNull() ?: emptySet()
                                processedJobsRepository.initializeRejectedJobs(jobIds)
                                Log.d(TAG, "Loaded ${jobIds.size} rejected job IDs from local storage")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception loading rejected jobs: ${e.message}")
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
                    applicationRepository.updateEmployeeApplicationStatus(jobId, "APPLIED").collect { result ->
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
    // Helper method to avoid code duplication
    private suspend fun handleApplicationResult(result: Result<Any>, jobId: String) {
        if (result.isSuccess) {
            Log.d(TAG, "Successfully applied for job: $jobId")

            // Update local state to reflect the user has applied
            _hasApplied.value = true
            _applicationStatus.value = ApplicationStatus.SUCCESS

            // Repository already knows this job is applied

            // Get the employee's district for refreshing
            val district = _employeeProfile.value?.district

            // Check if we're in reconsidering rejected jobs mode
            if (isShowingRejectedJobs.value) {
                // If in rejected jobs mode, refresh the rejected jobs list
                if (!district.isNullOrBlank()) {
                    getOnlyRejectedJobs(district)
                } else {
                    // If district is not available, still try to refresh rejected jobs
                    refreshRejectedJobs()
                }
            } else {
                // Otherwise refresh featured jobs to remove the applied one
                if (!district.isNullOrBlank()) {
                    // If we have a district, get localized jobs
                    getLocalizedFeaturedJobs(district, _featuredJobs.value.size + 1)
                } else {
                    // Otherwise use regular featured jobs
                    refreshFeaturedJobs()
                }
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
                        _jobs.value = allJobs.take(limit)

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
    /**
     * Get only jobs that the user has previously rejected
     */
    fun getOnlyRejectedJobs(district: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                Log.d(TAG, "Getting only rejected jobs for district: $district (isShowingRejectedJobs=${isShowingRejectedJobs.value})")

                // First fetch and sync rejected jobs from the server
                jobRepository.fetchAndSyncRejectedJobs(processedJobsRepository).collect { syncResult ->
                    if (syncResult.isSuccess) {
                        Log.d(TAG, "Successfully synced rejected jobs from server")

                        // Now get the details of rejected jobs for the specific district
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
                        }
                    } else {
                        Log.e(TAG, "Error syncing rejected jobs: ${syncResult.exceptionOrNull()?.message}")
                        _isLoading.value = false
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception: ${e.message}")
                _isLoading.value = false
                _featuredJobs.value = emptyList()
            }
        }
    }

    /**
     * Refresh the rejected jobs list by fetching from server
     */
    fun refreshRejectedJobs() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                jobRepository.fetchAndSyncRejectedJobs(processedJobsRepository).collect { result ->
                    _isLoading.value = false
                    if (result.isSuccess) {
                        Log.d(TAG, "Successfully refreshed rejected jobs")

                        // If we're in rejected jobs mode, refresh the display
                        if (isShowingRejectedJobs.value && _employeeProfile.value?.district != null) {
                            getOnlyRejectedJobs(_employeeProfile.value!!.district!!)
                        }
                    } else {
                        Log.e(TAG, "Error refreshing rejected jobs: ${result.exceptionOrNull()?.message}")
                    }
                }
            } catch (e: Exception) {
                _isLoading.value = false
                Log.e(TAG, "Exception refreshing rejected jobs: ${e.message}")
            }
        }
    }

    suspend fun setShowingRejectedJobs(showing: Boolean) {
        processedJobsRepository.setShowingRejectedJobs(showing)

        // If switching to rejected jobs mode, refresh the data
        if (showing) {
            refreshRejectedJobs()
        }
    }
    // Add these methods to the JobViewModel class
    private fun updateJobInState(updatedJob: Job) {
        _jobs.update { currentJobs ->
            currentJobs.map { job ->
                if (job.id == updatedJob.id) updatedJob else job
            }
        }
    }

    private fun updateFeaturedJobInState(updatedJob: Job) {
        _featuredJobs.update { currentJobs ->
            currentJobs.map { job ->
                if (job.id == updatedJob.id) updatedJob else job
            }
        }
    }

    // Remove job from list without reloading
    private fun removeJobFromFeaturedJobs(jobId: String) {
        _featuredJobs.update { currentJobs ->
            currentJobs.filter { it.id != jobId }
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