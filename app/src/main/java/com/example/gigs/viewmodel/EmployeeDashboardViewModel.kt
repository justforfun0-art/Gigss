package com.example.gigs.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gigs.data.model.Activity
import com.example.gigs.data.model.ApplicationWithJob
import com.example.gigs.data.model.CategoryStat
import com.example.gigs.data.model.EmployeeDashboardData
import com.example.gigs.data.model.EmployerDashboardData
import com.example.gigs.data.model.EmployerProfile
import com.example.gigs.data.model.Job
import com.example.gigs.data.model.JobStatus
import com.example.gigs.data.model.LocationStat
import com.example.gigs.data.repository.ApplicationRepository
import com.example.gigs.data.repository.DashboardRepository
import com.example.gigs.data.repository.JobRepository
import com.example.gigs.data.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EmployeeDashboardViewModel @Inject constructor(
    private val dashboardRepository: DashboardRepository,
    private val applicationRepository: ApplicationRepository
) : ViewModel() {
    private val _dashboardData = MutableStateFlow<EmployeeDashboardData?>(null)
    val dashboardData: StateFlow<EmployeeDashboardData?> = _dashboardData

    private val _recentActivities = MutableStateFlow<List<Activity>>(emptyList())
    val recentActivities: StateFlow<List<Activity>> = _recentActivities

    private val _recentApplications = MutableStateFlow<List<ApplicationWithJob>>(emptyList())
    val recentApplications: StateFlow<List<ApplicationWithJob>> = _recentApplications

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    // Direct stats for UI display
    private val _totalApplications = MutableStateFlow(0)
    val totalApplications: StateFlow<Int> = _totalApplications

    private val _totalHired = MutableStateFlow(0)
    val totalHired: StateFlow<Int> = _totalHired

    private val _averageRating = MutableStateFlow(0.0f)
    val averageRating: StateFlow<Float> = _averageRating

    private val _totalReviews = MutableStateFlow(0)
    val totalReviews: StateFlow<Int> = _totalReviews

    fun loadDashboardData() {
        viewModelScope.launch {
            _isLoading.value = true

            // First load applications directly to calculate stats
            loadApplicationsAndCalculateStats()

            // Load additional dashboard data
            loadAdditionalDashboardData()

            _isLoading.value = false
        }
    }

    private suspend fun loadApplicationsAndCalculateStats() {
        try {
            println("Loading applications for employee dashboard")

            // Get all applications for the current employee
            applicationRepository.getMyApplications(100)
                .catch { e ->
                    println("Error loading employee applications: ${e.message}")
                }
                .collect { result ->
                    if (result.isSuccess) {
                        val applications = result.getOrNull() ?: emptyList()

                        // Store recent applications for display
                        _recentApplications.value = applications.take(5)

                        // Calculate stats directly from applications list
                        calculateStatsFromApplications(applications)

                        println("Loaded ${applications.size} applications for employee dashboard")
                    } else {
                        println("Failed to load applications: ${result.exceptionOrNull()?.message}")
                    }
                }
        } catch (e: Exception) {
            println("Error in loadApplicationsAndCalculateStats: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun calculateStatsFromApplications(applications: List<ApplicationWithJob>) {
        // Total applications count
        _totalApplications.value = applications.size

        // Count hired jobs
        _totalHired.value = applications.count { app ->
            val status = app.status?.toString()?.uppercase() ?: ""
            status == "HIRED" || status == "COMPLETED"
        }

        println("Calculated employee stats: Applications=${_totalApplications.value}, Hired=${_totalHired.value}")

        // Note: Rating and reviews would typically come from the dashboard repository
        // as they're feedback left by employers about this employee
    }

    private suspend fun loadAdditionalDashboardData() {
        try {
            // Load main dashboard data for rating and reviews
            dashboardRepository.getEmployeeDashboardData()
                .catch { e ->
                    println("Error loading employee dashboard data: ${e.message}")
                }
                .collect { result ->
                    if (result.isSuccess) {
                        val dashboardData = result.getOrNull()
                        _dashboardData.value = dashboardData

                        // Update rating and reviews from dashboard data
                        if (dashboardData != null) {
                            _averageRating.value = dashboardData.averageRating
                            _totalReviews.value = dashboardData.reviewCount

                            // Only update applications and hired if our direct calculation returned zero
                            if (_totalApplications.value == 0) {
                                _totalApplications.value = dashboardData.totalApplications
                            }
                            if (_totalHired.value == 0) {
                                _totalHired.value = dashboardData.hiredCount
                            }

                            println("Updated dashboard stats: Rating=${_averageRating.value}, Reviews=${_totalReviews.value}")
                        }
                    } else {
                        println("Failed to load employee dashboard data: ${result.exceptionOrNull()?.message}")
                    }
                }
        } catch (e: Exception) {
            println("Error loading additional dashboard data: ${e.message}")
        }

        try {
            // Load recent activities
            dashboardRepository.getRecentActivities(5)
                .catch { e ->
                    println("Error loading employee activities: ${e.message}")
                }
                .collect { result ->
                    if (result.isSuccess) {
                        _recentActivities.value = result.getOrNull() ?: emptyList()
                    } else {
                        println("Failed to load employee activities: ${result.exceptionOrNull()?.message}")
                    }
                }
        } catch (e: Exception) {
            println("Error loading employee activities: ${e.message}")
        }
    }

    /**
     * Direct method to get application history for the job history screen
     */
    fun loadApplicationHistory() {
        viewModelScope.launch {
            _isLoading.value = true

            try {
                // Load all applications (no limit)
                applicationRepository.getMyApplications(0)
                    .catch { e ->
                        println("Error loading application history: ${e.message}")
                        _recentApplications.value = emptyList()
                        _isLoading.value = false
                    }
                    .collect { result ->
                        if (result.isSuccess) {
                            val applications = result.getOrNull() ?: emptyList()
                            // Store all applications
                            _recentApplications.value = applications
                            println("Loaded ${applications.size} applications for job history")
                        } else {
                            println("Failed to load application history: ${result.exceptionOrNull()?.message}")
                            _recentApplications.value = emptyList()
                        }
                        _isLoading.value = false
                    }
            } catch (e: Exception) {
                println("Exception in loadApplicationHistory: ${e.message}")
                e.printStackTrace()
                _recentApplications.value = emptyList()
                _isLoading.value = false
            }
        }
    }

    fun refreshDashboard() {
        viewModelScope.launch {
            loadDashboardData()
        }
    }
}

@HiltViewModel
class EmployerDashboardViewModel @Inject constructor(
    private val dashboardRepository: DashboardRepository,
    private val jobRepository: JobRepository,
    private val applicationRepository: ApplicationRepository,
    private val profileRepository: ProfileRepository
) : ViewModel() {
    private val _dashboardData = MutableStateFlow<EmployerDashboardData?>(null)
    val dashboardData: StateFlow<EmployerDashboardData?> = _dashboardData

    private val _recentActivities = MutableStateFlow<List<Activity>>(emptyList())
    val recentActivities: StateFlow<List<Activity>> = _recentActivities

    private val _recentJobs = MutableStateFlow<List<Job>>(emptyList())
    val recentJobs: StateFlow<List<Job>> = _recentJobs

    private val _locationStats = MutableStateFlow<List<LocationStat>>(emptyList())
    val locationStats: StateFlow<List<LocationStat>> = _locationStats

    private val _categoryStats = MutableStateFlow<List<CategoryStat>>(emptyList())
    val categoryStats: StateFlow<List<CategoryStat>> = _categoryStats

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    // For directly exposing the stats in the dashboard
    private val _totalJobs = MutableStateFlow(0)
    val totalJobs: StateFlow<Int> = _totalJobs

    private val _activeJobs = MutableStateFlow(0)
    val activeJobs: StateFlow<Int> = _activeJobs

    private val _totalApplications = MutableStateFlow(0)
    val totalApplications: StateFlow<Int> = _totalApplications

    private val _averageRating = MutableStateFlow(0.0f)
    val averageRating: StateFlow<Float> = _averageRating

    // Added for employer profile
    private val _employerProfile = MutableStateFlow<EmployerProfile?>(null)
    val employerProfile: StateFlow<EmployerProfile?> = _employerProfile

    private val _isProfileLoading = MutableStateFlow(false)
    val isProfileLoading: StateFlow<Boolean> = _isProfileLoading

    fun loadDashboardData() {
        viewModelScope.launch {
            _isLoading.value = true

            // Load profile data
            loadEmployerProfile()

            // First load recent jobs - we'll use this to calculate stats directly
            try {
                jobRepository.getMyJobs(50).collect { result ->
                    if (result.isSuccess) {
                        val jobs = result.getOrNull() ?: emptyList()
                        _recentJobs.value = jobs.take(5)

                        // Calculate stats directly from the jobs list
                        calculateStatsFromJobs(jobs)

                        println("Found ${jobs.size} total jobs, set active jobs to ${_activeJobs.value}")
                    }
                }
            } catch (e: Exception) {
                println("Error loading jobs: ${e.message}")
            }

            // Load additional data without affecting our stats
            loadAdditionalDashboardData()
        }
    }

    /**
     * Load employer profile data
     */
    private fun loadEmployerProfile() {
        viewModelScope.launch {
            _isProfileLoading.value = true

            try {
                // Get current user ID from Firebase
                val userId = profileRepository.firebaseAuthManager.getCurrentUserId()

                if (userId != null) {
                    // Query the database directly for employer profile
                    getEmployerProfile(userId).collect { result ->
                        if (result.isSuccess) {
                            _employerProfile.value = result.getOrNull()
                            println("Loaded employer profile: ${_employerProfile.value?.companyName}")
                        } else {
                            println("Failed to load employer profile: ${result.exceptionOrNull()?.message}")
                        }
                        _isProfileLoading.value = false
                    }
                } else {
                    println("Error loading employer profile: User ID is null")
                    _isProfileLoading.value = false
                }
            } catch (e: Exception) {
                println("Exception in loadEmployerProfile: ${e.message}")
                e.printStackTrace()
                _isProfileLoading.value = false
            }
        }
    }

    /**
     * Get employer profile by user ID
     * This internal method serves as a workaround for the missing repository method
     */
    private fun getEmployerProfile(userId: String): Flow<Result<EmployerProfile>> = flow {
        try {
            val profile = profileRepository.supabaseClient
                .table("employer_profiles")
                .select {
                    filter {
                        eq("user_id", userId)
                    }
                }
                .decodeSingleOrNull<EmployerProfile>()

            if (profile != null) {
                emit(Result.success(profile))
            } else {
                emit(Result.failure(Exception("Employer profile not found")))
            }
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }

    /**
     * Load additional dashboard data that doesn't affect our primary stats
     */
    private fun loadAdditionalDashboardData() {
        viewModelScope.launch {
            try {
                // Load recent activities
                dashboardRepository.getRecentActivities(5).collect { result ->
                    if (result.isSuccess) {
                        _recentActivities.value = result.getOrNull() ?: emptyList()
                    }
                }
            } catch (e: Exception) {
                println("Error loading activities: ${e.message}")
            }

            try {
                // Load location stats
                dashboardRepository.getApplicationsByLocation().collect { result ->
                    if (result.isSuccess) {
                        _locationStats.value = result.getOrNull() ?: emptyList()
                    }
                }
            } catch (e: Exception) {
                println("Error loading location stats: ${e.message}")
            }

            try {
                // Load category stats
                dashboardRepository.getApplicationsByCategory().collect { result ->
                    if (result.isSuccess) {
                        _categoryStats.value = result.getOrNull() ?: emptyList()
                    }
                }
            } catch (e: Exception) {
                println("Error loading category stats: ${e.message}")
            }

            // For additional data that might be in the dashboard, load it last
            try {
                dashboardRepository.getEmployerDashboardData().collect { result ->
                    if (result.isSuccess) {
                        val dashboardData = result.getOrNull()
                        _dashboardData.value = dashboardData

                        // Only update rating from dashboard
                        if (dashboardData != null) {
                            _averageRating.value = dashboardData.averageRating
                        }
                    }
                }
            } catch (e: Exception) {
                println("Error loading dashboard data: ${e.message}")
            }

            _isLoading.value = false
        }
    }

    /**
     * Calculate stats directly from a list of jobs
     * This method doesn't use Flow so it avoids the Flow exception issues
     */
    private suspend fun calculateStatsFromJobs(jobs: List<Job>) {
        try {
            // Debug output
            println("Calculating stats from ${jobs.size} jobs")
            jobs.forEach { job ->
                println("Job ${job.id}: title=${job.title}, status=${job.status}")
            }

            // Update job counts
            _totalJobs.value = jobs.size

            // Count active jobs
            val activeCount = jobs.count { job ->
                val isActive = when {
                    // Try multiple approaches to find active jobs
                    job.status.toString().equals("APPROVED", ignoreCase = true) -> true
                    job.status == JobStatus.APPROVED -> true
                    job.isActive == true -> true
                    else -> false
                }
                println("Job ${job.id}: isActive = $isActive")
                isActive
            }
            _activeJobs.value = activeCount

            // Count applications for each job and sum them up
            var totalApplications = 0
            for (job in jobs) {
                try {
                    // Use catch operator to prevent Flow exceptions from propagating
                    applicationRepository.getApplicationsForJob(job.id)
                        .catch { e ->
                            println("Error fetching applications for job ${job.id}: ${e.message}")
                        }
                        .collect { result ->
                            if (result.isSuccess) {
                                val applications = result.getOrNull() ?: emptyList()
                                println("Job ${job.id}: found ${applications.size} applications")
                                totalApplications += applications.size
                            }
                        }
                } catch (e: Exception) {
                    println("Exception while counting applications for job ${job.id}: ${e.message}")
                }
            }
            _totalApplications.value = totalApplications

            println("Stats calculation complete: Total Jobs=${_totalJobs.value}, Active Jobs=${_activeJobs.value}, Total Applications=${_totalApplications.value}")
        } catch (e: Exception) {
            println("Error in calculateStatsFromJobs: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Force refresh all dashboard data
     */
    fun refreshDashboard() {
        viewModelScope.launch {
            _isLoading.value = true
            loadEmployerProfile()
            loadDashboardData()
        }
    }
}

@HiltViewModel
class EmployerApplicationsViewModel @Inject constructor(
    private val applicationRepository: ApplicationRepository,
    private val jobRepository: JobRepository
) : ViewModel() {
    private val _recentApplications = MutableStateFlow<List<ApplicationWithJob>>(emptyList())
    val recentApplications: StateFlow<List<ApplicationWithJob>> = _recentApplications

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    // Load recent applications across all jobs
    fun loadRecentApplications(limit: Int = 5) {
        viewModelScope.launch {
            _isLoading.value = true

            // List to collect all applications
            val allApplications = mutableListOf<ApplicationWithJob>()

            try {
                println("Starting to load applications for recent jobs")

                // First get the most recent jobs - try with a bigger limit
                jobRepository.getMyJobs(50)
                    .catch { e ->
                        println("Error getting jobs for applications: ${e.message}")
                    }
                    .collect { jobResult ->
                        if (jobResult.isSuccess) {
                            val jobs = jobResult.getOrNull() ?: emptyList()
                            println("Found ${jobs.size} jobs to check for applications")

                            if (jobs.isEmpty()) {
                                _recentApplications.value = emptyList()
                                _isLoading.value = false
                                return@collect
                            }

                            // Process each job individually and safely
                            for (job in jobs) {
                                try {
                                    // Use catch operator to prevent Flow exceptions from killing the whole process
                                    applicationRepository.getApplicationsForJob(job.id)
                                        .catch { e ->
                                            println("Error fetching applications for job ${job.id}: ${e.message}")
                                        }
                                        .collect { appResult ->
                                            if (appResult.isSuccess) {
                                                val applications = appResult.getOrNull() ?: emptyList()
                                                println("Job ${job.id}: found ${applications.size} applications")
                                                allApplications.addAll(applications)
                                            } else {
                                                println("Failed to get applications for job ${job.id}: ${appResult.exceptionOrNull()?.message}")
                                            }
                                        }
                                } catch (e: Exception) {
                                    // Catch any exceptions that weren't handled by the catch operator
                                    println("Exception while getting applications for job ${job.id}: ${e.message}")
                                }
                            }

                            // After processing all jobs, sort all applications by date and take only the limit
                            println("Total applications collected: ${allApplications.size}")
                            _recentApplications.value = allApplications
                                .sortedByDescending { it.appliedAt }
                                .take(limit)

                            println("Updated recent applications: ${_recentApplications.value.size}")
                        } else {
                            println("Failed to get jobs for applications: ${jobResult.exceptionOrNull()?.message}")
                        }

                        _isLoading.value = false
                    }
            } catch (e: Exception) {
                // Global error handler
                println("Error in loadRecentApplications: ${e.message}")
                e.printStackTrace()
                _isLoading.value = false
            }
        }
    }

    // Get applications for a specific job
    fun loadApplicationsForJob(jobId: String) {
        viewModelScope.launch {
            _isLoading.value = true

            try {
                println("Loading applications for job $jobId")

                applicationRepository.getApplicationsForJob(jobId)
                    .catch { e ->
                        println("Error loading applications for job $jobId: ${e.message}")
                        _recentApplications.value = emptyList()
                        _isLoading.value = false
                    }
                    .collect { result ->
                        if (result.isSuccess) {
                            val applications = result.getOrNull() ?: emptyList()
                            println("Found ${applications.size} applications for job $jobId")
                            _recentApplications.value = applications
                        } else {
                            println("Failed to get applications for job $jobId: ${result.exceptionOrNull()?.message}")
                            _recentApplications.value = emptyList()
                        }
                        _isLoading.value = false
                    }
            } catch (e: Exception) {
                println("Exception in loadApplicationsForJob: ${e.message}")
                e.printStackTrace()
                _recentApplications.value = emptyList()
                _isLoading.value = false
            }
        }
    }

    /**
     * Update an application status
     */
    suspend fun updateApplicationStatus(applicationId: String, newStatus: String): Boolean {
        return try {
            val result = applicationRepository.updateApplicationStatus(applicationId, newStatus).first()
            val success = result.isSuccess

            // Refresh the application list if the update was successful
            if (success) {
                coroutineScope {
                    launch {
                        // Get the current size to maintain the same number of items
                        val currentSize = _recentApplications.value.size
                        if (currentSize > 0) {
                            // Try to find job ID for the current applications
                            val jobId = _recentApplications.value.firstOrNull()?.jobId

                            if (jobId != null && _recentApplications.value.all { it.jobId == jobId }) {
                                // If all applications are for the same job, reload only that job's applications
                                loadApplicationsForJob(jobId)
                            } else {
                                // Otherwise reload all recent applications
                                loadRecentApplications(currentSize.coerceAtLeast(5))
                            }
                        }
                    }
                }
            }

            success
        } catch (e: Exception) {
            println("Error updating application status: ${e.message}")
            false
        }
    }

    // Direct fetch of application count - can be used for dashboard stats
    suspend fun getTotalApplicationCount(): Int {
        return try {
            var totalCount = 0

            // Get all jobs
            jobRepository.getMyJobs(100)
                .catch { e ->
                    println("Error getting jobs for counting: ${e.message}")
                }
                .collect { jobResult ->
                    if (jobResult.isSuccess) {
                        val jobs = jobResult.getOrNull() ?: emptyList()

                        // Count applications for each job
                        for (job in jobs) {
                            try {
                                applicationRepository.getApplicationsForJob(job.id)
                                    .catch { e ->
                                        println("Error fetching applications count for job ${job.id}: ${e.message}")
                                    }
                                    .collect { appResult ->
                                        if (appResult.isSuccess) {
                                            val applications = appResult.getOrNull() ?: emptyList()
                                            totalCount += applications.size
                                        }
                                    }
                            } catch (e: Exception) {
                                println("Exception counting applications for job ${job.id}: ${e.message}")
                            }
                        }
                    }
                }

            totalCount
        } catch (e: Exception) {
            println("Error in getTotalApplicationCount: ${e.message}")
            0
        }
    }
}
