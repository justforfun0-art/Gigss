package com.example.gigs.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gigs.data.model.Activity
import com.example.gigs.data.model.ApplicationWithJob
import com.example.gigs.data.model.CategoryStat
import com.example.gigs.data.model.EmployeeDashboardData
import com.example.gigs.data.model.EmployerDashboardData
import com.example.gigs.data.model.Job
import com.example.gigs.data.model.LocationStat
import com.example.gigs.data.repository.ApplicationRepository
import com.example.gigs.data.repository.DashboardRepository
import com.example.gigs.data.repository.JobRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
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

    fun loadDashboardData() {
        viewModelScope.launch {
            _isLoading.value = true

            // Load main dashboard data
            dashboardRepository.getEmployeeDashboardData().collect { result ->
                if (result.isSuccess) {
                    _dashboardData.value = result.getOrNull()
                }
            }

            // Load recent activities
            dashboardRepository.getRecentActivities(5).collect { result ->
                if (result.isSuccess) {
                    _recentActivities.value = result.getOrNull() ?: emptyList()
                }
            }

            // Load recent applications
            applicationRepository.getMyApplications(5).collect { result ->
                if (result.isSuccess) {
                    _recentApplications.value = result.getOrNull() ?: emptyList()
                }
            }

            _isLoading.value = false
        }
    }
}

@HiltViewModel
class EmployerDashboardViewModel @Inject constructor(
    private val dashboardRepository: DashboardRepository,
    private val jobRepository: JobRepository,
    private val applicationRepository: ApplicationRepository
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

    fun loadDashboardData() {
        viewModelScope.launch {
            _isLoading.value = true

            // Calculate the job counts directly for immediate access
            calculateJobStats()

            // Load recent activities
            dashboardRepository.getRecentActivities(5).collect { result ->
                if (result.isSuccess) {
                    _recentActivities.value = result.getOrNull() ?: emptyList()
                }
            }

            // Load recent jobs
            jobRepository.getMyJobs(5).collect { result ->
                if (result.isSuccess) {
                    _recentJobs.value = result.getOrNull() ?: emptyList()
                }
            }

            // Load location stats
            dashboardRepository.getApplicationsByLocation().collect { result ->
                if (result.isSuccess) {
                    _locationStats.value = result.getOrNull() ?: emptyList()
                }
            }

            // Load category stats
            dashboardRepository.getApplicationsByCategory().collect { result ->
                if (result.isSuccess) {
                    _categoryStats.value = result.getOrNull() ?: emptyList()
                }
            }

            // At this point, all dashboard data should be loaded
            // Get official dashboard data (might be used for other info)
            dashboardRepository.getEmployerDashboardData().collect { result ->
                if (result.isSuccess) {
                    val dashboardData = result.getOrNull()
                    _dashboardData.value = dashboardData

                    // Update our stats from the dashboard if available
                    if (dashboardData != null) {
                        // Only update these if not already set by calculateJobStats
                        if (_totalJobs.value == 0) {
                            _totalJobs.value = dashboardData.totalJobs
                        }
                        if (_activeJobs.value == 0) {
                            _activeJobs.value = dashboardData.activeJobs
                        }
                        if (_totalApplications.value == 0) {
                            _totalApplications.value = dashboardData.totalApplicationsReceived
                        }
                        _averageRating.value = dashboardData.averageRating
                    }
                }

                _isLoading.value = false
            }
        }
    }

    /**
     * Calculate job statistics directly from repositories
     * This provides immediate accurate counts for the dashboard UI
     */
    private suspend fun calculateJobStats() {
        try {
            // Get all jobs to count
            val jobsResult = jobRepository.getMyJobs(0).first()
            if (jobsResult.isSuccess) {
                val jobs = jobsResult.getOrNull() ?: emptyList()

                // Update job counts
                _totalJobs.value = jobs.size
                _activeJobs.value = jobs.count {
                    it.status?.name.equals("APPROVED", true) // Using enum's name property
                }

                // Count applications for all jobs
                var totalApps = 0
                for (job in jobs) {
                    val appResult = applicationRepository.getApplicationsForJob(job.id).first()
                    if (appResult.isSuccess) {
                        val applications = appResult.getOrNull() ?: emptyList()
                        totalApps += applications.size
                    }
                }
                _totalApplications.value = totalApps
            }
        } catch (e: Exception) {
            // If there's an error, we'll rely on the dashboard values instead
            println("Error calculating job stats: ${e.message}")
        }
    }

    /**
     * Force refresh all dashboard data
     */
    fun refreshDashboard() {
        viewModelScope.launch {
            _isLoading.value = true
            calculateJobStats()
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

            // First get user's jobs
            jobRepository.getMyJobs(0).collect { jobResult ->
                if (jobResult.isSuccess) {
                    val jobs = jobResult.getOrNull() ?: emptyList()

                    if (jobs.isEmpty()) {
                        _recentApplications.value = emptyList()
                        _isLoading.value = false
                        return@collect
                    }

                    // For each job, get applications
                    val allApplications = mutableListOf<ApplicationWithJob>()

                    jobs.forEach { job ->
                        try {
                            applicationRepository.getApplicationsForJob(job.id).collect { appResult ->
                                if (appResult.isSuccess) {
                                    val applications = appResult.getOrNull() ?: emptyList()
                                    allApplications.addAll(applications)
                                }
                            }
                        } catch (e: Exception) {
                            // Log error but continue with other jobs
                            println("Error fetching applications for job ${job.id}: ${e.message}")
                        }
                    }

                    // Sort all applications by date (newest first) and take only the requested limit
                    _recentApplications.value = allApplications
                        .sortedByDescending { it.appliedAt }
                        .take(limit)

                    _isLoading.value = false
                } else {
                    _isLoading.value = false
                }
            }
        }
    }

    // Get applications for a specific job
    fun loadApplicationsForJob(jobId: String) {
        viewModelScope.launch {
            _isLoading.value = true

            applicationRepository.getApplicationsForJob(jobId).collect { result ->
                if (result.isSuccess) {
                    _recentApplications.value = result.getOrNull() ?: emptyList()
                } else {
                    // Handle error
                    _recentApplications.value = emptyList()
                }
                _isLoading.value = false
            }
        }
    }

    /**
     * Update an application status
     */
    /**
     * Update an application status
     */
    suspend fun updateApplicationStatus(applicationId: String, newStatus: String): Boolean {
        return try {
            // Use first() to get the first emission only
            val result = applicationRepository.updateApplicationStatus(applicationId, newStatus).first()
            val success = result.isSuccess && result.getOrNull() == true

            // Refresh the application list if the update was successful
            if (success) {
                if (_recentApplications.value.isNotEmpty()) {
                    val jobId = _recentApplications.value.firstOrNull()?.jobId

                    // Use coroutineScope to ensure all refreshing is done before returning
                    coroutineScope {
                        if (jobId != null && _recentApplications.value.all { it.jobId == jobId }) {
                            // If all applications are for the same job, reload only that job's applications
                            launch { loadApplicationsForJob(jobId) }
                        } else {
                            // Otherwise reload all recent applications
                            launch { loadRecentApplications(_recentApplications.value.size) }
                        }
                    }
                }
            }

            success
        } catch (e: Exception) {
            false
        }
    }
}