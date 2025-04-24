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