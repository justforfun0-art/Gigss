package com.example.gigs.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gigs.data.model.Activity
import com.example.gigs.data.model.ApplicationStatus
import com.example.gigs.data.model.ApplicationWithJob
import com.example.gigs.data.model.CategoryStat
import com.example.gigs.data.model.EmployerDashboardData
import com.example.gigs.data.model.EmployerProfile
import com.example.gigs.data.model.Job
import com.example.gigs.data.model.JobStatus
import com.example.gigs.data.model.LocationStat
import com.example.gigs.data.model.WorkSession
import com.example.gigs.data.repository.ApplicationRepository
import com.example.gigs.data.repository.DashboardRepository
import com.example.gigs.data.repository.JobRepository
import com.example.gigs.data.repository.ProfileRepository
import com.example.gigs.data.repository.AuthRepository
import com.example.gigs.data.remote.SupabaseClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

@HiltViewModel
class EmployerDashboardViewModel @Inject constructor(
    private val dashboardRepository: DashboardRepository,
    private val jobRepository: JobRepository,
    private val applicationRepository: ApplicationRepository,
    private val profileRepository: ProfileRepository,
    private val authRepository: AuthRepository
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

    private val _navigationEvent = MutableSharedFlow<DashboardNavigationEvent>()
    val navigationEvent: SharedFlow<DashboardNavigationEvent> = _navigationEvent.asSharedFlow()

    /**
     * Handle dashboard card clicks
     */
    fun onDashboardCardClicked(cardType: DashboardCardType) {
        viewModelScope.launch {
            when (cardType) {
                DashboardCardType.TOTAL_JOBS -> {
                    Log.d(TAG, "📊 Total Jobs card clicked - navigating to all jobs")
                    _navigationEvent.emit(
                        DashboardNavigationEvent.NavigateToMyJobs(
                            filter = JobsFilter.ALL_JOBS,
                            title = "All Jobs (${_totalJobs.value})"
                        )
                    )
                }
                DashboardCardType.ACTIVE_JOBS -> {
                    Log.d(TAG, "📊 Active Jobs card clicked - navigating to active jobs only")
                    _navigationEvent.emit(
                        DashboardNavigationEvent.NavigateToMyJobs(
                            filter = JobsFilter.ACTIVE_ONLY,
                            title = "Active Jobs (${_activeJobs.value})"
                        )
                    )
                }
                DashboardCardType.APPLICATIONS -> {
                    Log.d(TAG, "📊 Applications card clicked - navigating to applications")
                    _navigationEvent.emit(
                        DashboardNavigationEvent.NavigateToApplications(
                            title = "All Applications (${_totalApplications.value})"
                        )
                    )
                }
                DashboardCardType.RATING -> {
                    Log.d(TAG, "📊 Rating card clicked - could show reviews/feedback")
                    // Could navigate to reviews/ratings screen
                }
            }
        }
    }

    /**
     * Load dashboard data safely
     */
    private suspend fun loadDashboardDataSafely() {
        try {
            Log.d(TAG, "🔍 Loading dashboard data safely...")

            withTimeoutOrNull(8000) {
                try {
                    dashboardRepository.getEmployerDashboardData()
                        .catch { e ->
                            Log.e(TAG, "❌ Dashboard data flow error: ${e.message}")
                            if (e !is kotlinx.coroutines.CancellationException) {
                                Log.e(TAG, "Non-abort exception in dashboard flow", e)
                            }
                        }
                        .collect { result ->
                            if (result.isSuccess) {
                                val dashboardData = result.getOrNull()
                                _dashboardData.value = dashboardData

                                if (dashboardData != null) {
                                    _averageRating.value = dashboardData.averageRating
                                    Log.d(TAG, "✅ Loaded dashboard data with rating: ${dashboardData.averageRating}")
                                }
                            } else {
                                Log.e(TAG, "❌ Dashboard data result failed: ${result.exceptionOrNull()?.message}")
                            }
                        }

                } catch (e: Exception) {
                    if (e !is kotlinx.coroutines.CancellationException) {
                        Log.e(TAG, "❌ Exception loading dashboard data: ${e.message}")
                    }
                }
            } ?: Log.w(TAG, "⚠️ Dashboard data loading timed out")

        } catch (e: Exception) {
            if (e !is kotlinx.coroutines.CancellationException) {
                Log.e(TAG, "❌ Error in loadDashboardDataSafely: ${e.message}")
            }
        }
    }

    /**
     * Main function to load all dashboard data
     */
    fun loadDashboardData() {
        viewModelScope.launch {
            _isLoading.value = true
            Log.d(TAG, "🚀 Starting dashboard data load...")

            try {
                supervisorScope {
                    // Load all data concurrently
                    val profileDeferred = async { loadEmployerProfileSafely() }
                    val jobStatsDeferred = async { calculateJobStatsFixed() }
                    val activitiesDeferred = async { loadActivitiesSafely() }
                    val recentJobsDeferred = async { loadRecentJobsSafely() }
                    val locationStatsDeferred = async { loadLocationStatsSafely() }
                    val categoryStatsDeferred = async { loadCategoryStatsSafely() }
                    val dashboardDataDeferred = async { loadDashboardDataSafely() }

                    // Wait for all operations to complete
                    awaitAll(
                        profileDeferred,
                        jobStatsDeferred,
                        activitiesDeferred,
                        recentJobsDeferred,
                        locationStatsDeferred,
                        categoryStatsDeferred,
                        dashboardDataDeferred
                    )
                }

                Log.d(TAG, "✅ Dashboard data load completed successfully")
                Log.d(TAG, "📊 Final stats - Jobs: ${_totalJobs.value}, Active: ${_activeJobs.value}, Applications: ${_totalApplications.value}")

            } catch (e: Exception) {
                Log.e(TAG, "❌ Error in loadDashboardData: ${e.message}", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Load activities safely
     */
    private suspend fun loadActivitiesSafely() {
        try {
            withTimeoutOrNull(5000) {
                val result = dashboardRepository.getRecentActivities(5).first()
                if (result.isSuccess) {
                    _recentActivities.value = result.getOrNull() ?: emptyList()
                    Log.d(TAG, "✅ Loaded ${_recentActivities.value.size} activities")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading activities safely: ${e.message}")
        }
    }

    /**
     * Load recent jobs safely
     */
    private suspend fun loadRecentJobsSafely() {
        try {
            withTimeoutOrNull(5000) {
                val result = jobRepository.getMyJobs(10).first()
                if (result.isSuccess) {
                    _recentJobs.value = result.getOrNull() ?: emptyList()
                    Log.d(TAG, "✅ Loaded ${_recentJobs.value.size} recent jobs")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading recent jobs safely: ${e.message}")
        }
    }

    /**
     * Load location stats safely
     */
    private suspend fun loadLocationStatsSafely() {
        try {
            withTimeoutOrNull(5000) {
                val result = dashboardRepository.getApplicationsByLocation().first()
                if (result.isSuccess) {
                    _locationStats.value = result.getOrNull() ?: emptyList()
                    Log.d(TAG, "✅ Loaded ${_locationStats.value.size} location stats")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading location stats safely: ${e.message}")
        }
    }

    /**
     * Load category stats safely
     */
    private suspend fun loadCategoryStatsSafely() {
        try {
            withTimeoutOrNull(5000) {
                val result = dashboardRepository.getApplicationsByCategory().first()
                if (result.isSuccess) {
                    _categoryStats.value = result.getOrNull() ?: emptyList()
                    Log.d(TAG, "✅ Loaded ${_categoryStats.value.size} category stats")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading category stats safely: ${e.message}")
        }
    }

    /**
     * Calculate job statistics with better debugging and error handling
     */
    private suspend fun calculateJobStatsFixed() {
        try {
            Log.d(TAG, "🔍 Starting job stats calculation...")

            withTimeoutOrNull(10000) {
                try {
                    val jobs = jobRepository.getMyJobsDirect(100)
                    Log.d(TAG, "📊 Retrieved ${jobs.size} total jobs for employer")

                    // Update job counts
                    _totalJobs.value = jobs.size
                    Log.d(TAG, "📊 Total jobs: ${_totalJobs.value}")

                    // Better active job detection with debugging
                    val activeJobsList = jobs.filter { job ->
                        val isActive = when {
                            job.isActive == true -> {
                                Log.d(TAG, "🟢 Job ${job.id.take(8)} is active (isActive=true)")
                                true
                            }
                            job.status == JobStatus.APPROVED -> {
                                Log.d(TAG, "🟢 Job ${job.id.take(8)} is active (status=APPROVED)")
                                true
                            }
                            job.status?.name?.equals("APPROVED", true) == true -> {
                                Log.d(TAG, "🟢 Job ${job.id.take(8)} is active (status name=APPROVED)")
                                true
                            }
                            else -> {
                                Log.d(TAG, "🔴 Job ${job.id.take(8)} is inactive (isActive=${job.isActive}, status=${job.status})")
                                false
                            }
                        }
                        isActive
                    }

                    _activeJobs.value = activeJobsList.size
                    Log.d(TAG, "📊 Active jobs: ${_activeJobs.value}")

                    // Calculate applications with proper error handling
                    if (jobs.isNotEmpty()) {
                        var totalApplicationCount = 0

                        // Process jobs in smaller batches to avoid timeouts
                        jobs.chunked(10).forEach { jobBatch ->
                            jobBatch.forEach { job ->
                                try {
                                    withTimeoutOrNull(2000) {
                                        applicationRepository.getApplicationsForJob(job.id)
                                            .catch { e ->
                                                Log.w(TAG, "⚠️ Error getting applications for job ${job.id.take(8)}: ${e.message}")
                                            }
                                            .collect { appResult ->
                                                if (appResult.isSuccess) {
                                                    val applicationCount = appResult.getOrNull()?.size ?: 0
                                                    totalApplicationCount += applicationCount
                                                    if (applicationCount > 0) {
                                                        Log.d(TAG, "📊 Job ${job.id.take(8)} has $applicationCount applications")
                                                    }
                                                }
                                            }
                                    }
                                } catch (e: Exception) {
                                    Log.w(TAG, "⚠️ Error counting applications for job ${job.id.take(8)}: ${e.message}")
                                }
                            }
                        }

                        _totalApplications.value = totalApplicationCount
                        Log.d(TAG, "📊 Total applications: ${_totalApplications.value}")
                    } else {
                        _totalApplications.value = 0
                        Log.d(TAG, "📊 No jobs found, setting applications to 0")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error in job stats calculation: ${e.message}")
                }
            }

            Log.d(TAG, "✅ Job stats calculation completed")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error calculating job stats: ${e.message}", e)
            // Set default values on error
            _totalJobs.value = 0
            _activeJobs.value = 0
            _totalApplications.value = 0
        }
    }

    /**
     * Load employer profile with better error handling
     */
    private suspend fun loadEmployerProfileSafely() {
        _isProfileLoading.value = true
        Log.d(TAG, "🔍 Loading employer profile...")

        try {
            val userId = authRepository.getCurrentUserId()
            Log.d(TAG, "👤 Current user ID: ${userId?.take(8)}...")

            if (userId != null) {
                withTimeoutOrNull(5000) {
                    getEmployerProfile(userId)
                        .catch { e ->
                            Log.e(TAG, "❌ Error loading employer profile: ${e.message}", e)
                        }
                        .collect { result ->
                            if (result.isSuccess) {
                                _employerProfile.value = result.getOrNull()
                                Log.d(TAG, "✅ Loaded employer profile: ${_employerProfile.value?.companyName}")
                            } else {
                                Log.e(TAG, "❌ Failed to load employer profile: ${result.exceptionOrNull()?.message}")
                            }
                        }
                }
            } else {
                Log.e(TAG, "❌ Error loading employer profile: User ID is null")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception in loadEmployerProfile: ${e.message}", e)
        } finally {
            _isProfileLoading.value = false
        }
    }

    /**
     * Get employer profile by user ID with proper error handling
     */
    private fun getEmployerProfile(userId: String) = flow {
        try {
            Log.d(TAG, "🔍 Querying employer profile for user: ${userId.take(8)}...")

            val profile = profileRepository.supabaseClient
                .table("employer_profiles")
                .select {
                    filter {
                        eq("user_id", userId)
                    }
                }
                .decodeSingleOrNull<EmployerProfile>()

            if (profile != null) {
                Log.d(TAG, "✅ Found employer profile: ${profile.companyName}")
                emit(Result.success(profile))
            } else {
                Log.w(TAG, "⚠️ No employer profile found for user ${userId.take(8)}")
                emit(Result.failure(Exception("Employer profile not found")))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error querying employer profile: ${e.message}", e)
            emit(Result.failure(e))
        }
    }

    /**
     * Force refresh all dashboard data with debugging
     */
    fun refreshDashboard() {
        viewModelScope.launch {
            Log.d(TAG, "🔄 Refreshing dashboard...")
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

    /**
     * Debug method to test job loading directly
     */
    fun debugJobStats() {
        viewModelScope.launch {
            Log.d(TAG, "🐛 DEBUG: Testing job stats calculation...")

            try {
                val userId = authRepository.getCurrentUserId()
                Log.d(TAG, "🐛 Current user ID: ${userId?.take(8)}...")

                if (userId != null) {
                    // Test direct repository call
                    val directJobs = jobRepository.getMyJobsDirect(50)
                    Log.d(TAG, "🐛 Direct repository call returned ${directJobs.size} jobs")

                    directJobs.forEachIndexed { index, job ->
                        Log.d(TAG, "🐛 Job $index: ${job.id.take(8)} - ${job.title} - Active: ${job.isActive} - Status: ${job.status}")
                    }

                    // Test flow-based call
                    jobRepository.getMyJobs(50)
                        .catch { e ->
                            Log.d(TAG, "🐛 Flow-based call failed: ${e.message}")
                        }
                        .collect { flowResult ->
                            if (flowResult.isSuccess) {
                                val flowJobs = flowResult.getOrNull() ?: emptyList()
                                Log.d(TAG, "🐛 Flow-based call returned ${flowJobs.size} jobs")
                            } else {
                                Log.d(TAG, "🐛 Flow-based call failed: ${flowResult.exceptionOrNull()?.message}")
                            }
                        }
                }
            } catch (e: Exception) {
                Log.e(TAG, "🐛 Debug failed: ${e.message}", e)
            }
        }
    }

    /**
     * Debug dashboard state
     */
    fun debugDashboardState() {
        viewModelScope.launch {
            Log.d("DEBUG_DASHBOARD", "=== DASHBOARD DEBUG STATE ===")
            Log.d("DEBUG_DASHBOARD", "Total Jobs: ${_totalJobs.value}")
            Log.d("DEBUG_DASHBOARD", "Active Jobs: ${_activeJobs.value}")
            Log.d("DEBUG_DASHBOARD", "Total Applications: ${_totalApplications.value}")
            Log.d("DEBUG_DASHBOARD", "Is Loading: ${_isLoading.value}")
            Log.d("DEBUG_DASHBOARD", "Employer Profile: ${_employerProfile.value?.companyName}")

            // Test direct repository call
            try {
                val userId = authRepository.getCurrentUserId()
                Log.d("DEBUG_DASHBOARD", "Current User ID: ${userId?.take(8)}...")

                if (userId != null) {
                    val directJobs = jobRepository.getMyJobsDirect(10)
                    Log.d("DEBUG_DASHBOARD", "Direct jobs call returned: ${directJobs.size} jobs")

                    directJobs.forEach { job ->
                        Log.d("DEBUG_DASHBOARD", "Job: ${job.title} - Active: ${job.isActive} - Status: ${job.status}")
                    }
                }
            } catch (e: Exception) {
                Log.e("DEBUG_DASHBOARD", "Debug test failed: ${e.message}")
            }
            Log.d("DEBUG_DASHBOARD", "==========================")
        }
    }
}

@HiltViewModel
class EmployerApplicationsViewModel @Inject constructor(
    internal val applicationRepository: ApplicationRepository,
    private val jobRepository: JobRepository,
    private val authRepository: AuthRepository,
    private val supabaseClient: SupabaseClient
) : ViewModel() {

    private val TAG = "EmployerAppsVM"

    // 🚀 Application lists
    private val _recentApplications = MutableStateFlow<List<ApplicationWithJob>>(emptyList())
    val recentApplications: StateFlow<List<ApplicationWithJob>> = _recentApplications

    // 🚀 Loading states
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    // 🚀 Application counts for dashboard
    private val _totalApplicationsCount = MutableStateFlow(0)
    val totalApplicationsCount: StateFlow<Int> = _totalApplicationsCount

    private val _selectedApplicationsCount = MutableStateFlow(0)
    val selectedApplicationsCount: StateFlow<Int> = _selectedApplicationsCount

    private val _completionPendingCount = MutableStateFlow(0)
    val completionPendingCount: StateFlow<Int> = _completionPendingCount

    // 🚀 Individual OTP management per application
    private val _applicationOtps = MutableStateFlow<Map<String, String>>(emptyMap())
    val applicationOtps: StateFlow<Map<String, String>> = _applicationOtps

    private val _applicationWorkSessions = MutableStateFlow<Map<String, WorkSession>>(emptyMap())
    val applicationWorkSessions: StateFlow<Map<String, WorkSession>> = _applicationWorkSessions

    private val _applicationOtpErrors = MutableStateFlow<Map<String, String>>(emptyMap())
    val applicationOtpErrors: StateFlow<Map<String, String>> = _applicationOtpErrors

    private val _applicationLoadingStates = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val applicationLoadingStates: StateFlow<Map<String, Boolean>> = _applicationLoadingStates

    // 🚀 OTP Dialog states
    private val _applicationOtpDialogStates = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val applicationOtpDialogStates: StateFlow<Map<String, Boolean>> = _applicationOtpDialogStates

    // 🚀 Completion OTP management
    private val _completionOtpDialogStates = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val completionOtpDialogStates: StateFlow<Map<String, Boolean>> = _completionOtpDialogStates

    private val _completionOtpErrors = MutableStateFlow<Map<String, String>>(emptyMap())
    val completionOtpErrors: StateFlow<Map<String, String>> = _completionOtpErrors

    private val _completionLoadingStates = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val completionLoadingStates: StateFlow<Map<String, Boolean>> = _completionLoadingStates

    private val _completionResults = MutableStateFlow<Map<String, String>>(emptyMap())
    val completionResults: StateFlow<Map<String, String>> = _completionResults

    // 🚀 DEPRECATED: Keep for backward compatibility but don't use for auto-popup
    private val _generatedOtp = MutableStateFlow<String?>(null)
    val generatedOtp: StateFlow<String?> = _generatedOtp

    private val _workSession = MutableStateFlow<WorkSession?>(null)
    val workSession: StateFlow<WorkSession?> = _workSession

    private val _otpError = MutableStateFlow<String?>(null)
    val otpError: StateFlow<String?> = _otpError


    private val _rejectedApplicationsCount = MutableStateFlow(0)
    val rejectedApplicationsCount: StateFlow<Int> = _rejectedApplicationsCount

    private val _appliedApplicationsCount = MutableStateFlow(0)
    val appliedApplicationsCount: StateFlow<Int> = _appliedApplicationsCount




    /**
     * 🚀 ENHANCED: Load applications with completion awareness
     */
    fun loadApplicationsWithCompletion(limit: Int = 20, statusFilter: String? = null) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                val userId = authRepository.getCurrentUserId() ?: return@launch
                Log.d(TAG, "🔍 Loading applications with completion support, filter: $statusFilter")

                val applications = applicationRepository.getApplicationsForEmployerDashboard(
                    employerId = userId,
                    limit = limit,
                    statusFilter = statusFilter
                )

                _recentApplications.value = applications
                Log.d(TAG, "✅ Loaded ${applications.size} applications")

                // Load work sessions for all applications
                loadWorkSessionsForAllApplications(applications)

                // Update counts
                updateApplicationCounts(applications)

                // Debug log
                val statusBreakdown = applications.groupBy { it.status }.mapValues { it.value.size }
                Log.d(TAG, "📊 Application status breakdown: $statusBreakdown")

            } catch (e: Exception) {
                Log.e(TAG, "❌ Error loading applications: ${e.message}")
                _error.value = "Failed to load applications: ${e.message}"
                _recentApplications.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }



    /**
     * Accept application and generate OTP
     */
    fun acceptApplicationWithOtp(applicationId: String) {
        viewModelScope.launch {
            Log.d(TAG, "🚀 Starting OTP generation for application: $applicationId")

            // Set loading state for this specific application
            setApplicationLoadingState(applicationId, true)
            clearApplicationError(applicationId)

            try {
                val result = withTimeoutOrNull(10000L) {
                    applicationRepository.acceptApplicationAndGenerateOtp(applicationId)
                } ?: run {
                    Log.e(TAG, "❌ OTP generation timed out for application: $applicationId")
                    setApplicationError(applicationId, "Request timed out. Please try again.")
                    return@launch
                }

                if (result.isSuccess) {
                    val otp = result.getOrNull() ?: ""
                    Log.d(TAG, "✅ Generated OTP: $otp for application $applicationId")
                    Log.d(TAG, "🔐 STORING OTP in ViewModel state...")

                    // 🚀 CRITICAL FIX: Store OTP immediately
                    setApplicationOtp(applicationId, otp)

                    // Set legacy state for backward compatibility
                    _generatedOtp.value = otp

                    // 🚀 CRITICAL FIX: Load work session AFTER storing OTP
                    loadWorkSessionForApplication(applicationId)

                    // 🚀 DEBUG: Log the current state
                    Log.d(TAG, "🔍 Current OTP map: ${_applicationOtps.value}")
                    Log.d(TAG, "🔍 OTP for $applicationId: ${_applicationOtps.value[applicationId]}")

                    // Refresh applications list
                    refreshApplications()

                } else {
                    val error = result.exceptionOrNull()?.message ?: "Failed to generate OTP"
                    Log.e(TAG, "❌ Failed to generate OTP: $error")
                    setApplicationError(applicationId, error)
                }
            } catch (e: Exception) {
                val error = e.message ?: "Unknown error occurred"
                Log.e(TAG, "❌ Exception generating OTP: $error", e)
                setApplicationError(applicationId, error)
            } finally {
                setApplicationLoadingState(applicationId, false)
            }
        }
    }


    /**
     * Load only selected applications for the home screen
     */
    fun loadSelectedApplications(limit: Int = 10) {
        viewModelScope.launch {
            _isLoading.value = true

            try {
                val userId = authRepository.getCurrentUserId() ?: return@launch
                Log.d(TAG, "🔍 Loading SELECTED applications for dashboard")

                val selectedApplications = applicationRepository.getSelectedApplicationsForEmployer(
                    employerId = userId,
                    limit = limit
                )

                _recentApplications.value = selectedApplications
                Log.d(TAG, "✅ Loaded ${selectedApplications.size} SELECTED applications")

                // Load work sessions
                loadWorkSessionsForAllApplications(selectedApplications)

                // Update counts
                updateApplicationCounts(selectedApplications)

            } catch (e: Exception) {
                Log.e(TAG, "❌ Error loading selected applications: ${e.message}")
                _recentApplications.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }


    /**
     * Update application status with proper validation for employers
     */
    suspend fun updateEmployerApplicationStatusSimplified(
        applicationId: String,
        newStatus: String
    ): kotlinx.coroutines.flow.Flow<Result<Boolean>> = flow {
        try {
            val success = updateEmployerApplicationStatusDirectSimplified(applicationId, newStatus)
            emit(Result.success(success))
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }

    /**
     * Simplified direct method for employer status updates (SELECTED/REJECTED only)
     */
    private suspend fun updateEmployerApplicationStatusDirectSimplified(
        applicationId: String,
        newStatus: String
    ): Boolean {
        return try {
            val userId = authRepository.getCurrentUserId() ?: throw Exception("User not authenticated")

            // Validate: Only allow SELECTED or REJECTED for employers
            val validatedStatus = when (newStatus.uppercase().trim()) {
                "SELECTED", "HIRED", "ACCEPTED" -> "SELECTED"
                "REJECTED", "DECLINED" -> "REJECTED"
                else -> {
                    Log.w(TAG, "⚠️ Invalid employer status '$newStatus', defaulting to REJECTED")
                    "REJECTED"
                }
            }

            Log.d(TAG, "🔄 EMPLOYER: Updating application $applicationId: $newStatus -> $validatedStatus")

            // Get the application
            val application = supabaseClient
                .table("applications")
                .select {
                    filter { eq("id", applicationId) }
                }
                .decodeSingleOrNull<com.example.gigs.data.model.Application>() ?: throw Exception("Application not found")

            // Get job to verify ownership
            val job = supabaseClient
                .table("jobs")
                .select {
                    filter { eq("id", application.jobId) }
                }
                .decodeSingleOrNull<Job>()

            if (job?.employerId != userId) {
                throw Exception("You can only update applications for your own jobs")
            }

            val timestamp = java.time.Instant.now().toString()

            // Update application status
            supabaseClient
                .table("applications")
                .update(mapOf(
                    "status" to validatedStatus,
                    "updated_at" to timestamp
                )) {
                    filter { eq("id", applicationId) }
                }

            Log.d(TAG, "✅ EMPLOYER: Successfully updated application to $validatedStatus")
            true

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error updating employer application status: ${e.message}")
            false
        }
    }

    /**
     * Get work sessions with OTP for selected applications
     */
    suspend fun getWorkSessionsForSelectedApplications(employerId: String): List<WorkSession> {
        return try {
            Log.d(TAG, "🔍 Getting work sessions for employer's selected applications")

            // Get all jobs for this employer
            val employerJobs = supabaseClient
                .table("jobs")
                .select {
                    filter { eq("employer_id", employerId) }
                }
                .decodeList<Job>()

            if (employerJobs.isEmpty()) return emptyList()

            val jobIds = employerJobs.map { it.id }
            val inClause = jobIds.joinToString(",") { "\"$it\"" }

            // Get work sessions for jobs owned by this employer
            val workSessions = supabaseClient
                .table("work_sessions")
                .select {
                    filter {
                        io.github.jan.supabase.postgrest.query.filter.FilterOperator.IN
                        eq("employer_id", employerId)
                    }
                    order("created_at", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                }
                .decodeList<WorkSession>()

            Log.d(TAG, "✅ Found ${workSessions.size} work sessions for employer")
            workSessions

        } catch (e: Exception) {
            Log.e(TAG, "Error getting work sessions for selected applications: ${e.message}")
            emptyList()
        }
    }

    /**
     * Get application statistics for employer
     */
    suspend fun getApplicationStatisticsForEmployer(employerId: String): Map<String, Int> {
        return try {
            Log.d(TAG, "🔍 Getting application statistics for employer")

            // Get all jobs for this employer
            val employerJobs = supabaseClient
                .table("jobs")
                .select {
                    filter { eq("employer_id", employerId) }
                }
                .decodeList<Job>()

            if (employerJobs.isEmpty()) return emptyMap()

            val jobIds = employerJobs.map { it.id }
            val inClause = jobIds.joinToString(",") { "\"$it\"" }

            // Get all applications for employer's jobs (excluding NOT_INTERESTED)
            val applications = supabaseClient
                .table("applications")
                .select {
                    filter {
                        neq("status", "NOT_INTERESTED")
                    }
                }
                .decodeList<com.example.gigs.data.model.Application>()

            // Group by status and count
            val statistics = applications
                .groupBy { it.status }
                .mapValues { it.value.size }

            Log.d(TAG, "✅ Application statistics: $statistics")
            statistics

        } catch (e: Exception) {
            Log.e(TAG, "Error getting application statistics: ${e.message}")
            emptyMap()
        }
    }

    /**
     * Update application status simplified
     */
    suspend fun updateApplicationStatusSimplified(applicationId: String, newStatus: String): Boolean {
        return try {
            // Validate status - only SELECTED or REJECTED allowed
            val validStatus = when (newStatus.uppercase()) {
                "SELECTED", "HIRED", "ACCEPTED" -> "SELECTED"
                "REJECTED", "DECLINED" -> "REJECTED"
                else -> {
                    Log.w(TAG, "⚠️ Invalid status '$newStatus', defaulting to REJECTED")
                    "REJECTED"
                }
            }

            Log.d(TAG, "🔄 Updating application $applicationId to $validStatus")

            var success = false

            // Use the existing updateApplicationStatus method from ApplicationRepository
            applicationRepository.updateApplicationStatus(applicationId, validStatus)
                .catch { e ->
                    Log.e(TAG, "Failed to update application status: ${e.message}")
                }
                .collect { result: Result<Boolean> ->
                    success = result.isSuccess
                }

            if (success) {
                // Refresh the application list
                coroutineScope {
                    launch {
                        // Determine refresh strategy based on current list
                        val currentSize = _recentApplications.value.size

                        // If we're showing selected applications specifically, refresh selected
                        val hasOnlySelected = _recentApplications.value.all { it.status == ApplicationStatus.SELECTED }

                        if (hasOnlySelected) {
                            loadSelectedApplications(currentSize.coerceAtLeast(5))
                        } else {
                            loadRecentApplications(currentSize.coerceAtLeast(5))
                        }
                    }
                }
                Log.d(TAG, "✅ Successfully updated application status to $validStatus")
            }

            success
        } catch (e: Exception) {
            Log.e(TAG, "Error updating application status: ${e.message}")
            false
        }
    }

    /**
     * Get application statistics for dashboard
     */
    fun loadApplicationStatistics() {
        viewModelScope.launch {
            try {
                val userId = authRepository.getCurrentUserId() ?: return@launch

                val statistics = getApplicationStatisticsForEmployer(userId)

                // Update individual counters based on statistics
                _totalApplicationsCount.value = statistics.values.sum()
                _selectedApplicationsCount.value = statistics["SELECTED"] ?: 0
                _rejectedApplicationsCount.value = statistics["REJECTED"] ?: 0
                _appliedApplicationsCount.value = statistics["APPLIED"] ?: 0

                Log.d(TAG, "✅ Application statistics loaded: $statistics")

            } catch (e: Exception) {
                Log.e(TAG, "Error loading application statistics: ${e.message}")
            }
        }
    }

    /**
     * Generate OTP for selected application with validation
     */
    fun generateOtpForSelectedApplication(applicationId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _otpError.value = null

            try {
                // First verify the application is SELECTED
                val application = _recentApplications.value.find { it.id == applicationId }

                if (application?.status != ApplicationStatus.SELECTED) {
                    _otpError.value = "Can only generate OTP for selected applications"
                    _isLoading.value = false
                    return@launch
                }

                val result = applicationRepository.acceptApplicationAndGenerateOtp(applicationId)

                if (result.isSuccess) {
                    val otp = result.getOrNull() ?: ""
                    _generatedOtp.value = otp

                    Log.d(TAG, "✅ Generated OTP: $otp for selected application $applicationId")

                    // Refresh applications to update work session data
                    loadSelectedApplications()

                } else {
                    val error = result.exceptionOrNull()?.message ?: "Failed to generate OTP"
                    _otpError.value = error
                    Log.e(TAG, "❌ Failed to generate OTP: $error")
                }
            } catch (e: Exception) {
                _otpError.value = e.message ?: "Unknown error"
                Log.e(TAG, "❌ Exception generating OTP: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Check if application can have OTP generated
     */
    fun canGenerateOtp(application: ApplicationWithJob): Boolean {
        return application.status == ApplicationStatus.SELECTED
    }


    /**
     * Direct fetch of application count - can be used for dashboard stats
     */




    /**
     * 🚀 NEW: Get OTP for specific application
     */
    fun getOtpForApplication(applicationId: String): String? {
        return _applicationOtps.value[applicationId]
    }

    /**
     * 🚀 NEW: Get loading state for specific application
     */
    fun isApplicationLoading(applicationId: String): Boolean {
        return _applicationLoadingStates.value[applicationId] ?: false
    }

    /**
     * 🚀 NEW: Get error for specific application
     */
    fun getErrorForApplication(applicationId: String): String? {
        return _applicationOtpErrors.value[applicationId]
    }


    fun refreshAllApplicationData() {
        viewModelScope.launch {
            Log.d(TAG, "🔄 Refreshing all application data")

            // Clear all OTP data before refreshing
            clearAllOtpData()

            launch { loadRecentApplications(50) }
            launch { loadSelectedApplications(20) }
            launch { loadApplicationStatistics() }

            Log.d(TAG, "✅ All application data refresh initiated")
        }
    }

    suspend fun getTotalApplicationCount(): Int {
        return try {
            var totalCount = 0

            jobRepository.getMyJobs(100)
                .catch { e ->
                    Log.e(TAG, "Error getting jobs for counting: ${e.message}")
                }
                .collect { jobResult ->
                    if (jobResult.isSuccess) {
                        val jobs = jobResult.getOrNull() ?: emptyList()

                        for (job in jobs) {
                            try {
                                withTimeoutOrNull(2000) {
                                    applicationRepository.getApplicationsForJob(job.id)
                                        .catch { e ->
                                            Log.e(TAG, "Error fetching applications count for job ${job.id}: ${e.message}")
                                        }
                                        .collect { appResult ->
                                            if (appResult.isSuccess) {
                                                val applications = appResult.getOrNull() ?: emptyList()
                                                totalCount += applications.size
                                            }
                                        }
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Exception counting applications for job ${job.id}: ${e.message}")
                            }
                        }
                    }
                }

            totalCount
        } catch (e: Exception) {
            Log.e(TAG, "Error in getTotalApplicationCount: ${e.message}")
            0
        }
    }

        /**
     * 🚀 DEBUG: Add this method to check state
     */
    fun debugOtpState(applicationId: String) {
        Log.d(TAG, "🐛 === OTP STATE DEBUG ===")
        Log.d(TAG, "🐛 Application ID: $applicationId")
        Log.d(TAG, "🐛 Full OTP Map: ${_applicationOtps.value}")
        Log.d(TAG, "🐛 OTP for this app: ${_applicationOtps.value[applicationId]}")
        Log.d(TAG, "🐛 Dialog States: ${_applicationOtpDialogStates.value}")
        Log.d(TAG, "🐛 Should show dialog: ${shouldShowOtpDialog(applicationId)}")
        Log.d(TAG, "🐛 === END DEBUG ===")
    }
    /**
     * 🚀 FIXED: Accept application and generate OTP with proper dialog control
     */
    fun acceptApplicationWithOtp(applicationId: String, shouldShowDialog: Boolean = false) {
        viewModelScope.launch {
            Log.d(TAG, "🚀 Accepting application: $applicationId (showDialog: $shouldShowDialog)")

            setApplicationLoadingState(applicationId, true)
            clearApplicationError(applicationId)

            try {
                val result = withTimeoutOrNull(10000L) {
                    applicationRepository.acceptApplicationAndGenerateOtp(applicationId)
                } ?: run {
                    Log.e(TAG, "❌ OTP generation timed out")
                    setApplicationError(applicationId, "Request timed out. Please try again.")
                    return@launch
                }

                if (result.isSuccess) {
                    val otp = result.getOrNull() ?: ""
                    Log.d(TAG, "✅ Generated OTP: $otp")

                    // Store OTP immediately
                    setApplicationOtp(applicationId, otp)

                    // Set dialog state if requested
                    if (shouldShowDialog) {
                        setApplicationOtpDialogState(applicationId, true)
                    }

                    // Load work session
                    loadWorkSessionForApplication(applicationId)

                    // Refresh applications
                    refreshApplications()

                } else {
                    val error = result.exceptionOrNull()?.message ?: "Failed to generate OTP"
                    Log.e(TAG, "❌ OTP generation failed: $error")
                    setApplicationError(applicationId, error)
                }
            } catch (e: Exception) {
                val error = e.message ?: "Unknown error occurred"
                Log.e(TAG, "❌ Exception generating OTP: $error")
                setApplicationError(applicationId, error)
            } finally {
                setApplicationLoadingState(applicationId, false)
            }
        }
    }

    /**
     * 🚀 NEW: Generate OTP silently (for status updates)
     */
    fun generateOtpSilently(applicationId: String) {
        Log.d(TAG, "🚀 Generate OTP silently for: $applicationId")
        acceptApplicationWithOtp(applicationId, shouldShowDialog = false)
    }


    /**
     * 🚀 NEW: Generate OTP and show dialog (for button clicks)
     */
    fun generateOtpAndShowDialog(applicationId: String) {
        Log.d(TAG, "🚀 Generate OTP and show dialog for: $applicationId")
        acceptApplicationWithOtp(applicationId, shouldShowDialog = true)
    }


    /**
     * 🚀 NEW: Show OTP dialog for existing OTP
     */
    fun showOtpDialog(applicationId: String) {
        Log.d(TAG, "🚀 Show OTP dialog for: $applicationId")
        setApplicationOtpDialogState(applicationId, true)
    }

    /**
     * 🚀 NEW: Hide OTP dialog
     */
    fun hideOtpDialog(applicationId: String) {
        Log.d(TAG, "🚀 Hide OTP dialog for: $applicationId")
        setApplicationOtpDialogState(applicationId, false)
    }

    /**
     * 🚀 NEW: Check if OTP dialog should be shown for application
     */
    fun shouldShowOtpDialog(applicationId: String): Boolean {
        return _applicationOtpDialogStates.value[applicationId] ?: false
    }

    /**
     * Get work session for an application with better state management
     */
    fun getWorkSessionForApplication(applicationId: String) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "🔍 Loading work session for application: $applicationId")

                val result = withTimeoutOrNull(5000L) {
                    applicationRepository.getWorkSessionForApplication(applicationId)
                } ?: run {
                    Log.w(TAG, "⚠️ Work session loading timed out for application: $applicationId")
                    return@launch
                }

                if (result.isSuccess) {
                    val workSession = result.getOrNull()
                    if (workSession != null) {
                        Log.d(TAG, "✅ Loaded work session for application $applicationId")
                        Log.d(TAG, "   Status: ${workSession.status}")
                        Log.d(TAG, "   OTP: ${workSession.otp}")

                        // Store work session
                        setApplicationWorkSession(applicationId, workSession)

                        // 🚀 CRITICAL FIX: Store OTP but DON'T auto-show dialog
                        if (workSession.status == "OTP_GENERATED" && workSession.otp.isNotBlank()) {
                            Log.d(TAG, "🔐 STORING OTP from work session: ${workSession.otp}")
                            setApplicationOtp(applicationId, workSession.otp)

                            // Also update legacy state but don't trigger dialog
                            _generatedOtp.value = workSession.otp
                            _workSession.value = workSession
                        }

                    } else {
                        Log.d(TAG, "ℹ️ No work session found for application: $applicationId")
                    }
                } else {
                    Log.w(TAG, "⚠️ Failed to load work session: ${result.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error loading work session for application $applicationId: ${e.message}")
            }
        }
    }


    suspend fun updateApplicationStatus(applicationId: String, newStatus: String): Boolean {
        return try {
            val validatedStatus = when (newStatus.uppercase()) {
                "HIRED", "SELECTED", "ACCEPTED" -> "SELECTED"
                "REJECTED", "DECLINED" -> "REJECTED"
                "SHORTLISTED" -> "SHORTLISTED"
                "INTERVIEW", "INTERVIEW_SCHEDULED" -> "INTERVIEW_SCHEDULED"
                "REVIEWING", "UNDER_REVIEW" -> "REVIEWING"
                else -> {
                    Log.w(TAG, "⚠️ Invalid status '$newStatus' - defaulting to REVIEWING")
                    "REVIEWING"
                }
            }

            Log.d(TAG, "🔄 Updating application $applicationId: $newStatus -> $validatedStatus")

            var success = false
            applicationRepository.updateApplicationStatus(applicationId, validatedStatus)
                .catch { e ->
                    Log.e(TAG, "❌ Failed to update status: ${e.message}")
                }
                .collect { result ->
                    success = result.isSuccess
                }

            if (success) {
                Log.d(TAG, "✅ Successfully updated status to $validatedStatus")
                refreshApplications()

                // If status changed to SELECTED, generate OTP silently
                if (validatedStatus == "SELECTED") {
                    generateOtpSilently(applicationId)
                }
            }

            success
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error updating application status: ${e.message}")
            false
        }
    }



    /**
     * Load applications for a specific job
     */
    fun loadApplicationsForJob(jobId: String) {
        viewModelScope.launch {
            _isLoading.value = true

            try {
                Log.d(TAG, "🔍 Loading applications for job $jobId")

                applicationRepository.getApplicationsForJob(jobId)
                    .catch { e ->
                        Log.e(TAG, "❌ Error loading applications for job $jobId: ${e.message}")
                        _recentApplications.value = emptyList()
                        _isLoading.value = false
                    }
                    .collect { result ->
                        if (result.isSuccess) {
                            val applications = result.getOrNull() ?: emptyList()
                            _recentApplications.value = applications
                            Log.d(TAG, "✅ Loaded ${applications.size} applications for job $jobId")

                            // 🚀 FIXED: Load work sessions without auto-dialogs
                            loadWorkSessionsForApplications(applications)
                        } else {
                            Log.e(TAG, "❌ Failed to get applications for job $jobId")
                            _recentApplications.value = emptyList()
                        }
                        _isLoading.value = false
                    }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Exception in loadApplicationsForJob: ${e.message}")
                _recentApplications.value = emptyList()
                _isLoading.value = false
            }
        }
    }

    /**
     * Clear OTP data for specific application
     */
    fun clearOtpDataForApplication(applicationId: String) {
        setApplicationOtp(applicationId, "")
        setApplicationWorkSession(applicationId, null)
        clearApplicationError(applicationId)
        setApplicationOtpDialogState(applicationId, false)

        Log.d(TAG, "🧹 Cleared OTP data for application $applicationId")
    }

    /**
     * Clear all OTP data
     */
    fun clearAllOtpData() {
        _applicationOtps.value = emptyMap()
        _applicationWorkSessions.value = emptyMap()
        _applicationOtpErrors.value = emptyMap()
        _applicationLoadingStates.value = emptyMap()
        _applicationOtpDialogStates.value = emptyMap()

        // Also clear legacy state
        clearOtpData()

        Log.d(TAG, "🧹 Cleared all OTP data")
    }

    /**
     * Legacy clear method
     */
    fun clearOtpData() {
        _generatedOtp.value = null
        _workSession.value = null
        _otpError.value = null
    }

    /**
     * Helper methods for state management
     */
    private fun setApplicationLoadingState(applicationId: String, isLoading: Boolean) {
        val currentMap = _applicationLoadingStates.value.toMutableMap()
        currentMap[applicationId] = isLoading
        _applicationLoadingStates.value = currentMap
        Log.d(TAG, "📊 Set loading state for $applicationId: $isLoading")
    }

    private fun setApplicationOtp(applicationId: String, otp: String) {
        val currentMap = _applicationOtps.value.toMutableMap()
        if (otp.isBlank()) {
            currentMap.remove(applicationId)
        } else {
            currentMap[applicationId] = otp
        }
        _applicationOtps.value = currentMap
        Log.d(TAG, "🔐 ${if (otp.isBlank()) "REMOVED" else "STORED"} OTP for application $applicationId: $otp")
    }

    private fun setApplicationWorkSession(applicationId: String, workSession: WorkSession?) {
        val currentMap = _applicationWorkSessions.value.toMutableMap()
        if (workSession == null) {
            currentMap.remove(applicationId)
        } else {
            currentMap[applicationId] = workSession
        }
        _applicationWorkSessions.value = currentMap
        Log.d(TAG, "📄 ${if (workSession == null) "REMOVED" else "STORED"} work session for application $applicationId")
    }

    private fun setApplicationError(applicationId: String, error: String) {
        val currentMap = _applicationOtpErrors.value.toMutableMap()
        currentMap[applicationId] = error
        _applicationOtpErrors.value = currentMap
        Log.e(TAG, "❌ Set error for $applicationId: $error")
    }

    private fun clearApplicationError(applicationId: String) {
        val currentMap = _applicationOtpErrors.value.toMutableMap()
        currentMap.remove(applicationId)
        _applicationOtpErrors.value = currentMap
    }

    /**
     * 🚀 NEW: Dialog state management
     */
    private fun setApplicationOtpDialogState(applicationId: String, shouldShow: Boolean) {
        val currentMap = _applicationOtpDialogStates.value.toMutableMap()
        if (shouldShow) {
            currentMap[applicationId] = true
        } else {
            currentMap.remove(applicationId)
        }
        _applicationOtpDialogStates.value = currentMap
        Log.d(TAG, "🚀 Set OTP dialog state for $applicationId: $shouldShow")
    }

    private suspend fun loadWorkSessionsForAllApplications(applications: List<ApplicationWithJob>) {
        try {
            supervisorScope {
                applications.forEach { application ->
                    launch {
                        if (application.status in listOf(
                                ApplicationStatus.SELECTED,
                                ApplicationStatus.WORK_IN_PROGRESS,
                                ApplicationStatus.COMPLETION_PENDING,
                                ApplicationStatus.COMPLETED
                            )) {
                            getWorkSessionWithCompletion(application.id)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading work sessions: ${e.message}")
        }
    }

    private fun updateApplicationCounts(applications: List<ApplicationWithJob>) {
        _totalApplicationsCount.value = applications.size
        _selectedApplicationsCount.value = applications.count { it.status == ApplicationStatus.SELECTED }
        _completionPendingCount.value = applications.count { it.status == ApplicationStatus.COMPLETION_PENDING }
    }

    /**
     * Load applications with completion status awareness
     */
    fun loadRecentApplications(limit: Int = 5, statusFilter: String? = null) {
        viewModelScope.launch {
            _isLoading.value = true

            try {
                val userId = authRepository.getCurrentUserId() ?: return@launch
                Log.d(TAG, "🔍 Loading applications with filter: $statusFilter")

                val applications = if (statusFilter == "SELECTED") {
                    applicationRepository.getSelectedApplicationsForEmployer(
                        employerId = userId,
                        limit = limit
                    )
                } else {
                    applicationRepository.getApplicationsForEmployerDashboard(
                        employerId = userId,
                        limit = limit,
                        statusFilter = statusFilter
                    )
                }

                _recentApplications.value = applications
                Log.d(TAG, "✅ Loaded ${applications.size} applications")

                // Load work sessions for applications, including completion pending ones
                loadWorkSessionsForApplications(applications)

                // Debug status breakdown
                val statusBreakdown = applications.groupBy { it.status }.mapValues { it.value.size }
                Log.d(TAG, "Applications breakdown: $statusBreakdown")

            } catch (e: Exception) {
                Log.e(TAG, "Error loading applications: ${e.message}")
                _recentApplications.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Load work sessions for multiple applications including completion status
     */
    private suspend fun loadWorkSessionsForApplications(applications: List<ApplicationWithJob>) {
        try {
            supervisorScope {
                applications.forEach { application ->
                    launch {
                        // Load work session for all applications that might have sessions
                        if (application.status in listOf(
                                ApplicationStatus.SELECTED,
                                ApplicationStatus.WORK_IN_PROGRESS,
                                ApplicationStatus.COMPLETED
                            )) {
                            getWorkSessionWithCompletion(application.id)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading work sessions: ${e.message}")
        }
    }

    /**
     * Clear all data
     */
    fun clearAllData() {
        _applicationOtps.value = emptyMap()
        _applicationWorkSessions.value = emptyMap()
        _applicationOtpErrors.value = emptyMap()
        _applicationLoadingStates.value = emptyMap()
        _applicationOtpDialogStates.value = emptyMap()
        _completionOtpDialogStates.value = emptyMap()
        _completionOtpErrors.value = emptyMap()
        _completionLoadingStates.value = emptyMap()
        _completionResults.value = emptyMap()
        _error.value = null
    }


    private fun setCompletionResult(applicationId: String, result: String) {
        val currentMap = _completionResults.value.toMutableMap()
        currentMap[applicationId] = result
        _completionResults.value = currentMap
    }

    /**
     * Refresh applications
     */
    fun refreshApplications() {
        viewModelScope.launch {
            try {
                val currentSize = _recentApplications.value.size
                loadApplicationsWithCompletion(currentSize.coerceAtLeast(10))
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error refreshing applications: ${e.message}")
            }
        }
    }

    /**
     * 🚀 ENHANCED: Verify work completion OTP and finalize work
     */
    fun verifyCompletionOtpAndFinalize(applicationId: String, enteredOtp: String) {
        viewModelScope.launch {
            Log.d(TAG, "🚀 Verifying completion OTP for: $applicationId")

            setCompletionLoadingState(applicationId, true)
            clearCompletionError(applicationId)

            try {
                val result = applicationRepository.verifyCompletionOtpAndFinalize(applicationId, enteredOtp)

                if (result.isSuccess) {
                    val completionResult = result.getOrNull()!!

                    Log.d(TAG, "✅ Work completion verified successfully!")
                    Log.d(TAG, "💼 Job: ${completionResult.jobTitle}")
                    Log.d(TAG, "⏰ Duration: ${completionResult.workDurationMinutes} minutes")
                    Log.d(TAG, "💰 Total Wages: ₹${completionResult.totalWages}")

                    // Store completion result
                    setCompletionResult(
                        applicationId,
                        "Work completed! Duration: ${completionResult.workDurationMinutes}min, Wages: ₹${completionResult.totalWages}"
                    )

                    // Hide completion dialog
                    hideCompletionOtpDialog(applicationId)

                    // Refresh applications
                    refreshApplications()

                } else {
                    val error = result.exceptionOrNull()?.message ?: "Failed to verify completion OTP"
                    Log.e(TAG, "❌ Completion verification failed: $error")
                    setCompletionError(applicationId, error)
                }
            } catch (e: Exception) {
                val error = e.message ?: "Unknown error occurred"
                Log.e(TAG, "❌ Exception during completion verification: $error")
                setCompletionError(applicationId, error)
            } finally {
                setCompletionLoadingState(applicationId, false)
            }
        }
    }

    private suspend fun loadWorkSessionForApplication(applicationId: String) {
        try {
            val result = applicationRepository.getWorkSessionForApplication(applicationId)
            if (result.isSuccess) {
                val workSession = result.getOrNull()
                if (workSession != null) {
                    setApplicationWorkSession(applicationId, workSession)
                    if (workSession.status == "OTP_GENERATED" && workSession.otp.isNotBlank()) {
                        setApplicationOtp(applicationId, workSession.otp)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading work session for $applicationId: ${e.message}")
        }
    }


    /**
     * 🚀 NEW: Show completion OTP dialog
     */
    fun showCompletionOtpDialog(applicationId: String) {
        Log.d(TAG, "🚀 Show completion OTP dialog for: $applicationId")
        setCompletionOtpDialogState(applicationId, true)
    }

    /**
     * 🚀 NEW: Hide completion OTP dialog
     */
    fun hideCompletionOtpDialog(applicationId: String) {
        Log.d(TAG, "🚀 Hide completion OTP dialog for: $applicationId")
        setCompletionOtpDialogState(applicationId, false)
        clearCompletionError(applicationId)
    }

    /**
     * 🚀 NEW: Check if completion OTP dialog should be shown
     */
    fun shouldShowCompletionOtpDialog(applicationId: String): Boolean {
        return _completionOtpDialogStates.value[applicationId] ?: false
    }

    /**
     * 🚀 NEW: Check if application has completion pending
     */
    fun hasCompletionPending(application: ApplicationWithJob): Boolean {
        val appStatus = application.status == ApplicationStatus.COMPLETION_PENDING
        val sessionStatus = application.workSession?.status == "COMPLETION_PENDING"
        return appStatus || sessionStatus
    }

    /**
     * 🚀 NEW: Get completion OTP from work session
     */
    fun getCompletionOtpForApplication(applicationId: String): String? {
        val workSession = _applicationWorkSessions.value[applicationId]
        return workSession?.completionOtp
    }


    /**
     * 🚀 NEW: Get completion error for specific application
     */
    fun getCompletionErrorForApplication(applicationId: String): String? {
        return _completionOtpErrors.value[applicationId]
    }

    /**
     * 🚀 NEW: Check if completion is loading for specific application
     */
    fun isCompletionLoading(applicationId: String): Boolean {
        return _completionLoadingStates.value[applicationId] ?: false
    }

    /**
     * 🚀 NEW: Get work session with completion data
     */
    fun getWorkSessionWithCompletion(applicationId: String) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "🔍 Loading work session with completion data for: $applicationId")

                val result = applicationRepository.getWorkSessionWithCompletion(applicationId)

                if (result.isSuccess) {
                    val workSession = result.getOrNull()
                    if (workSession != null) {
                        setApplicationWorkSession(applicationId, workSession)

                        when (workSession.status) {
                            "COMPLETION_PENDING" -> {
                                Log.d(TAG, "✅ Found completion pending session")
                                // Could auto-show completion dialog if needed
                            }
                            "WORK_COMPLETED" -> {
                                Log.d(TAG, "✅ Work already completed")
                            }
                            "OTP_GENERATED" -> {
                                if (workSession.otp.isNotBlank()) {
                                    setApplicationOtp(applicationId, workSession.otp)
                                }
                            }
                            "WORK_IN_PROGRESS" -> {
                                Log.d(TAG, "✅ Work in progress")
                            }
                        }
                    }
                } else {
                    Log.w(TAG, "⚠️ Failed to load work session: ${result.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error loading work session: ${e.message}")
            }
        }
    }



    /**
     * Helper methods for completion state management
     */
    private fun setCompletionLoadingState(applicationId: String, isLoading: Boolean) {
        val currentMap = _completionLoadingStates.value.toMutableMap()
        currentMap[applicationId] = isLoading
        _completionLoadingStates.value = currentMap
        Log.d(TAG, "📊 Set completion loading state for $applicationId: $isLoading")
    }

    private fun setCompletionError(applicationId: String, error: String) {
        val currentMap = _completionOtpErrors.value.toMutableMap()
        currentMap[applicationId] = error
        _completionOtpErrors.value = currentMap
        Log.e(TAG, "❌ Set completion error for $applicationId: $error")
    }

    private fun clearCompletionError(applicationId: String) {
        val currentMap = _completionOtpErrors.value.toMutableMap()
        currentMap.remove(applicationId)
        _completionOtpErrors.value = currentMap
    }

    private fun setCompletionOtpDialogState(applicationId: String, shouldShow: Boolean) {
        val currentMap = _completionOtpDialogStates.value.toMutableMap()
        if (shouldShow) {
            currentMap[applicationId] = true
        } else {
            currentMap.remove(applicationId)
        }
        _completionOtpDialogStates.value = currentMap
        Log.d(TAG, "🚀 Set completion OTP dialog state for $applicationId: $shouldShow")
    }

    /**
     * Load applications with completion status awareness
     */
    fun loadRecentApplicationsWithCompletion(limit: Int = 5, statusFilter: String? = null) {
        viewModelScope.launch {
            _isLoading.value = true

            try {
                val userId = authRepository.getCurrentUserId() ?: return@launch
                Log.d(TAG, "🔍 Loading applications with completion awareness, filter: $statusFilter")

                val applications = if (statusFilter == "SELECTED") {
                    applicationRepository.getSelectedApplicationsForEmployer(
                        employerId = userId,
                        limit = limit
                    )
                } else {
                    applicationRepository.getApplicationsForEmployerDashboard(
                        employerId = userId,
                        limit = limit,
                        statusFilter = statusFilter
                    )
                }

                _recentApplications.value = applications
                Log.d(TAG, "✅ Loaded ${applications.size} applications")

                // Load work sessions for applications, including completion pending ones
                loadWorkSessionsForApplicationsWithCompletion(applications)

                // Debug status breakdown
                val statusBreakdown = applications.groupBy { it.status }.mapValues { it.value.size }
                Log.d(TAG, "Applications breakdown: $statusBreakdown")

            } catch (e: Exception) {
                Log.e(TAG, "Error loading applications with completion: ${e.message}")
                _recentApplications.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }

    suspend fun verifyWorkCompletionOtp(applicationId: String, otp: String): Result<String> {
        return try {
            Log.d(TAG, "🚀 Verifying work completion OTP for application: $applicationId")

            // Call the repository method
            val result = applicationRepository.verifyWorkCompletionOtp(applicationId, otp)

            if (result.isSuccess) {
                Log.d(TAG, "✅ Work completion OTP verified successfully")

                // Refresh applications to show updated status
                    delay(500)
                    loadRecentApplications(50)

            } else {
                Log.e(TAG, "❌ Work completion OTP verification failed: ${result.exceptionOrNull()?.message}")
            }

            result

        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception during work completion OTP verification: ${e.message}", e)
            Result.failure(Exception("Failed to verify completion: ${e.message}"))
        }
    }


    /**
     * Load work sessions for multiple applications including completion status
     */
    private suspend fun loadWorkSessionsForApplicationsWithCompletion(applications: List<ApplicationWithJob>) {
        try {
            supervisorScope {
                applications.forEach { application ->
                    launch {
                        // Load work session for applications that might have sessions
                        if (application.status in listOf(
                                ApplicationStatus.SELECTED,
                                ApplicationStatus.WORK_IN_PROGRESS,
                                ApplicationStatus.COMPLETED
                            )) {
                            getWorkSessionWithCompletion(application.id)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading work sessions with completion: ${e.message}")
        }
    }

}

enum class DashboardCardType {
    TOTAL_JOBS,
    ACTIVE_JOBS,
    APPLICATIONS,
    RATING
}

enum class JobsFilter {
    ALL_JOBS,           // Show all jobs (including rejected)
    ACTIVE_ONLY         // Show only approved/active jobs
}

sealed class DashboardNavigationEvent {
    data class NavigateToMyJobs(
        val filter: JobsFilter,
        val title: String
    ) : DashboardNavigationEvent()

    data class NavigateToApplications(
        val title: String
    ) : DashboardNavigationEvent()
}