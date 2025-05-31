// 🚀 COMPLETE JobViewModel.kt - All Functions with Clean Architecture

package com.example.gigs.viewmodel

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gigs.data.model.*
import com.example.gigs.data.repository.*
import com.example.gigs.data.util.PerformanceUtils
import com.example.gigs.data.util.track
import com.example.gigs.ui.screens.jobs.JobFilters
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import kotlinx.coroutines.delay
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

enum class SortOption {
    DATE_NEWEST,
    DATE_OLDEST,
    SALARY_HIGH_LOW,
    SALARY_LOW_HIGH,
    ALPHABETICAL
}

@HiltViewModel
class JobViewModel @Inject constructor(
    val jobRepository: JobRepository,
    private val employerProfileRepository: EmployerProfileRepository,
    private val applicationRepository: ApplicationRepository,
    private val profileRepository: ProfileRepository,
    private val authRepository: AuthRepository,
    private val processedJobsRepository: ProcessedJobsRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val TAG = "JobViewModel"

    // 🚀 UI STATE MANAGEMENT: Processing and performance monitoring
    private val duplicateOperationPreventer = ConcurrentHashMap<String, Long>()
    private val OPERATION_COOLDOWN = 1000L
    private val processingJobs = mutableSetOf<String>()
    private val performanceMonitor = PerformanceUtils.AdvancedPerformanceMonitor()
    private val applicationLoadingCache = mutableMapOf<String, Long>()
    private val CACHE_VALIDITY_MS = 30000L

    // 🚀 REJECTED JOBS SESSION MANAGEMENT
    private val _currentRejectedJobsSession = MutableStateFlow<List<Job>>(emptyList())
    private val _rejectedJobsProcessedInSession = MutableStateFlow<Set<String>>(emptySet())

    // 🚀 PERFORMANCE UTILITIES
    private val throttler = PerformanceUtils.UnifiedThrottler(1000L)
    private val cache = PerformanceUtils.LRUCache<String, List<Job>>(maxSize = 50, expiryTimeMs = 2 * 60 * 1000L)
    private val debouncer = PerformanceUtils.Debouncer(300L, viewModelScope)
    private var memoryCallback: (() -> Unit)? = null

    // 🚀 UI STATE FLOWS
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

    private val _applicationUIState = MutableStateFlow<ApplicationUIState>(ApplicationUIState.IDLE)
    val applicationUIState: StateFlow<ApplicationUIState> = _applicationUIState

    private val _employeeProfile = MutableStateFlow<EmployeeProfile?>(null)
    val employeeProfile: StateFlow<EmployeeProfile?> = _employeeProfile

    private val _currentSortOption = MutableStateFlow(SortOption.DATE_NEWEST)
    val currentSortOption: StateFlow<SortOption> = _currentSortOption

    private val _jobFilters = MutableStateFlow(JobFilters())
    val jobFilters: StateFlow<JobFilters> = _jobFilters

    // 🚀 REPOSITORY STATE FLOWS
    val isShowingRejectedJobs = processedJobsRepository.isShowingRejectedJobs
    val appliedJobIds = processedJobsRepository.appliedJobIds

    init {
        // Performance monitoring setup
        memoryCallback = {
            Log.d(TAG, "Memory pressure detected, clearing caches")
            cache.clear()
        }
        memoryCallback?.let { PerformanceUtils.MemoryMonitor.addLowMemoryCallback(it) }

        // Initialize
        loadUserApplications()
        loadRejectedJobs()
    }

    sealed class ApplicationUIState {
        object IDLE : ApplicationUIState()
        object LOADING : ApplicationUIState()
        object SUCCESS : ApplicationUIState()
        data class ERROR(val message: String) : ApplicationUIState()
    }

    // 🚀 SORTING AND FILTERING
    fun setSortOption(sortOption: SortOption) {
        PerformanceUtils.PerformanceMetrics.measureOperation("set_sort_option", "ui") {
            _currentSortOption.value = sortOption
            applyFiltersAndSort()
        }
    }

    fun setJobFilters(filters: JobFilters) {
        PerformanceUtils.PerformanceMetrics.measureOperation("set_job_filters", "ui") {
            _jobFilters.value = filters
            applyFiltersAndSort()
        }
    }

    private fun applyFiltersAndSort() {
        viewModelScope.launch {
            PerformanceUtils.PerformanceMetrics.measureOperation("apply_filters_and_sort", "ui") {
                val allJobs = _jobs.value
                val filters = _jobFilters.value
                val sortOption = _currentSortOption.value

                val filteredJobs = applyFilters(allJobs, filters)
                val sortedJobs = applySorting(filteredJobs, sortOption)

                _featuredJobs.value = sortedJobs
            }
        }
    }

    // 🚀 JOB LOADING METHODS
    fun getJobsByDistrict(district: String, applyFiltersAndSort: Boolean = true) {
        val cacheKey = "jobs_district_$district"

        // Check cache first
        cache.get(cacheKey)?.let { cachedJobs ->
            Log.d(TAG, "Using cached jobs for district: $district")
            _jobs.value = cachedJobs
            if (applyFiltersAndSort) {
                this.applyFiltersAndSort()
            }
            return
        }

        // Prevent duplicate requests
        if (_isLoading.value) {
            Log.d(TAG, "Already loading jobs for district: $district")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) { _isLoading.value = true }

                val jobs = withTimeoutOrNull(3000) {
                    jobRepository.getJobsByLocationDirectPublic(district)
                } ?: emptyList()

                withContext(Dispatchers.Main) {
                    _jobs.value = jobs
                    cache.put(cacheKey, jobs)

                    if (applyFiltersAndSort) {
                        applyFiltersAndSort()
                    } else {
                        if (isShowingRejectedJobs.value) {
                            val rejectedJobs = processedJobsRepository.getRejectedJobIds()
                            _featuredJobs.value = jobs.filter { job ->
                                rejectedJobs.contains(job.id)
                            }
                        } else {
                            val processedJobs = processedJobsRepository.getProcessedJobIds()
                            _featuredJobs.value = jobs.filter { job ->
                                !processedJobs.contains(job.id)
                            }
                        }
                    }

                    _isLoading.value = false
                    Log.d(TAG, "Successfully loaded ${jobs.size} jobs for district: $district")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading jobs: ${e.message}")
                withContext(Dispatchers.Main) {
                    _isLoading.value = false
                    _jobs.value = emptyList()
                    _featuredJobs.value = emptyList()
                }
            }
        }
    }

    fun getJobsByDistrict(district: String) {
        getJobsByDistrict(district, true)
    }

    suspend fun getJobsByLocationDirect(district: String): Result<List<Job>> {
        return try {
            val jobs = jobRepository.getJobsByLocationDirectPublic(district)
            Result.success(jobs)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getLocalizedFeaturedJobs(district: String, limit: Int = 5) {
        val cacheKey = "featured_jobs_$district"

        cache.get(cacheKey)?.let { cachedJobs ->
            Log.d(TAG, "Using cached featured jobs for district: $district")
            val processedJobs = processedJobsRepository.getProcessedJobIds()
            val filteredJobs = cachedJobs.filter { job -> !processedJobs.contains(job.id) }
            _featuredJobs.value = filteredJobs.take(limit)
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            try {
                Log.d(TAG, "Getting localized featured jobs for district: $district")

                val allJobs = withTimeoutOrNull(2000) {
                    jobRepository.getJobsByLocationDirectPublic(district)
                } ?: emptyList()

                withContext(Dispatchers.Main) {
                    Log.d(TAG, "Retrieved ${allJobs.size} jobs for district $district")
                    cache.put(cacheKey, allJobs)

                    val processedJobs = processedJobsRepository.getProcessedJobIds()
                    val filteredJobs = allJobs.filter { job ->
                        !processedJobs.contains(job.id)
                    }

                    _featuredJobs.value = filteredJobs.take(limit)
                    Log.d(TAG, "Filtered to ${_featuredJobs.value.size} featured jobs")
                    _isLoading.value = false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception: ${e.message}")
                withContext(Dispatchers.Main) {
                    _isLoading.value = false
                    getFeaturedJobs(limit)
                }
            }
        }
    }

    fun getFeaturedJobs(limit: Int = 5) {
        val cacheKey = "featured_jobs_global"

        cache.get(cacheKey)?.let { cachedJobs ->
            Log.d(TAG, "Using cached global featured jobs")
            val processedJobs = processedJobsRepository.getProcessedJobIds()
            val filteredJobs = cachedJobs.filter { job -> !processedJobs.contains(job.id) }
            _featuredJobs.value = filteredJobs.take(limit)
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            try {
                val allJobs = withTimeoutOrNull(3000) {
                    jobRepository.getFeaturedJobsDirect(limit * 2)
                } ?: emptyList()

                withContext(Dispatchers.Main) {
                    cache.put(cacheKey, allJobs)
                    val processedJobs = processedJobsRepository.getProcessedJobIds()
                    val filteredJobs = allJobs.filter { job ->
                        !processedJobs.contains(job.id)
                    }

                    _featuredJobs.value = filteredJobs.take(limit)
                    Log.d(TAG, "Loaded ${_featuredJobs.value.size} featured jobs after filtering")
                    _isLoading.value = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _isLoading.value = false
                    _featuredJobs.value = emptyList()
                    Log.e(TAG, "Error loading featured jobs: ${e.message}")
                }
            }
        }
    }

    fun getAllJobsWithoutFiltering(district: String, limit: Int = 10) {
        val cacheKey = "all_jobs_$district"

        cache.get(cacheKey)?.let { cachedJobs ->
            Log.d(TAG, "Using cached all jobs for district: $district")
            _jobs.value = cachedJobs.take(limit)
            _featuredJobs.value = cachedJobs.take(limit)
            Log.d(TAG, "Returning ${cachedJobs.take(limit).size} jobs without filtering")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            try {
                Log.d(TAG, "Getting ALL jobs for district: $district (without filtering)")

                val allJobs = withTimeoutOrNull(3000) {
                    jobRepository.getJobsByLocationDirectPublic(district)
                } ?: emptyList()

                withContext(Dispatchers.Main) {
                    Log.d(TAG, "Retrieved ${allJobs.size} jobs for district $district (no filtering)")
                    cache.put(cacheKey, allJobs)

                    _jobs.value = allJobs.take(limit)
                    _featuredJobs.value = allJobs.take(limit)

                    Log.d(TAG, "Returning ${_featuredJobs.value.size} jobs without filtering")
                    _isLoading.value = false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception: ${e.message}")
                withContext(Dispatchers.Main) {
                    _isLoading.value = false
                    _featuredJobs.value = emptyList()
                }
            }
        }
    }

    fun loadJobsProgressively(district: String) {
        viewModelScope.launch(Dispatchers.IO) {
            // Load first batch quickly
            val quickJobs = withTimeoutOrNull(1000) {
                jobRepository.getJobsByLocationDirectPublic(district).take(5)
            } ?: emptyList()

            // Show initial jobs immediately
            withContext(Dispatchers.Main) {
                if (quickJobs.isNotEmpty()) {
                    _featuredJobs.value = quickJobs
                    _isLoading.value = false
                    Log.d(TAG, "Loaded ${quickJobs.size} jobs quickly")
                }
            }

            // Load remaining jobs in background
            val allJobs = withTimeoutOrNull(3000) {
                jobRepository.getJobsByLocationDirectPublic(district)
            } ?: quickJobs

            withContext(Dispatchers.Main) {
                val processedJobs = processedJobsRepository.getProcessedJobIds()
                val filteredJobs = allJobs.filter { !processedJobs.contains(it.id) }
                _featuredJobs.value = filteredJobs
                Log.d(TAG, "Loaded all ${filteredJobs.size} jobs")
            }
        }
    }

    private var lastLoadTime = 0L
    private val CACHE_DURATION = 30_000L

    fun getLocalizedFeaturedJobsCached(district: String, limit: Int = 5) {
        val currentTime = System.currentTimeMillis()

        if (currentTime - lastLoadTime < CACHE_DURATION && _featuredJobs.value.isNotEmpty()) {
            Log.d(TAG, "Using cached jobs for $district")
            return
        }

        lastLoadTime = currentTime
        loadJobsProgressively(district)
    }

    // 🚀 JOB DETAILS AND PROFILE METHODS
    fun getJobDetails(jobId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            try {
                val job = withTimeoutOrNull(2000) {
                    jobRepository.getJobByIdDirect(jobId)
                }

                withContext(Dispatchers.Main) {
                    _selectedJob.value = job
                    job?.let { loadEmployerProfile(it.employerId) }
                    _isLoading.value = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _isLoading.value = false
                }
            }
        }
    }

    private fun loadEmployerProfile(employerId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val profile = withTimeoutOrNull(2000) {
                    jobRepository.supabaseClient
                        .table("employer_profiles")
                        .select {
                            filter { eq("user_id", employerId) }
                        }
                        .decodeSingleOrNull<EmployerProfile>()
                }

                withContext(Dispatchers.Main) {
                    _employerProfile.value = profile
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading employer profile: ${e.message}")
            }
        }
    }

    fun getEmployeeProfile() {
        viewModelScope.launch(Dispatchers.IO) {
            val userId = authRepository.getCurrentUserId() ?: return@launch
            try {
                val profile = withTimeoutOrNull(2000) {
                    jobRepository.supabaseClient
                        .table("employee_profiles")
                        .select {
                            filter { eq("user_id", userId) }
                        }
                        .decodeSingleOrNull<EmployeeProfile>()
                }

                withContext(Dispatchers.Main) {
                    _employeeProfile.value = profile
                    Log.d(TAG, "Loaded employee profile: ${profile?.district}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception loading employee profile: ${e.message}")
            }
        }
    }

    fun getMyJobs(limit: Int = 10) {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            try {
                Log.d(TAG, "Loading my jobs with limit: $limit")

                val jobs = withTimeoutOrNull(3000) {
                    jobRepository.getMyJobsDirect(limit)
                } ?: emptyList()

                withContext(Dispatchers.Main) {
                    Log.d(TAG, "Retrieved ${jobs.size} jobs")
                    _jobs.value = jobs
                    _isLoading.value = false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception: ${e.message}")
                withContext(Dispatchers.Main) {
                    _isLoading.value = false
                    _jobs.value = emptyList()
                }
            }
        }
    }

    // 🚀 JOB APPLICATION METHODS
    fun applyForJob(jobId: String) {
        debouncer.debounce {
            PerformanceUtils.PerformanceMetrics.measureOperation("apply_job_$jobId", "database") {
                viewModelScope.launch {
                    executeJobApplication(jobId)
                }
            }
        }
    }

    private suspend fun executeJobApplication(jobId: String) {
        withContext(Dispatchers.Main) { _isLoading.value = true }

        try {
            Log.d(TAG, "Applying for job: $jobId")

            // 1. Immediately mark as applied in local state
            processedJobsRepository.markJobAsApplied(jobId)

            // 2. Remove from UI immediately
            withContext(Dispatchers.Main) {
                removeJobFromFeaturedJobs(jobId)
                Log.d(TAG, "Immediately removed job $jobId from UI display")
            }

            // 3. Apply in backend
            val result = withContext(Dispatchers.IO) {
                if (processedJobsRepository.isShowingRejectedJobs.value) {
                    try {
                        applicationRepository.updateEmployeeApplicationStatus(jobId, "APPLIED")
                            .first()
                            .let { Result.success(Unit) }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error updating rejected job application: ${e.message}")
                        Result.failure(e)
                    }
                } else {
                    try {
                        applicationRepository.applyForJob(jobId)
                            .first()
                            .let { Result.success(Unit) }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error creating new job application: ${e.message}")
                        Result.failure(e)
                    }
                }
            }

            // 4. Handle result and update UI state
            withContext(Dispatchers.Main) {
                if (result.isSuccess) {
                    Log.d(TAG, "Successfully applied for job: $jobId")
                    _hasApplied.value = true
                    _applicationUIState.value = ApplicationUIState.SUCCESS

                    clearJobCaches()

                    val district = _employeeProfile.value?.district
                    if (processedJobsRepository.isShowingRejectedJobs.value) {
                        if (!district.isNullOrBlank()) {
                            Log.d(TAG, "Refreshing rejected jobs for district: $district")
                            getOnlyRejectedJobs(district)
                        }
                    } else {
                        if (!district.isNullOrBlank()) {
                            Log.d(TAG, "Loading fresh jobs for district: $district")
                            getLocalizedFeaturedJobs(district, _featuredJobs.value.size + 1)
                        } else {
                            getFeaturedJobs(_featuredJobs.value.size + 1)
                        }
                    }

                    Log.d(TAG, "Job application completed successfully")
                } else {
                    val errorMessage = result.exceptionOrNull()?.message ?: "Unknown error"
                    Log.e(TAG, "Error applying for job: $errorMessage")

                    if (errorMessage.contains("already applied", ignoreCase = true) ||
                        errorMessage.contains("duplicate", ignoreCase = true)) {
                        _hasApplied.value = true
                        _applicationUIState.value = ApplicationUIState.SUCCESS
                        Log.d(TAG, "Job was already applied, keeping it removed from display")
                    } else {
                        _applicationUIState.value = ApplicationUIState.ERROR(errorMessage)

                        if (errorMessage.contains("network", ignoreCase = true) ||
                            errorMessage.contains("timeout", ignoreCase = true)) {
                            processedJobsRepository.markJobAsRejected(jobId)
                            processedJobsRepository.updateAppliedJobIds(
                                processedJobsRepository.appliedJobIds.value - jobId
                            )
                            Log.d(TAG, "Network error, job status reverted")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception applying for job: ${e.message}", e)

            withContext(Dispatchers.Main) {
                _applicationUIState.value = ApplicationUIState.ERROR(e.message ?: "Unknown error")

                if (e.message?.contains("network", ignoreCase = true) == true) {
                    processedJobsRepository.updateAppliedJobIds(
                        processedJobsRepository.appliedJobIds.value - jobId
                    )
                    Log.d(TAG, "Exception occurred, job status reverted due to network error")
                }
            }
        } finally {
            withContext(Dispatchers.Main) {
                _isLoading.value = false
                Log.d(TAG, "Job application process completed for: $jobId")
            }
        }
    }

    fun checkIfApplied(jobId: String) {
        if (processedJobsRepository.isJobApplied(jobId)) {
            _hasApplied.value = true
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val userId = authRepository.getCurrentUserId() ?: return@launch

                val applications = withTimeoutOrNull(2000) {
                    jobRepository.supabaseClient
                        .table("applications")
                        .select {
                            filter {
                                eq("job_id", jobId)
                                eq("employee_id", userId)
                                eq("status", "APPLIED")
                            }
                        }
                        .decodeList<Application>()
                } ?: emptyList()

                val hasApplied = applications.isNotEmpty()

                withContext(Dispatchers.Main) {
                    _hasApplied.value = hasApplied

                    if (hasApplied) {
                        processedJobsRepository.markJobAsApplied(jobId)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking application status: ${e.message}")
            }
        }
    }

    // 🚀 JOB REJECTION METHODS
    fun markJobAsNotInterested(jobId: String) {
        if (processingJobs.contains(jobId)) {
            Log.w(TAG, "🚫 Job $jobId already being processed, blocking duplicate")
            return
        }

        processingJobs.add(jobId)

        viewModelScope.launch(Dispatchers.Main.immediate) {
            val actionStart = System.currentTimeMillis()

            try {
                performanceMonitor.track("ultra_fast_reject_$jobId", "ui") {
                    Log.d(TAG, "⚡ ULTRA-FAST: Starting instant rejection for job: $jobId")

                    // Immediate UI updates
                    val currentJobs = _featuredJobs.value.filter { job -> job.id != jobId }
                    _featuredJobs.value = currentJobs

                    val updatedSessionSet = _rejectedJobsProcessedInSession.value + jobId
                    _rejectedJobsProcessedInSession.value = updatedSessionSet

                    // Immediate repository update
                    processedJobsRepository.markJobAsRejectedUltraFast(jobId)

                    val uiTime = System.currentTimeMillis() - actionStart
                    Log.d(TAG, "✅ INSTANT: UI updated for job $jobId in ${uiTime}ms")

                    // Fire-and-forget background database sync
                    launch(Dispatchers.IO) {
                        try {
                            withTimeoutOrNull(2000) {
                                val success = jobRepository.markJobAsNotInterested(jobId)

                                if (success) {
                                    Log.d(TAG, "✅ BACKGROUND: Database synced for job $jobId")
                                } else {
                                    Log.w(TAG, "⚠️ BACKGROUND: Database sync failed for $jobId")
                                }
                            } ?: Log.w(TAG, "⚠️ BACKGROUND: Database sync timeout for $jobId (UI already updated)")
                        } catch (e: Exception) {
                            Log.e(TAG, "❌ BACKGROUND: Database sync error for $jobId: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Ultra-fast rejection failed for $jobId: ${e.message}")
            } finally {
                processingJobs.remove(jobId)

                val totalTime = System.currentTimeMillis() - actionStart
                if (totalTime > 16) {
                    Log.w(TAG, "⚠️ PERFORMANCE: Rejection took ${totalTime}ms (target: <16ms)")
                } else {
                    Log.d(TAG, "✅ PERFORMANCE: Rejection completed in ${totalTime}ms")
                }
            }
        }
    }

    private suspend fun syncRejectionToDatabase(jobId: String): Boolean {
        return try {
            val userId = authRepository.getCurrentUserId() ?: return false

            val existing = withTimeoutOrNull(1000) {
                jobRepository.supabaseClient
                    .table("applications")
                    .select(columns = Columns.list("id", "status")) {
                        filter {
                            eq("job_id", jobId)
                            eq("employee_id", userId)
                        }
                        limit(1)
                    }
                    .decodeList<Map<String, String>>()
            } ?: emptyList()

            val timestamp = java.time.Instant.now().toString()

            if (existing.isNotEmpty()) {
                jobRepository.supabaseClient
                    .table("applications")
                    .update(mapOf(
                        "status" to "REJECTED",
                        "updated_at" to timestamp
                    )) {
                        filter {
                            eq("job_id", jobId)
                            eq("employee_id", userId)
                        }
                    }
            } else {
                jobRepository.supabaseClient
                    .table("applications")
                    .insert(mapOf(
                        "job_id" to jobId,
                        "employee_id" to userId,
                        "status" to "REJECTED",
                        "applied_at" to timestamp,
                        "created_at" to timestamp,
                        "updated_at" to timestamp
                    ))
            }

            Log.d(TAG, "✅ Database sync completed for job $jobId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Database sync failed for job $jobId: ${e.message}")
            false
        }
    }

    // 🚀 REJECTED JOBS METHODS
    fun getOnlyRejectedJobs(district: String) {
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { _isLoading.value = true }

            try {
                Log.d(TAG, "🚀 GETTING ONLY REJECTED JOBS FOR DISTRICT: $district")

                val userId = authRepository.getCurrentUserId() ?: run {
                    withContext(Dispatchers.Main) {
                        _isLoading.value = false
                        _featuredJobs.value = emptyList()
                    }
                    return@launch
                }

                val currentSessionProcessed = _rejectedJobsProcessedInSession.value

                val applications = withTimeoutOrNull(3000) {
                    try {
                        jobRepository.supabaseClient
                            .table("applications")
                            .select {
                                filter { eq("employee_id", userId) }
                                order("updated_at", Order.DESCENDING)
                                limit(100)
                            }
                            .decodeList<Application>()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error fetching applications: ${e.message}")
                        emptyList<Application>()
                    }
                } ?: emptyList()

                Log.d(TAG, "Found ${applications.size} total applications")

                if (applications.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        _featuredJobs.value = emptyList()
                        _isLoading.value = false
                        Log.d(TAG, "❌ No applications found for user")
                    }
                    return@launch
                }

                val applicationsByJobId = applications.groupBy { it.jobId }
                val rejectedJobIds = mutableListOf<String>()
                val appliedJobIds = mutableSetOf<String>()

                applicationsByJobId.forEach { (jobId, apps) ->
                    val sortedApps = apps.sortedByDescending {
                        it.updatedAt ?: it.createdAt ?: ""
                    }

                    val latest = sortedApps.firstOrNull()
                    when (latest?.status?.uppercase()) {
                        "APPLIED" -> {
                            appliedJobIds.add(jobId)
                        }
                        "REJECTED" -> {
                            if (!appliedJobIds.contains(jobId)) {
                                rejectedJobIds.add(jobId)
                            }
                        }
                    }
                }

                val availableRejectedJobIds = rejectedJobIds.filter { jobId ->
                    !currentSessionProcessed.contains(jobId)
                }

                processedJobsRepository.updateRejectedJobIds(rejectedJobIds.toSet())
                processedJobsRepository.updateAppliedJobIds(appliedJobIds)

                if (availableRejectedJobIds.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        _featuredJobs.value = emptyList()
                        _currentRejectedJobsSession.value = emptyList()
                        _isLoading.value = false
                    }
                    return@launch
                }

                val rejectedJobs = withTimeoutOrNull(3000) {
                    try {
                        if (availableRejectedJobIds.size == 1) {
                            val singleJob = jobRepository.supabaseClient
                                .table("jobs")
                                .select {
                                    filter {
                                        eq("id", availableRejectedJobIds.first())
                                        eq("is_active", true)
                                    }
                                }
                                .decodeSingleOrNull<Job>()

                            listOfNotNull(singleJob)
                        } else {
                            val inClause = availableRejectedJobIds.joinToString(",")

                            jobRepository.supabaseClient
                                .table("jobs")
                                .select {
                                    filter {
                                        filter("id", FilterOperator.IN, "($inClause)")
                                        eq("is_active", true)
                                    }
                                    order("created_at", Order.DESCENDING)
                                }
                                .decodeList<Job>()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error fetching job details: ${e.message}")

                        try {
                            Log.d(TAG, "Batch query failed, trying individual queries...")
                            val individualJobs = mutableListOf<Job>()

                            availableRejectedJobIds.take(5).forEach { jobId ->
                                try {
                                    val job = jobRepository.supabaseClient
                                        .table("jobs")
                                        .select {
                                            filter {
                                                eq("id", jobId)
                                                eq("is_active", true)
                                            }
                                        }
                                        .decodeSingleOrNull<Job>()

                                    job?.let { individualJobs.add(it) }
                                } catch (individualError: Exception) {
                                    Log.e(TAG, "Failed to fetch individual job $jobId: ${individualError.message}")
                                }
                            }

                            individualJobs
                        } catch (fallbackError: Exception) {
                            Log.e(TAG, "Fallback individual queries also failed: ${fallbackError.message}")
                            emptyList<Job>()
                        }
                    }
                } ?: emptyList()

                withContext(Dispatchers.Main) {
                    _featuredJobs.value = rejectedJobs
                    _currentRejectedJobsSession.value = rejectedJobs
                    _isLoading.value = false
                    Log.d(TAG, "✅ SUCCESS: Set ${rejectedJobs.size} rejected jobs in UI")
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ Exception getting rejected jobs: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    _isLoading.value = false
                    _featuredJobs.value = emptyList()
                    _currentRejectedJobsSession.value = emptyList()
                }
            }
        }
    }

    fun refreshRejectedJobs() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _isLoading.value = true

                val rejectedJobs = withTimeoutOrNull(3000) {
                    fetchRejectedJobsDirectly()
                } ?: emptyList()

                withContext(Dispatchers.Main) {
                    _isLoading.value = false
                    Log.d(TAG, "Successfully refreshed rejected jobs")

                    if (isShowingRejectedJobs.value && _employeeProfile.value?.district != null) {
                        getOnlyRejectedJobs(_employeeProfile.value!!.district!!)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _isLoading.value = false
                    Log.e(TAG, "Exception refreshing rejected jobs: ${e.message}")
                }
            }
        }
    }

    private suspend fun fetchRejectedJobsDirectly(): List<Job> {
        return try {
            withContext(Dispatchers.IO) {
                val userId = authRepository.getCurrentUserId() ?: return@withContext emptyList()

                val applications = withTimeoutOrNull(1500) {
                    jobRepository.supabaseClient
                        .table("applications")
                        .select {
                            filter {
                                eq("employee_id", userId)
                                eq("status", "REJECTED")
                            }
                            order("updated_at", Order.DESCENDING)
                            limit(20)
                        }
                        .decodeList<Application>()
                } ?: emptyList()

                val rejectedJobIds = applications.map { it.jobId }.toSet()

                withContext(Dispatchers.Main) {
                    processedJobsRepository.updateRejectedJobIds(rejectedJobIds)
                }

                if (rejectedJobIds.isNotEmpty() && rejectedJobIds.size <= 10) {
                    withTimeoutOrNull(1000) {
                        jobRepository.supabaseClient
                            .table("jobs")
                            .select {
                                filter {
                                    filter("id", FilterOperator.IN, "(${rejectedJobIds.joinToString(",")})")
                                }
                                limit(10)
                            }
                            .decodeList<Job>()
                    } ?: emptyList()
                } else {
                    emptyList()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching rejected jobs directly: ${e.message}")
            emptyList()
        }
    }

    // 🚀 DEBUG AND TESTING METHODS
    fun forceShowRejectedJobs() {
        viewModelScope.launch {
            Log.d(TAG, "=== FORCING REJECTED JOBS MODE ===")

            try {
                processedJobsRepository.setShowingRejectedJobs(true)
                Log.d(TAG, "Set showing rejected jobs to TRUE")

                delay(100)

                val isShowing = processedJobsRepository.isShowingRejectedJobs.value
                Log.d(TAG, "State after setting: isShowingRejected = $isShowing")

                val district = _employeeProfile.value?.district ?: "Jind"
                Log.d(TAG, "Loading rejected jobs for district: $district")

                getOnlyRejectedJobs(district)

                delay(1000)
                val jobs = _featuredJobs.value
                Log.d(TAG, "Result: ${jobs.size} jobs loaded")
                jobs.forEach { job ->
                    Log.d(TAG, "- Job: ${job.id} - ${job.title}")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error forcing rejected jobs: ${e.message}")
            }

            Log.d(TAG, "=== FORCE COMPLETE ===")
        }
    }

    fun debugCurrentState() {
        viewModelScope.launch {
            Log.d(TAG, "=== CURRENT STATE DEBUG ===")
            Log.d(TAG, "isShowingRejectedJobs: ${processedJobsRepository.isShowingRejectedJobs.value}")
            Log.d(TAG, "appliedJobIds: ${processedJobsRepository.appliedJobIds.value}")
            Log.d(TAG, "rejectedJobIds: ${processedJobsRepository.rejectedJobIds.value}")
            Log.d(TAG, "featuredJobs count: ${_featuredJobs.value.size}")
            Log.d(TAG, "employee district: ${_employeeProfile.value?.district}")
            Log.d(TAG, "=== END STATE DEBUG ===")
        }
    }

    fun testRejectedJobsWithWorkingUI() {
        viewModelScope.launch {
            Log.d(TAG, "🚀 TESTING REJECTED JOBS WITH WORKING UI")

            try {
                processedJobsRepository.setShowingRejectedJobs(true)
                Log.d(TAG, "Set showing rejected jobs to TRUE")

                delay(200)

                val district = _employeeProfile.value?.district ?: "Jind"
                Log.d(TAG, "Loading rejected jobs for district: $district")

                getOnlyRejectedJobs(district)

                delay(2000)
                val jobs = _featuredJobs.value
                Log.d(TAG, "Result: ${jobs.size} jobs loaded")
                jobs.forEach { job ->
                    Log.d(TAG, "- Job: ${job.id} - ${job.title}")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error testing rejected jobs: ${e.message}")
            }
        }
    }

    // 🚀 HELPER METHODS
    fun hasAppliedToJob(jobId: String): Boolean {
        return processedJobsRepository.isJobApplied(jobId)
    }

    fun hasRejectedJob(jobId: String): Boolean {
        return processedJobsRepository.isJobRejected(jobId)
    }

    fun isJobProcessed(jobId: String): Boolean {
        return processedJobsRepository.isJobProcessed(jobId)
    }

    private fun refreshFeaturedJobs() {
        val currentLimit = if (_featuredJobs.value.isNotEmpty()) {
            _featuredJobs.value.size
        } else {
            5
        }

        getFeaturedJobs(currentLimit)
    }

    fun refreshJobsForDistrict(district: String) {
        viewModelScope.launch {
            cache.remove("jobs_district_$district")
            cache.remove("featured_jobs_$district")

            if (isShowingRejectedJobs.value) {
                getOnlyRejectedJobs(district)
            } else {
                getJobsByDistrict(district, applyFiltersAndSort = true)
            }
        }
    }

    fun checkForNewJobs(district: String): Flow<Int> = flow {
        try {
            val allJobs = jobRepository.getJobsByLocationDirectPublic(district)
            val processedJobs = processedJobsRepository.getProcessedJobIds()
            val newJobsCount = allJobs.count { job -> !processedJobs.contains(job.id) }
            emit(newJobsCount)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for new jobs: ${e.message}")
            emit(0)
        }
    }

    private fun removeJobFromFeaturedJobs(jobId: String) {
        _featuredJobs.update { currentJobs ->
            val filteredJobs = currentJobs.filter { it.id != jobId }
            Log.d(TAG, "Removed job $jobId from display. ${currentJobs.size} -> ${filteredJobs.size} jobs")
            filteredJobs
        }
    }

    private fun clearJobCaches() {
        cache.clear()
        jobRepository.clearAllCaches()
        Log.d(TAG, "Cleared all job caches")
    }

    fun clearProcessingJobs() {
        processingJobs.clear()
        Log.d(TAG, "Cleared processing jobs")
    }

    fun isJobBeingProcessed(jobId: String): Boolean {
        return processingJobs.contains(jobId)
    }

    // 🚀 SESSION MANAGEMENT
    suspend fun startRejectedJobsSession() {
        _rejectedJobsProcessedInSession.value = emptySet()
        _currentRejectedJobsSession.value = emptyList()
        Log.d(TAG, "Started new rejected jobs session")
    }

    suspend fun endRejectedJobsSession() {
        _rejectedJobsProcessedInSession.value = emptySet()
        _currentRejectedJobsSession.value = emptyList()
        Log.d(TAG, "Ended rejected jobs session")
    }

    suspend fun setShowingRejectedJobs(showing: Boolean) {
        processedJobsRepository.setShowingRejectedJobs(showing)

        if (showing) {
            startRejectedJobsSession()
        } else {
            endRejectedJobsSession()
        }

        clearCache()
    }

    fun getRemainingRejectedJobsCount(): Int {
        val currentSession = _currentRejectedJobsSession.value
        val processed = _rejectedJobsProcessedInSession.value
        return currentSession.count { !processed.contains(it.id) }
    }

    fun isRejectedJobsSessionComplete(): Boolean {
        return isShowingRejectedJobs.value && getRemainingRejectedJobsCount() == 0
    }

    // 🚀 USER DATA LOADING
    private fun loadUserApplications() {
        val cacheKey = "user_applications"
        val lastLoaded = applicationLoadingCache[cacheKey] ?: 0L
        val currentTime = System.currentTimeMillis()

        if (currentTime - lastLoaded < CACHE_VALIDITY_MS) {
            Log.d(TAG, "Using cached application data (${currentTime - lastLoaded}ms ago)")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            performanceMonitor.track("load_user_applications", "database") {
                try {
                    Log.d(TAG, "🚀 OPTIMIZED: Loading user applications")

                    val userId = authRepository.getCurrentUserId() ?: return@track

                    val applications = withTimeoutOrNull(2000) {
                        jobRepository.supabaseClient
                            .table("applications")
                            .select(columns = Columns.list("job_id", "status", "updated_at")) {
                                filter { eq("employee_id", userId) }
                                order("updated_at", Order.DESCENDING)
                                limit(100)
                            }
                            .decodeList<Map<String, String>>()
                    } ?: emptyList()

                    val appliedJobIds = mutableSetOf<String>()
                    val rejectedJobIds = mutableSetOf<String>()

                    applications.chunked(20).forEach { chunk ->
                        chunk.forEach { app ->
                            val jobId = app["job_id"] ?: return@forEach
                            when (app["status"]?.uppercase()) {
                                "APPLIED" -> appliedJobIds.add(jobId)
                                "REJECTED" -> rejectedJobIds.add(jobId)
                            }
                        }
                    }

                    withContext(Dispatchers.Main) {
                        processedJobsRepository.updateAllJobIds(appliedJobIds, rejectedJobIds)
                        applicationLoadingCache[cacheKey] = currentTime

                        val duration = System.currentTimeMillis() - currentTime
                        Log.d(TAG, "✅ OPTIMIZED: Loaded ${appliedJobIds.size} applied, ${rejectedJobIds.size} rejected jobs in ${duration}ms")
                    }

                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error in optimized loadUserApplications: ${e.message}")
                }
            }
        }
    }

    private fun loadRejectedJobs() {
        viewModelScope.launch(Dispatchers.IO) {
            performanceMonitor.track("load_rejected_jobs", "database") {
                try {
                    Log.d(TAG, "🚀 OPTIMIZED: Starting fast rejected jobs load")
                    val startTime = System.currentTimeMillis()

                    val result = withTimeoutOrNull(1500) {
                        loadRejectedJobsOptimizedV2()
                    }

                    val duration = System.currentTimeMillis() - startTime

                    if (result == true) {
                        Log.d(TAG, "✅ OPTIMIZED: Rejected jobs loaded in ${duration}ms")
                    } else {
                        Log.w(TAG, "⚠️ Rejected jobs load timed out in ${duration}ms")
                    }

                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error in optimized loadRejectedJobs: ${e.message}")
                }
            }
        }
    }

    private suspend fun loadRejectedJobsOptimizedV2(): Boolean {
        return try {
            val userId = authRepository.getCurrentUserId() ?: return false

            val rejectedApps = jobRepository.supabaseClient
                .table("applications")
                .select(columns = Columns.list("job_id")) {
                    filter {
                        eq("employee_id", userId)
                        eq("status", "REJECTED")
                    }
                    limit(50)
                }
                .decodeList<Map<String, String>>()

            val rejectedJobIds = rejectedApps
                .mapNotNull { it["job_id"] }
                .toSet()

            withContext(Dispatchers.Main) {
                processedJobsRepository.updateRejectedJobIds(rejectedJobIds)
            }

            Log.d(TAG, "✅ Fast sync completed: ${rejectedJobIds.size} rejected jobs")
            true

        } catch (e: Exception) {
            Log.e(TAG, "❌ Fast rejected jobs sync failed: ${e.message}")
            false
        }
    }

    // 🚀 FILTERING AND SORTING HELPERS
    private fun applyFilters(jobs: List<Job>, filters: JobFilters): List<Job> {
        if (jobs.isEmpty()) return emptyList()

        return jobs.filter { job ->
            if (filters.jobType != null && job.jobType != filters.jobType) return@filter false

            val jobSalary = job.salaryRange?.let { extractSalaryAverageFast(it) } ?: 0.0
            if (jobSalary < filters.minSalary || jobSalary > filters.maxSalary) return@filter false

            if (filters.location.isNotBlank()) {
                val location = filters.location.lowercase()
                if (!job.location.lowercase().contains(location) &&
                    !job.district.lowercase().contains(location) &&
                    !job.state.lowercase().contains(location)) {
                    return@filter false
                }
            }

            if (filters.categories.isNotEmpty()) {
                if (job.jobCategory?.let { !filters.categories.contains(it) } != false) {
                    return@filter false
                }
            }

            true
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

    private fun extractSalaryAverageFast(salaryRange: String): Double {
        if (salaryRange.isBlank()) return 0.0

        val numbers = salaryRange.filter { it.isDigit() || it == '.' || it == '-' }
            .split('-')
            .mapNotNull { it.trim().toDoubleOrNull() }

        return when {
            numbers.size >= 2 -> (numbers[0] + numbers[1]) / 2
            numbers.size == 1 -> numbers[0]
            else -> 0.0
        }
    }

    private fun extractSalaryAverage(salaryRange: String?): Double {
        if (salaryRange.isNullOrEmpty()) return 0.0

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

    private fun measureAndLogPerformance(operation: String, block: () -> Unit) {
        val startTime = System.currentTimeMillis()
        block()
        val duration = System.currentTimeMillis() - startTime

        if (duration > 16) {
            Log.w(TAG, "SLOW OPERATION: $operation took ${duration}ms")
        }
    }

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

    // 🚀 PERFORMANCE MONITORING
    fun logPerformanceMetrics() {
        PerformanceUtils.PerformanceMetrics.logMetrics()
        Log.d(TAG, "Cache stats: ${cache.getStats()}")
        PerformanceUtils.MemoryMonitor.logMemoryUsage(TAG)
    }

    fun getCacheStats(): PerformanceUtils.CacheStats {
        return cache.getStats()
    }

    fun clearCache() {
        cache.clear()
        Log.d(TAG, "JobViewModel cache cleared")
    }

    private fun checkMemoryPressure() {
        if (PerformanceUtils.MemoryMonitor.isMemoryLow()) {
            Log.w(TAG, "Memory pressure detected, clearing caches")
            cache.clear()
        }
    }

    fun logPerformanceReport() {
        val report = performanceMonitor.generatePerformanceReport()

        Log.d(TAG, "=== PERFORMANCE REPORT ===")
        Log.d(TAG, "Critical Issues: ${report.criticalIssues.size}")

        report.criticalIssues.forEach { issue ->
            Log.w(TAG, "🚨 CRITICAL: $issue")
        }

        report.recommendations.forEach { recommendation ->
            Log.i(TAG, "💡 RECOMMENDATION: $recommendation")
        }

        Log.d(TAG, "Total Frame Drops: ${report.totalFrameDrops}")
        Log.d(TAG, "Slow Operations: ${report.slowOperations.size}")
        Log.d(TAG, "==========================")
    }

    override fun onCleared() {
        super.onCleared()

        Log.d(TAG, "JobViewModel clearing, cleaning up performance resources")

        processingJobs.clear()
        applicationLoadingCache.clear()

        PerformanceUtils.PerformanceMetrics.logMetrics()
        Log.d(TAG, "Final cache stats: ${cache.getStats()}")

        throttler.cleanup()
        cache.clear()
        debouncer.cancel()

        memoryCallback?.let { PerformanceUtils.MemoryMonitor.removeLowMemoryCallback(it) }
        memoryCallback = null

        Log.d(TAG, "JobViewModel cleanup completed")
    }
}