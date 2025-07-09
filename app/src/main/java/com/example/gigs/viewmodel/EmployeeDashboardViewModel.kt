package com.example.gigs.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gigs.data.model.Activity
import com.example.gigs.data.model.Application
import com.example.gigs.data.model.ApplicationStatus
import com.example.gigs.data.model.ApplicationWithJob
import com.example.gigs.data.model.CategoryStat
import com.example.gigs.data.model.EmployeeDashboardData
import com.example.gigs.data.model.EmployerDashboardData
import com.example.gigs.data.model.EmployerProfile
import com.example.gigs.data.model.Job
import com.example.gigs.data.model.JobStatus
import com.example.gigs.data.model.LocationStat
import com.example.gigs.data.model.WorkSession
import com.example.gigs.data.remote.SupabaseClient
import com.example.gigs.data.repository.ApplicationRepository
import com.example.gigs.data.repository.AuthRepository
import com.example.gigs.data.repository.DashboardRepository
import com.example.gigs.data.repository.JobRepository
import com.example.gigs.data.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.postgrest.query.Order
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

// Add this to your ViewModel or call it directly to diagnose the issue

class WorkSessionDiagnostic @Inject constructor(
    private val applicationRepository: ApplicationRepository,
    private val authRepository: AuthRepository,
    private val supabaseClient: SupabaseClient
) {

    suspend fun diagnoseWorkSession(applicationId: String): String {
        val userId = authRepository.getCurrentUserId()
        val sb = StringBuilder()

        sb.appendLine("=== WORK SESSION DIAGNOSTIC ===")
        sb.appendLine("Application ID: $applicationId")
        sb.appendLine("User ID: $userId")
        sb.appendLine()

        try {
            // 1. Check application status
            val application = supabaseClient
                .table("applications")
                .select {
                    filter { eq("id", applicationId) }
                }
                .decodeSingleOrNull<Application>()

            if (application != null) {
                sb.appendLine("✅ Application found:")
                sb.appendLine("   Status: '${application.status}'")
                sb.appendLine("   Employee ID: '${application.employeeId}'")
                sb.appendLine("   Job ID: '${application.jobId}'")
                sb.appendLine("   Updated: ${application.updatedAt}")
            } else {
                sb.appendLine("❌ Application not found!")
                return sb.toString()
            }

            // 2. Check all work sessions for this application
            val allWorkSessions = supabaseClient
                .table("work_sessions")
                .select {
                    filter { eq("application_id", applicationId) }
                    order("created_at", Order.DESCENDING)
                }
                .decodeList<WorkSession>()

            sb.appendLine()
            sb.appendLine("Work Sessions (${allWorkSessions.size} found):")

            if (allWorkSessions.isEmpty()) {
                sb.appendLine("❌ No work sessions found!")
            } else {
                allWorkSessions.forEachIndexed { index, session ->
                    sb.appendLine("${index + 1}. Session ID: ${session.id}")
                    sb.appendLine("   Status: '${session.status}'")
                    sb.appendLine("   Employee ID: '${session.employeeId}'")
                    sb.appendLine("   Employer ID: '${session.employerId}'")
                    sb.appendLine("   OTP: '${session.otp}'")
                    sb.appendLine("   Work Start: ${session.workStartTime}")
                    sb.appendLine("   Work End: ${session.workEndTime}")
                    sb.appendLine("   Created: ${session.createdAt}")
                    sb.appendLine()
                }
            }

            // 3. Check for user-specific work sessions
            val userWorkSessions = allWorkSessions.filter { it.employeeId == userId }
            sb.appendLine("User's Work Sessions (${userWorkSessions.size} found):")

            if (userWorkSessions.isEmpty()) {
                sb.appendLine("❌ No work sessions found for current user!")
                sb.appendLine("Expected Employee ID: '$userId'")
                sb.appendLine("Actual Employee IDs in sessions: ${allWorkSessions.map { "'${it.employeeId}'" }}")
            }

            // 4. Check for WORK_IN_PROGRESS sessions specifically
            val activeWorkSessions = userWorkSessions.filter { it.status == "WORK_IN_PROGRESS" }
            sb.appendLine()
            sb.appendLine("Active Work Sessions (WORK_IN_PROGRESS): ${activeWorkSessions.size}")

            if (activeWorkSessions.isEmpty()) {
                sb.appendLine("❌ No WORK_IN_PROGRESS sessions found!")
                sb.appendLine("Available statuses: ${userWorkSessions.map { "'${it.status}'" }}")
            }

            // 5. Recommendations
            sb.appendLine()
            sb.appendLine("=== RECOMMENDATIONS ===")

            when {
                application.status != "WORK_IN_PROGRESS" -> {
                    sb.appendLine("❌ Application status is '${application.status}', should be 'WORK_IN_PROGRESS'")
                    sb.appendLine("   → Check if work was actually started with OTP")
                }
                userWorkSessions.isEmpty() -> {
                    sb.appendLine("❌ No work sessions for current user")
                    sb.appendLine("   → User ID mismatch or work session not created")
                }
                activeWorkSessions.isEmpty() -> {
                    val lastSession = userWorkSessions.maxByOrNull { it.createdAt ?: "" }
                    sb.appendLine("❌ No WORK_IN_PROGRESS sessions")
                    sb.appendLine("   → Last session status: '${lastSession?.status}'")
                    when (lastSession?.status) {
                        "OTP_GENERATED" -> sb.appendLine("   → Work not started yet, need to enter OTP")
                        "COMPLETION_PENDING" -> sb.appendLine("   → Work completion already initiated")
                        "WORK_COMPLETED" -> sb.appendLine("   → Work already completed")
                        else -> sb.appendLine("   → Unknown session state")
                    }
                }
                else -> {
                    sb.appendLine("✅ Everything looks correct")
                    sb.appendLine("   → Active work session found")
                }
            }

        } catch (e: Exception) {
            sb.appendLine("❌ Error during diagnosis: ${e.message}")
        }

        return sb.toString()
    }

    // Quick fix method
    suspend fun quickFixWorkSession(applicationId: String): Result<String> {
        return try {
            val diagnostic = diagnoseWorkSession(applicationId)
            Log.d("WorkSessionDiagnostic", diagnostic)

            val userId = authRepository.getCurrentUserId()
                ?: return Result.failure(Exception("Not authenticated"))

            // Try to find the most recent work session
            val workSessions = supabaseClient
                .table("work_sessions")
                .select {
                    filter {
                        eq("application_id", applicationId)
                        eq("employee_id", userId)
                    }
                    order("created_at", Order.DESCENDING)
                    limit(1)
                }
                .decodeList<WorkSession>()

            if (workSessions.isEmpty()) {
                return Result.failure(Exception("No work sessions found. Contact employer to generate OTP."))
            }

            val session = workSessions.first()

            when (session.status) {
                "OTP_GENERATED" -> {
                    Result.failure(Exception("Work not started. Please enter the OTP provided by your employer."))
                }
                "WORK_STARTED" -> {
                    // Fix the status to WORK_IN_PROGRESS
                    supabaseClient.table("work_sessions")
                        .update(mapOf("status" to "WORK_IN_PROGRESS")) {
                            filter { eq("id", session.id) }
                        }

                    supabaseClient.table("applications")
                        .update(mapOf("status" to "WORK_IN_PROGRESS")) {
                            filter { eq("id", applicationId) }
                        }

                    Result.success("Fixed: Updated status to WORK_IN_PROGRESS")
                }
                "COMPLETION_PENDING" -> {
                    Result.failure(Exception("Work completion already initiated. Share completion code with employer."))
                }
                "WORK_COMPLETED" -> {
                    Result.failure(Exception("Work already completed."))
                }
                else -> {
                    Result.failure(Exception("Unknown session status: ${session.status}"))
                }
            }

        } catch (e: Exception) {
            Result.failure(Exception("Quick fix failed: ${e.message}"))
        }
    }
}
