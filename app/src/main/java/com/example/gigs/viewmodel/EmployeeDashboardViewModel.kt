package com.example.gigs.viewmodel

import android.util.Log
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
    private val TAG = "EmployerDashboardVM"

    // Dashboard data
    private val _dashboardData = MutableStateFlow<EmployerDashboardData?>(null)
    val dashboardData: StateFlow<EmployerDashboardData?> = _dashboardData

    // Recent activities
    private val _recentActivities = MutableStateFlow<List<Activity>>(emptyList())
    val recentActivities: StateFlow<List<Activity>> = _recentActivities

    // Recent jobs
    private val _recentJobs = MutableStateFlow<List<Job>>(emptyList())
    val recentJobs: StateFlow<List<Job>> = _recentJobs

    // Location statistics
    private val _locationStats = MutableStateFlow<List<LocationStat>>(emptyList())
    val locationStats: StateFlow<List<LocationStat>> = _locationStats

    // Category statistics
    private val _categoryStats = MutableStateFlow<List<CategoryStat>>(emptyList())
    val categoryStats: StateFlow<List<CategoryStat>> = _categoryStats

    // Loading state
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    // Dashboard statistics
    private val _totalJobs = MutableStateFlow(0)
    val totalJobs: StateFlow<Int> = _totalJobs

    private val _activeJobs = MutableStateFlow(0)
    val activeJobs: StateFlow<Int> = _activeJobs

    private val _totalApplications = MutableStateFlow(0)
    val totalApplications: StateFlow<Int> = _totalApplications

    private val _averageRating = MutableStateFlow(0.0f)
    val averageRating: StateFlow<Float> = _averageRating

    // Employer profile
    private val _employerProfile = MutableStateFlow<EmployerProfile?>(null)
    val employerProfile: StateFlow<EmployerProfile?> = _employerProfile

    private val _isProfileLoading = MutableStateFlow(false)
    val isProfileLoading: StateFlow<Boolean> = _isProfileLoading

    /**
     * Load all dashboard data in parallel using coroutines
     */
    fun loadDashboardData() {
        viewModelScope.launch {
            _isLoading.value = true

            try {
                // Run all data loading concurrently
                coroutineScope {
                    // Load profile data asynchronously
                    val profileDeferred = async { loadEmployerProfile() }

                    // Create async tasks for all data loading operations
                    val jobStatsDeferred = async { calculateJobStats() }
                    val activitiesDeferred = async {
                        try {
                            dashboardRepository.getRecentActivities(5).first()
                        } catch (e: Exception) {
                            Log.e(TAG, "Error loading activities: ${e.message}")
                            Result.failure<List<Activity>>(e)
                        }
                    }
                    val jobsDeferred = async {
                        try {
                            jobRepository.getMyJobs(5).first()
                        } catch (e: Exception) {
                            Log.e(TAG, "Error loading jobs: ${e.message}")
                            Result.failure<List<Job>>(e)
                        }
                    }
                    val locationStatsDeferred = async {
                        try {
                            dashboardRepository.getApplicationsByLocation().first()
                        } catch (e: Exception) {
                            Log.e(TAG, "Error loading location stats: ${e.message}")
                            Result.failure<List<LocationStat>>(e)
                        }
                    }
                    val categoryStatsDeferred = async {
                        try {
                            dashboardRepository.getApplicationsByCategory().first()
                        } catch (e: Exception) {
                            Log.e(TAG, "Error loading category stats: ${e.message}")
                            Result.failure<List<CategoryStat>>(e)
                        }
                    }
                    val dashboardDataDeferred = async {
                        try {
                            dashboardRepository.getEmployerDashboardData().first()
                        } catch (e: Exception) {
                            Log.e(TAG, "Error loading dashboard data: ${e.message}")
                            Result.failure<EmployerDashboardData>(e)
                        }
                    }

                    // Wait for profile to load (we don't need to process its result here)
                    profileDeferred.await()

                    // Wait for job stats calculation to complete
                    jobStatsDeferred.await()

                    // Process activities result
                    val activitiesResult = activitiesDeferred.await()
                    if (activitiesResult.isSuccess) {
                        _recentActivities.value = activitiesResult.getOrNull() ?: emptyList()
                        Log.d(TAG, "Loaded ${_recentActivities.value.size} activities")
                    }

                    // Process jobs result
                    val jobsResult = jobsDeferred.await()
                    if (jobsResult.isSuccess) {
                        _recentJobs.value = jobsResult.getOrNull() ?: emptyList()
                        Log.d(TAG, "Loaded ${_recentJobs.value.size} recent jobs")
                    }

                    // Process location stats result
                    val locationStatsResult = locationStatsDeferred.await()
                    if (locationStatsResult.isSuccess) {
                        _locationStats.value = locationStatsResult.getOrNull() ?: emptyList()
                        Log.d(TAG, "Loaded ${_locationStats.value.size} location stats")
                    }

                    // Process category stats result
                    val categoryStatsResult = categoryStatsDeferred.await()
                    if (categoryStatsResult.isSuccess) {
                        _categoryStats.value = categoryStatsResult.getOrNull() ?: emptyList()
                        Log.d(TAG, "Loaded ${_categoryStats.value.size} category stats")
                    }

                    // Process dashboard data result
                    val dashboardDataResult = dashboardDataDeferred.await()
                    if (dashboardDataResult.isSuccess) {
                        val dashboardData = dashboardDataResult.getOrNull()
                        _dashboardData.value = dashboardData

                        // Update our stats from the dashboard if available
                        if (dashboardData != null) {
                            // Only update average rating from dashboard data
                            // (other stats are calculated more accurately in calculateJobStats)
                            _averageRating.value = dashboardData.averageRating

                            Log.d(TAG, "Loaded dashboard data with rating: ${dashboardData.averageRating}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in loadDashboardData: ${e.message}", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Load employer profile data asynchronously
     */
    private suspend fun loadEmployerProfile() {
        _isProfileLoading.value = true

        try {
            // Get current user ID from repository
            val userId = profileRepository.firebaseAuthManager.getCurrentUserId()

            if (userId != null) {
                // Query the database for employer profile
                getEmployerProfile(userId).catch { e ->
                    Log.e(TAG, "Error loading employer profile: ${e.message}", e)
                }.collect { result ->
                    if (result.isSuccess) {
                        _employerProfile.value = result.getOrNull()
                        Log.d(TAG, "Loaded employer profile: ${_employerProfile.value?.companyName}")
                    } else {
                        Log.e(TAG, "Failed to load employer profile: ${result.exceptionOrNull()?.message}")
                    }
                }
            } else {
                Log.e(TAG, "Error loading employer profile: User ID is null")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception in loadEmployerProfile: ${e.message}", e)
        } finally {
            _isProfileLoading.value = false
        }
    }

    /**
     * Get employer profile by user ID
     */
    private fun getEmployerProfile(userId: String) = flow {
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
     * Calculate job statistics with parallel loading of application counts
     */
    private suspend fun calculateJobStats() {
        try {
            // Get all jobs to count
            val jobsResult = jobRepository.getMyJobs(0).first()
            if (jobsResult.isSuccess) {
                val jobs = jobsResult.getOrNull() ?: emptyList()

                // Update job counts
                _totalJobs.value = jobs.size

                // Count active jobs with reliable detection of status
                _activeJobs.value = jobs.count { job ->
                    when {
                        job.status?.name.equals("APPROVED", true) -> true
                        job.status == JobStatus.APPROVED -> true
                        job.isActive == true -> true
                        else -> false
                    }
                }

                Log.d(TAG, "Calculated job stats: ${jobs.size} total, ${_activeJobs.value} active")

                // Launch concurrent tasks to count applications for each job
                coroutineScope {
                    val applicationCountDeferreds = jobs.map { job ->
                        async {
                            try {
                                val appResult = applicationRepository.getApplicationsForJob(job.id).first()
                                if (appResult.isSuccess) {
                                    appResult.getOrNull()?.size ?: 0
                                } else {
                                    0
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error counting applications for job ${job.id}: ${e.message}")
                                0
                            }
                        }
                    }

                    // Wait for all application count tasks to complete
                    val applicationCounts = applicationCountDeferreds.awaitAll()

                    // Sum up all application counts
                    val totalApps = applicationCounts.sum()
                    _totalApplications.value = totalApps

                    Log.d(TAG, "Calculated total of $totalApps applications across ${jobs.size} jobs")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating job stats: ${e.message}", e)
        }
    }

    /**
     * Force refresh all dashboard data
     */
    fun refreshDashboard() {
        viewModelScope.launch {
            _isLoading.value = true
            // Reset data to ensure fresh load
            _dashboardData.value = null
            _recentActivities.value = emptyList()
            _recentJobs.value = emptyList()
            _locationStats.value = emptyList()
            _categoryStats.value = emptyList()
            _totalJobs.value = 0
            _activeJobs.value = 0
            _totalApplications.value = 0

            // Reload all data
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
