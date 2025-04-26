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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
    private val jobRepository: JobRepository
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

    fun loadDashboardData() {
        viewModelScope.launch {
            _isLoading.value = true

            // Load main dashboard data
            dashboardRepository.getEmployerDashboardData().collect { result ->
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

            _isLoading.value = false
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
                _isLoading.value = false
                if (result.isSuccess) {
                    _recentApplications.value = result.getOrNull() ?: emptyList()
                }
            }
        }
    }

    // Update application status
    fun updateApplicationStatus(applicationId: String, newStatus: String) {
        viewModelScope.launch {
            applicationRepository.updateApplicationStatus(applicationId, newStatus).collect { result ->
                if (result.isSuccess) {
                    // Refresh applications
                    loadRecentApplications()
                }
            }
        }
    }
}