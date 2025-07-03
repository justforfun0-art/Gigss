package com.example.gigs.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gigs.data.model.Activity
import com.example.gigs.data.model.ApplicationStatus
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