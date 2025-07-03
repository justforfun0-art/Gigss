package com.example.gigs.data.repository

import android.content.ContentValues.TAG
import android.util.Log
import com.example.gigs.data.model.ApplicationStatus
import com.example.gigs.data.model.ApplicationWithJob
import com.example.gigs.data.model.Job
import com.example.gigs.data.model.Application
import com.example.gigs.data.model.WorkSession
import com.example.gigs.data.remote.SupabaseClient
import io.github.jan.supabase.annotations.SupabaseExperimental
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Repository for managing job applications with built-in caching - FLOW ISSUES FIXED
 */
@Singleton
class ApplicationRepository @Inject constructor(
    private val supabaseClient: SupabaseClient,
    private val authRepository: AuthRepository
) {
    // In-memory cache with expiration time
    private val jobCache = ConcurrentHashMap<String, Job>()
    private val cacheExpiration = ConcurrentHashMap<String, Long>()
    private val cacheDuration = 5 * 60 * 1000L // 5 minutes

    // Track processed application IDs to prevent duplicate submissions
    private val processedApplicationIds = ConcurrentHashMap.newKeySet<String>()

    /**
     * 2. Employee views selected job (returns job + location info if status is 'selected')
     */
    suspend fun getSelectedApplicationWithLocation(applicationId: String): Result<ApplicationWithJob> = withContext(Dispatchers.IO) {
        try {
            val app = supabaseClient
                .table("applications")
                .select {
                    filter { eq("id", applicationId) }
                }
                .decodeSingleOrNull<Application>()

            if (app != null && app.status == "selected") {
                val job = supabaseClient
                    .table("jobs")
                    .select { filter { eq("id", app.jobId) } }
                    .decodeSingleOrNull<Job>()
                if (job != null) {
                    Result.success(
                        ApplicationWithJob(
                            id = app.id,
                            jobId = app.jobId,
                            employeeId = app.employeeId,
                            status = ApplicationStatus.valueOf(app.status.uppercase()),
                            appliedAt = app.createdAt,
                            updatedAt = app.updatedAt,
                            job = job
                        )
                    )
                } else Result.failure(Exception("Job not found"))
            } else {
                Result.failure(Exception("Application not selected"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getSelectedApplicationsForEmployer(employerId: String, limit: Int = 10): List<ApplicationWithJob> {
        return try {
            Log.d(TAG, "🔍 Getting SELECTED applications for employer dashboard")

            // First get employer's jobs
            val employerJobs = supabaseClient
                .table("jobs")
                .select {
                    filter { eq("employer_id", employerId) }
                    order("created_at", Order.DESCENDING)
                    limit(50) // Limit jobs to check
                }
                .decodeList<Job>()

            if (employerJobs.isEmpty()) {
                Log.d(TAG, "No jobs found for employer $employerId")
                return emptyList()
            }

            val selectedApplications = mutableListOf<ApplicationWithJob>()

            // Get SELECTED applications for each job
            employerJobs.forEach { job ->
                try {
                    val applications = supabaseClient
                        .table("applications")
                        .select {
                            filter {
                                eq("job_id", job.id)
                                eq("status", "SELECTED") // 🚀 ONLY SELECTED STATUS
                            }
                            order("updated_at", Order.DESCENDING)
                            limit(20) // Limit per job
                        }
                        .decodeList<Application>()

                    // Convert to ApplicationWithJob
                    applications.forEach { app ->
                        selectedApplications.add(
                            ApplicationWithJob(
                                id = app.id,
                                jobId = app.jobId,
                                employeeId = app.employeeId,
                                status = ApplicationStatus.SELECTED,
                                appliedAt = app.appliedAt ?: app.createdAt,
                                updatedAt = app.updatedAt,
                                job = job
                            )
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error getting SELECTED applications for job ${job.id}: ${e.message}")
                }
            }

            // Sort by update time and limit
            val sortedSelectedApplications = selectedApplications
                .sortedByDescending { it.updatedAt }
                .take(limit)

            Log.d(TAG, "✅ Found ${sortedSelectedApplications.size} SELECTED applications for employer")
            sortedSelectedApplications

        } catch (e: Exception) {
            Log.e(TAG, "Error getting selected applications for employer: ${e.message}")
            emptyList()
        }
    }
    /**
     * 3. Employee starts work:
     * - Enters OTP (must match and not expired)
     * - Sets status "work_in_progress" and records work_start_time
     */

    /**
     * 🚀 FIXED: Get the current user's applications with job details - NO MORE FLOW ISSUES
     */
    suspend fun getMyApplications(limit: Int = 0): Flow<Result<List<ApplicationWithJob>>> = flow {
        try {
            val applications = getMyApplicationsDirect(limit)
            emit(Result.success(applications))
        } catch (e: Exception) {
            throw e
        }
    }.catch { e ->
        Log.e(TAG, "Flow error in getMyApplications: ${e.message}")
        emit(Result.failure(e))
    }

    /**
     * 🚀 NEW: Direct method for getting applications - NO FLOW COMPLEXITY
     */
    internal suspend fun getMyApplicationsDirect(limit: Int = 0): List<ApplicationWithJob> {
        return try {
            withContext(Dispatchers.IO) {
                withTimeoutOrNull(3000) {
                    Log.d(TAG, "Fetching applications for current user with work sessions")
                    val userId = authRepository.getCurrentUserId()
                        ?: throw Exception("User not authenticated")

                    // Direct API call for applications with explicit column selection
                    val applications = supabaseClient
                        .table("applications")
                        .select(columns = Columns.list(
                            "id", "job_id", "employee_id", "status",
                            "applied_at", "created_at", "updated_at"
                        )) {
                            filter { eq("employee_id", userId) }
                            // Order by applied_at if available, fallback to created_at
                            order("applied_at", Order.DESCENDING)
                            if (limit > 0) {
                                limit(limit.toLong())
                            } else {
                                limit(50)
                            }
                        }
                        .decodeList<Application>()

                    Log.d(TAG, "Found ${applications.size} applications")

                    // 🚀 ENHANCED: Log status distribution for debugging
                    val statusCounts = applications.groupBy { it.status }.mapValues { it.value.size }
                    Log.d(TAG, "Application status distribution: $statusCounts")

                    if (applications.isEmpty()) return@withTimeoutOrNull emptyList()

                    // Get unique job IDs
                    val jobIds = applications.map { it.jobId }.distinct()
                    Log.d(TAG, "Need to fetch ${jobIds.size} unique jobs")

                    // Batch fetch jobs if reasonable number
                    val jobs = if (jobIds.size <= 10) {
                        Log.d(TAG, "Fetching ${jobIds.size} jobs in a single batch query")

                        try {
                            val inClause = jobIds.joinToString(",") { "\"$it\"" } // Proper escaping
                            val fetchedJobs = supabaseClient
                                .table("jobs")
                                .select {
                                    filter {
                                        filter("id", FilterOperator.IN, "($inClause)")
                                    }
                                }
                                .decodeList<Job>()

                            Log.d(TAG, "Fetched batch of ${fetchedJobs.size} jobs")

                            // Update cache
                            val currentTime = System.currentTimeMillis()
                            fetchedJobs.forEach { job ->
                                jobCache[job.id] = job
                                cacheExpiration[job.id] = currentTime + cacheDuration
                            }

                            fetchedJobs
                        } catch (e: Exception) {
                            Log.w(TAG, "Batch fetch failed, trying individual requests: ${e.message}")

                            // Fallback to individual requests
                            val individualJobs = mutableListOf<Job>()
                            jobIds.take(5).forEach { jobId ->
                                try {
                                    val job = supabaseClient
                                        .table("jobs")
                                        .select { filter { eq("id", jobId) } }
                                        .decodeSingleOrNull<Job>()
                                    job?.let { individualJobs.add(it) }
                                } catch (e: Exception) {
                                    Log.e(TAG, "Failed to fetch job $jobId: ${e.message}")
                                }
                            }
                            individualJobs
                        }
                    } else {
                        Log.w(TAG, "Too many jobs to fetch (${jobIds.size}), returning applications without job details")
                        emptyList()
                    }

                    // 🚀 NEW: Fetch work sessions for these applications
                    val applicationIds = applications.map { it.id }
                    val workSessions = if (applicationIds.isNotEmpty()) {
                        try {
                            Log.d(TAG, "Fetching work sessions for ${applicationIds.size} applications")
                            val sessionInClause = applicationIds.joinToString(",") { "\"$it\"" }
                            val fetchedSessions = supabaseClient
                                .table("work_sessions")
                                .select {
                                    filter {
                                        filter("application_id", FilterOperator.IN, "($sessionInClause)")
                                    }
                                    order("created_at", Order.DESCENDING)
                                }
                                .decodeList<WorkSession>()

                            Log.d(TAG, "Fetched ${fetchedSessions.size} work sessions")
                            fetchedSessions
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to fetch work sessions: ${e.message}")
                            emptyList<WorkSession>()
                        }
                    } else {
                        emptyList()
                    }

                    // Create maps for quick lookup
                    val jobMap = jobs.associateBy { it.id }
                    val workSessionMap = workSessions.groupBy { it.applicationId }
                        .mapValues { it.value.maxByOrNull { session -> session.createdAt ?: "" } }

                    Log.d(TAG, "Work sessions found for ${workSessionMap.size} applications")

                    // Convert to ApplicationWithJob
                    applications.mapNotNull { app ->
                        jobMap[app.jobId]?.let { job ->
                            val workSession = workSessionMap[app.id]

                            ApplicationWithJob(
                                id = app.id,
                                jobId = app.jobId,
                                employeeId = app.employeeId,
                                status = try {
                                    ApplicationStatus.valueOf(app.status.uppercase())
                                } catch (e: Exception) {
                                    // 🚀 HANDLE: Map NOT_INTERESTED to appropriate enum if needed
                                    when (app.status.uppercase()) {
                                        "NOT_INTERESTED" -> ApplicationStatus.NOT_INTERESTED
                                        else -> ApplicationStatus.APPLIED
                                    }
                                },
                                // 🚀 FIX: Use appliedAt if available, fallback to createdAt
                                appliedAt = app.appliedAt ?: app.createdAt,
                                updatedAt = app.updatedAt,
                                job = job,
                                workSession = workSession // 🚀 NEW: Include work session data
                            )
                        }
                    }
                } ?: emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting applications with work sessions: ${e.message}")
            emptyList()
        }
    }

    private suspend fun fetchJobsBatch(jobIds: List<String>): List<Job> {
        return try {
            val inClause = jobIds.joinToString(",", prefix = "(", postfix = ")") { "\"$it\"" }
            supabaseClient
                .table("jobs")
                .select {
                    filter {
                        filter("id", FilterOperator.IN, inClause)
                    }
                }
                .decodeList<Job>()
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching jobs batch: ${e.message}")
            emptyList()
        }
    }

    /**
     * 🚀 FIXED: Check if the current user has applied to a specific job
     */
    suspend fun hasUserAppliedToJob(jobId: String): Flow<Result<Boolean>> = flow {
        try {
            val hasApplied = hasUserAppliedToJobDirect(jobId)
            emit(Result.success(hasApplied))
        } catch (e: Exception) {
            throw e
        }
    }.catch { e ->
        Log.e(TAG, "Flow error in hasUserAppliedToJob: ${e.message}")
        emit(Result.failure(e))
    }

    /**
     * 🚀 NEW: Direct method to check if user applied
     */
    private suspend fun hasUserAppliedToJobDirect(jobId: String): Boolean {
        return try {
            val userId = authRepository.getCurrentUserId() ?: return false

            // First check if we've processed this application recently
            if (processedApplicationIds.contains(jobId)) {
                return true
            }

            val applications = supabaseClient
                .table("applications")
                .select {
                    filter {
                        eq("job_id", jobId)
                        eq("employee_id", userId)
                        eq("status", "APPLIED")
                    }
                }
                .decodeList<Application>()

            val hasApplied = applications.isNotEmpty()

            // Cache the result if applied
            if (hasApplied) {
                processedApplicationIds.add(jobId)
            }

            hasApplied
        } catch (e: Exception) {
            Log.e(TAG, "Error checking if user applied directly: ${e.message}")
            false
        }
    }

    /**
     * 🚀 FIXED: Get applications for a specific job
     */
    suspend fun getApplicationsForJob(jobId: String): Flow<Result<List<ApplicationWithJob>>> = flow {
        try {
            val applications = getApplicationsForJobDirect(jobId)
            emit(Result.success(applications))
        } catch (e: Exception) {
            throw e
        }
    }.catch { e ->
        Log.e(TAG, "Flow error in getApplicationsForJob: ${e.message}")
        emit(Result.failure(e))
    }

    /**
     * 🚀 NEW: Direct method for getting job applications
     */
    suspend fun getApplicationsForJobDirect(jobId: String): List<ApplicationWithJob> {
        return try {
            val userId = authRepository.getCurrentUserId() ?: throw Exception("User not authenticated")

            // Get job details
            val job = getJobByIdDirect(jobId) ?: throw Exception("Job not found")

            if (job.employerId != userId) {
                throw Exception("You can only view applications for your own jobs")
            }

            // Get applications for this job, excluding NOT_INTERESTED
            val applications = supabaseClient
                .table("applications")
                .select {
                    filter {
                        eq("job_id", jobId)
                        neq("status", "NOT_INTERESTED")
                    }
                    order("applied_at", Order.ASCENDING)
                }
                .decodeList<Application>()

            Log.d(TAG, "Found ${applications.size} ACTUAL applications for job $jobId (excluded NOT_INTERESTED)")

            // Convert to ApplicationWithJob
            applications.map { application ->
                ApplicationWithJob(
                    id = application.id,
                    jobId = application.jobId,
                    employeeId = application.employeeId,
                    status = try {
                        ApplicationStatus.valueOf(application.status.uppercase())
                    } catch (e: Exception) {
                        ApplicationStatus.APPLIED
                    },
                    appliedAt = application.createdAt,
                    updatedAt = application.updatedAt,
                    job = job
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting applications for job directly: ${e.message}")
            emptyList()
        }
    }


    /**
     * 🚀 NEW: Get applications for employer dashboard - EXCLUDES NOT_INTERESTED
     */
    suspend fun getApplicationsForEmployerDashboard(
        employerId: String,
        limit: Int = 5,
        statusFilter: String? = null // 🚀 NEW: Optional status filter
    ): List<ApplicationWithJob> {
        return try {
            Log.d(TAG, "🔍 Getting applications for employer dashboard with filter: $statusFilter")

            // First get employer's jobs
            val employerJobs = supabaseClient
                .table("jobs")
                .select {
                    filter { eq("employer_id", employerId) }
                    order("created_at", Order.DESCENDING)
                    limit(20) // Limit jobs to check
                }
                .decodeList<Job>()

            if (employerJobs.isEmpty()) {
                Log.d(TAG, "No jobs found for employer $employerId")
                return emptyList()
            }

            val allApplications = mutableListOf<ApplicationWithJob>()

            // Get applications for each job with optional status filter
            employerJobs.forEach { job ->
                try {
                    val applications = supabaseClient
                        .table("applications")
                        .select {
                            filter {
                                eq("job_id", job.id)
                                // 🚀 CRITICAL: Always exclude NOT_INTERESTED
                                neq("status", "NOT_INTERESTED")

                                // 🚀 NEW: Apply status filter if provided
                                statusFilter?.let { status ->
                                    eq("status", status)
                                }
                            }
                            order("updated_at", Order.DESCENDING)
                            limit(10) // Limit per job
                        }
                        .decodeList<Application>()

                    // Convert to ApplicationWithJob
                    applications.forEach { app ->
                        allApplications.add(
                            ApplicationWithJob(
                                id = app.id,
                                jobId = app.jobId,
                                employeeId = app.employeeId,
                                status = try {
                                    ApplicationStatus.valueOf(app.status.uppercase())
                                } catch (e: Exception) {
                                    ApplicationStatus.APPLIED
                                },
                                appliedAt = app.appliedAt ?: app.createdAt,
                                updatedAt = app.updatedAt,
                                job = job
                            )
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error getting applications for job ${job.id}: ${e.message}")
                }
            }

            // Sort by application date and limit
            val sortedApplications = allApplications
                .sortedByDescending { it.updatedAt }
                .take(limit)

            Log.d(TAG, "✅ Employer dashboard: Found ${sortedApplications.size} applications with filter '$statusFilter'")

            // 🚀 DEBUG: Log status breakdown for employers
            val statusBreakdown = sortedApplications.groupBy { it.status }.mapValues { it.value.size }
            Log.d(TAG, "Employer dashboard status breakdown: $statusBreakdown")

            sortedApplications

        } catch (e: Exception) {
            Log.e(TAG, "Error getting applications for employer dashboard: ${e.message}")
            emptyList()
        }
    }

    /**
     * 🚀 NEW: Get applications by status for employer
     */
    suspend fun getApplicationsByStatusForEmployer(
        employerId: String,
        status: String,
        limit: Int = 50
    ): Flow<Result<List<ApplicationWithJob>>> = flow {
        try {
            Log.d(TAG, "🔍 Getting applications with status '$status' for employer")

            val applications = getApplicationsForEmployerDashboard(
                employerId = employerId,
                limit = limit,
                statusFilter = status
            )

            emit(Result.success(applications))
        } catch (e: Exception) {
            Log.e(TAG, "Error getting applications by status: ${e.message}")
            emit(Result.failure(e))
        }
    }
    /**
     * 🚀 FIXED: Update application status for employers (using application ID)
     */
    suspend fun updateEmployerApplicationStatus(
        applicationId: String,
        newStatus: String
    ): Flow<Result<Boolean>> = flow {
        try {
            val success = updateEmployerApplicationStatusDirect(applicationId, newStatus)
            emit(Result.success(success))
        } catch (e: Exception) {
            throw e
        }
    }.catch { e ->
        Log.e(TAG, "Flow error in updateEmployerApplicationStatus: ${e.message}")
        emit(Result.failure(e))
    }

    /**
     * 🚀 NEW: Direct method for updating employer application status
     */
    private suspend fun updateEmployerApplicationStatusDirect(
        applicationId: String,
        newStatus: String
    ): Boolean {
        return try {
            val userId = authRepository.getCurrentUserId() ?: throw Exception("User not authenticated")

            // Get the application
            val application = supabaseClient
                .table("applications")
                .select {
                    filter { eq("id", applicationId) }
                }
                .decodeSingleOrNull<Application>() ?: throw Exception("Application not found")

            // Get job to verify ownership
            val job = getJobByIdDirect(application.jobId)
            if (job?.employerId != userId) {
                throw Exception("You can only update applications for your own jobs")
            }

            // Update application status
            supabaseClient
                .table("applications")
                .update(mapOf(
                    "status" to newStatus,
                    "updated_at" to java.time.Instant.now().toString()
                )) {
                    filter { eq("id", applicationId) }
                }

            true
        } catch (e: Exception) {
            Log.e(TAG, "Error updating employer application status directly: ${e.message}")
            false
        }
    }

    /**
     * 🚀 FIXED: Update application status for employees (using job ID) - NO MORE FLOW COMPLEXITY
     */
    suspend fun updateEmployeeApplicationStatus(jobId: String, status: String): Flow<Result<Unit>> = flow {
        try {
            val result = updateEmployeeApplicationStatusDirect(jobId, status)
            emit(result)
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }

    private val processingOperations = ConcurrentHashMap<String, Long>()
    private val operationTimeout = 5000L // 5 seconds

    /**
     * 🚀 CRITICAL FIX: Centralized status validation
     */

    /**
     * 🚀 CRITICAL FIX: Check if operation is already in progress
     */

    /**
     * 🚀 NEW: Direct status update method - NO FLOW COLLECTIONS
     */

    // Add these methods to your ApplicationRepository.kt for the employer side

    /**
     * 🚀 NEW: Employer accepts application and moves status from SELECTED to HIRED
     * This happens when employee clicks "Accept Job" button
     */
    suspend fun markApplicationAsAccepted(applicationId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val userId = authRepository.getCurrentUserId() ?: return@withContext Result.failure(Exception("Not authenticated"))

            // Get application details
            val application = supabaseClient
                .table("applications")
                .select {
                    filter { eq("id", applicationId) }
                }
                .decodeSingleOrNull<Application>() ?: return@withContext Result.failure(Exception("Application not found"))

            // Get job details to verify employer ownership
            val job = supabaseClient
                .table("jobs")
                .select {
                    filter { eq("id", application.jobId) }
                }
                .decodeSingleOrNull<Job>() ?: return@withContext Result.failure(Exception("Job not found"))

            if (job.employerId != userId) {
                return@withContext Result.failure(Exception("Unauthorized"))
            }

            val now = Instant.now().toString()

            // Update application status to HIRED (Job Accepted)
            supabaseClient.table("applications")
                .update(mapOf(
                    "status" to "HIRED",
                    "updated_at" to now
                )) {
                    filter { eq("id", applicationId) }
                }

            Log.d(TAG, "✅ Application $applicationId marked as accepted (HIRED status)")
            Result.success(true)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error marking application as accepted: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * 🚀 ENHANCED: Check for date/time conflicts when accepting jobs
     */
    suspend fun checkJobDateConflict(employeeId: String, newJobId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            // Get the new job details
            val newJob = supabaseClient
                .table("jobs")
                .select {
                    filter { eq("id", newJobId) }
                }
                .decodeSingleOrNull<Job>() ?: return@withContext Result.failure(Exception("Job not found"))

            // Get all accepted/hired jobs for this employee
            val existingAcceptedJobs = supabaseClient
                .table("applications")
                .select {
                    filter {
                        eq("employee_id", employeeId)
                        or {
                            eq("status", "HIRED")
                            eq("status", "WORK_IN_PROGRESS")
                        }
                    }
                }
                .decodeList<Application>()

            // For each existing job, check if there's a date/time conflict
            for (existingApp in existingAcceptedJobs) {
                val existingJob = supabaseClient
                    .table("jobs")
                    .select {
                        filter { eq("id", existingApp.jobId) }
                    }
                    .decodeSingleOrNull<Job>()

                if (existingJob != null) {
                    // Here you would implement your actual date/time conflict logic
                    // For now, we'll do a simple check based on job creation dates
                    // You should implement proper date/time parsing and comparison

                    // Example: If jobs are on the same day (simplified)
                    val newJobDate = newJob.createdAt?.substring(0, 10) // Get date part
                    val existingJobDate = existingJob.createdAt?.substring(0, 10)

                    if (newJobDate == existingJobDate) {
                        Log.w(TAG, "⚠️ Date conflict detected: New job $newJobId conflicts with existing job ${existingJob.id}")
                        return@withContext Result.success(true) // Conflict found
                    }
                }
            }

            Log.d(TAG, "✅ No date conflicts found for employee $employeeId and job $newJobId")
            Result.success(false) // No conflicts

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error checking job date conflict: ${e.message}")
            Result.failure(e)
        }
    }

    // 🚀 UPDATE: Enhanced updateEmployeeApplicationStatus to handle job acceptance
    @OptIn(SupabaseExperimental::class)
    private suspend fun updateEmployeeApplicationStatusDirect(
        jobId: String,
        status: String
    ): Result<Unit> {
        val operationKey = "update_${jobId}_${status}"

        // 🚀 CRITICAL: Enhanced duplicate prevention
        if (isOperationInProgress(operationKey)) {
            Log.w(TAG, "🚫 Update operation already in progress for $operationKey")
            return Result.success(Unit)
        }

        processingOperations[operationKey] = System.currentTimeMillis()

        return try {
            val userId = authRepository.getCurrentUserId()
                ?: return Result.failure(Exception("User not authenticated"))

            // 🚀 CRITICAL: Always validate status before using
            val validatedStatus = validateAndNormalizeStatus(status)
            val timestamp = java.time.Instant.now().toString()

            Log.d(TAG, "⚡ ULTRA-FAST: Updating job=$jobId, status='$status'->'$validatedStatus', user=$userId")

            // 🚀 UPDATED: Special handling for job acceptance (ACCEPTED status, not HIRED)
            if (validatedStatus == "ACCEPTED") {
                // Check for date conflicts before accepting
                val conflictResult = checkJobDateConflict(userId, jobId)
                if (conflictResult.isSuccess && conflictResult.getOrNull() == true) {
                    return Result.failure(Exception("Cannot accept job: You have another job scheduled at the same time"))
                }
            }

            // 🚀 STEP 1: Check for existing application (fast query)
            val existingApplications = withTimeoutOrNull(1000) {
                supabaseClient
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

            if (existingApplications.isNotEmpty()) {
                // 🚀 STEP 2A: Update existing application
                val existingId = existingApplications.first()["id"] ?: ""
                val currentStatus = existingApplications.first()["status"] ?: ""

                // 🚀 SEMANTIC CHECK: Log the transition for debugging
                Log.d(TAG, "Status transition for job $jobId: '$currentStatus' -> '$validatedStatus'")

                supabaseClient
                    .table("applications")
                    .update(mapOf(
                        "status" to validatedStatus,
                        "updated_at" to timestamp
                    )) {
                        filter { eq("id", existingId) }
                    }

                Log.d(TAG, "✅ ULTRA-FAST: Updated existing application $existingId to $validatedStatus")

                // 🚀 UPDATED: If accepting job (ACCEPTED), generate OTP automatically
                if (validatedStatus == "ACCEPTED") {
                    try {
                        val otpResult = acceptApplicationAndGenerateOtp(existingId)
                        if (otpResult.isSuccess) {
                            Log.d(TAG, "✅ OTP generated automatically for accepted job")
                        } else {
                            Log.w(TAG, "⚠️ Job accepted but OTP generation failed: ${otpResult.exceptionOrNull()?.message}")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "⚠️ Job accepted but OTP generation failed: ${e.message}")
                    }
                }
            } else {
                // 🚀 STEP 2B: Create new application
                val applicationData = mapOf(
                    "job_id" to jobId,
                    "employee_id" to userId,
                    "status" to validatedStatus,
                    "applied_at" to timestamp,
                    "created_at" to timestamp,
                    "updated_at" to timestamp
                )

                val insertResult = supabaseClient
                    .table("applications")
                    .insert(applicationData) {
                        headers["Prefer"] = "return=representation"
                    }
                    .decodeList<Application>()

                Log.d(TAG, "✅ ULTRA-FAST: Created new application with status $validatedStatus")

                // 🚀 UPDATED: If accepting job (ACCEPTED), generate OTP automatically
                if (validatedStatus == "ACCEPTED" && insertResult.isNotEmpty()) {
                    try {
                        val newApplicationId = insertResult.first().id
                        val otpResult = acceptApplicationAndGenerateOtp(newApplicationId)
                        if (otpResult.isSuccess) {
                            Log.d(TAG, "✅ OTP generated automatically for new accepted job")
                        } else {
                            Log.w(TAG, "⚠️ Job accepted but OTP generation failed: ${otpResult.exceptionOrNull()?.message}")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "⚠️ Job accepted but OTP generation failed: ${e.message}")
                    }
                }
            }

            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Ultra-fast status update failed for job $jobId: ${e.message}")
            Result.failure(e)
        } finally {
            processingOperations.remove(operationKey)
        }
    }


    // 🚀 ALSO UPDATE: The isOperationInProgress method for better performance
    private fun isOperationInProgress(operationKey: String): Boolean {
        val currentTime = System.currentTimeMillis()
        val startTime = processingOperations[operationKey]

        return if (startTime != null) {
            if (currentTime - startTime > 3000L) { // Reduced from 5000L to 3000L
                // Operation timeout, remove and allow retry
                processingOperations.remove(operationKey)
                Log.w(TAG, "⚠️ Operation $operationKey timed out after ${currentTime - startTime}ms, allowing retry")
                false
            } else {
                true // Still in progress
            }
        } else {
            false
        }
    }

    // 🚀 ENHANCED: Improved status validation with better error handling
    // 🚀 UPDATE the validateAndNormalizeStatus function in ApplicationRepository.kt:

    private fun validateAndNormalizeStatus(status: String): String {
        val normalized = status.trim().uppercase()

        return when (normalized) {
            "APPLIED" -> "APPLIED"
            "PENDING" -> "PENDING"
            "UNDER_REVIEW" -> "UNDER_REVIEW"
            "SELECTED" -> "SELECTED"
            "ACCEPTED" -> "ACCEPTED"  // 🚀 Keep ACCEPTED as valid
            "DECLINED" -> "DECLINED"

            // 🚀 USER REJECTIONS: When user swipes left or marks as not interested
            "NOT_INTERESTED" -> "NOT_INTERESTED"
            "SKIP", "PASS" -> {
                Log.d(TAG, "ℹ️ Converting UI status '$status' to NOT_INTERESTED")
                "NOT_INTERESTED"
            }

            // 🚀 EMPLOYER REJECTIONS: When employer rejects the user's application
            "REJECTED" -> "REJECTED"

            // 🚀 WORK FLOW STATUSES
            "WORK_IN_PROGRESS" -> "WORK_IN_PROGRESS"
            "COMPLETED" -> "COMPLETED"

            // 🚀 CRITICAL: Remove HIRED - Convert to ACCEPTED
            "HIRED" -> {
                Log.w(TAG, "⚠️ Converting deprecated status 'HIRED' to 'ACCEPTED'")
                "ACCEPTED"
            }

            // 🚀 CRITICAL: Handle legacy/invalid statuses
            "FINISHED", "DONE" -> {
                Log.w(TAG, "⚠️ Converting legacy status '$status' to COMPLETED")
                "COMPLETED"
            }
            else -> {
                Log.w(TAG, "⚠️ Unknown status '$status' defaulted to APPLIED")
                "APPLIED" // Default to APPLIED instead of NOT_INTERESTED
            }
        }
    }

    // 🚀 NEW: Add periodic cleanup method (call this occasionally to prevent memory leaks)

    /**
     * Update application status - compatibility method for ViewModels
     */
    suspend fun updateApplicationStatus(applicationId: String, newStatus: String): Flow<Result<Boolean>> =
        updateEmployerApplicationStatus(applicationId, newStatus)

    /**
     * 🚀 COMPLETELY FIXED: Apply for a job - NO MORE FLOW ISSUES (This was line 611!)
     */
    @OptIn(SupabaseExperimental::class)
    suspend fun applyForJob(jobId: String): Flow<Result<ApplicationWithJob>> = flow {
        try {
            val result = applyForJobDirect(jobId)
            if (result != null) {
                emit(Result.success(result))
            } else {
                emit(Result.failure(Exception("Failed to apply for job")))
            }
        } catch (e: Exception) {
            // Don't emit here - let Flow.catch handle it
            throw e
        }
    }.catch { e ->
        // Proper Flow.catch usage
        Log.e(TAG, "Flow error in applyForJob: ${e.message}")
        emit(Result.failure(e))
    }

    /**
     * 🚀 NEW: Direct application method - NO COMPLEX FLOWS
     */
    @OptIn(SupabaseExperimental::class)
    private suspend fun applyForJobDirect(jobId: String): ApplicationWithJob? {
        val operationKey = "apply_$jobId"

        // 🚀 CRITICAL: Prevent duplicate applications
        if (isOperationInProgress(operationKey)) {
            Log.w(TAG, "Apply operation already in progress for job $jobId")
            return null
        }

        processingOperations[operationKey] = System.currentTimeMillis()

        return try {
            withContext(Dispatchers.IO) {
                withTimeoutOrNull(5000) {
                    val userId = authRepository.getCurrentUserId()
                        ?: throw Exception("User not authenticated")

                    Log.d(TAG, "Applying for job: $jobId by user: $userId")

                    // Step 1: Check for existing application
                    val existingApplications = supabaseClient
                        .table("applications")
                        .select {
                            filter {
                                eq("job_id", jobId)
                                eq("employee_id", userId)
                            }
                        }
                        .decodeList<Map<String, String>>()

                    Log.d(TAG, "Found ${existingApplications.size} existing applications")

                    // Get job details
                    val jobObject = getJobByIdDirect(jobId)
                        ?: throw Exception("Job not found")

                    val timestamp = java.time.Instant.now().toString()
                    val validatedStatus = validateAndNormalizeStatus("APPLIED")

                    if (existingApplications.isNotEmpty()) {
                        val existingApp = existingApplications.first()
                        val currentStatus = existingApp["status"] ?: ""

                        if (currentStatus.equals("APPLIED", ignoreCase = true)) {
                            Log.d(TAG, "Application already has APPLIED status")
                            return@withTimeoutOrNull ApplicationWithJob(
                                id = existingApp["id"] ?: "",
                                jobId = jobId,
                                employeeId = userId,
                                status = ApplicationStatus.APPLIED,
                                appliedAt = existingApp["applied_at"],
                                updatedAt = existingApp["updated_at"],
                                job = jobObject
                            )
                        }

                        // Update existing application
                        Log.d(TAG, "Updating existing application to APPLIED")

                        supabaseClient
                            .table("applications")
                            .update(mapOf(
                                "status" to validatedStatus, // Use validated status
                                "updated_at" to timestamp
                            )) {
                                filter {
                                    eq("id", existingApp["id"] ?: "")
                                }
                            }

                        return@withTimeoutOrNull ApplicationWithJob(
                            id = existingApp["id"] ?: "",
                            jobId = jobId,
                            employeeId = userId,
                            status = ApplicationStatus.APPLIED,
                            appliedAt = existingApp["applied_at"],
                            updatedAt = timestamp,
                            job = jobObject
                        )
                    }

                    // Step 3: Create new application
                    Log.d(TAG, "Creating new application for job: $jobId")

                    val applicationData = mapOf(
                        "job_id" to jobId,
                        "employee_id" to userId,
                        "status" to validatedStatus, // Use validated status
                        "applied_at" to timestamp,
                        "created_at" to timestamp,
                        "updated_at" to timestamp
                    )

                    supabaseClient
                        .table("applications")
                        .insert(applicationData) {
                            headers["Prefer"] = "return=minimal"
                        }

                    ApplicationWithJob(
                        id = "",
                        jobId = jobId,
                        employeeId = userId,
                        status = ApplicationStatus.APPLIED,
                        appliedAt = timestamp,
                        updatedAt = timestamp,
                        job = jobObject
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error applying for job: ${e.message}")
            null
        } finally {
            // 🚀 CRITICAL: Always cleanup
            processingOperations.remove(operationKey)
        }
    }

    /**
     * 🚀 NEW: Clear stale operations (call this periodically)
     */
    fun clearStaleOperations() {
        val currentTime = System.currentTimeMillis()
        val staleKeys = processingOperations.filter { (_, startTime) ->
            currentTime - startTime > operationTimeout
        }.keys

        staleKeys.forEach { processingOperations.remove(it) }

        if (staleKeys.isNotEmpty()) {
            Log.d(TAG, "Cleared ${staleKeys.size} stale operations")
        }
    }

    /**
     * 🚀 FIXED: Helper method to get job by ID - NO FLOW COMPLEXITY
     */
    private suspend fun getJobByIdDirect(jobId: String): Job? {
        return try {
            // Check cache first
            if (jobCache.containsKey(jobId) && System.currentTimeMillis() <= (cacheExpiration[jobId] ?: 0L)) {
                return jobCache[jobId]
            }

            // Fetch from database with timeout
            val job = withTimeoutOrNull(2000) {
                supabaseClient
                    .table("jobs")
                    .select {
                        filter { eq("id", jobId) }
                    }
                    .decodeSingleOrNull<Job>()
            }

            // Cache the job if found
            if (job != null) {
                jobCache[job.id] = job
                cacheExpiration[job.id] = System.currentTimeMillis() + cacheDuration
            }

            job
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching job by ID directly: ${e.message}")
            null
        }
    }

    /**
     * 🚀 NEW: Helper method to apply for job (returns boolean)
     */
    private suspend fun applyForJobDirectSimple(jobId: String): Boolean {
        return try {
            val result = applyForJobDirect(jobId)
            result != null
        } catch (e: Exception) {
            Log.e(TAG, "Error in simple apply for job: ${e.message}")
            false
        }
    }

    /**
     * Clear all caches
     */
    fun clearCache() {
        jobCache.clear()
        cacheExpiration.clear()
        processedApplicationIds.clear()
        Log.d(TAG, "Cache cleared")
    }

    /**
     * Clear processed application IDs only
     */
    fun clearProcessedApplications() {
        processedApplicationIds.clear()
        Log.d(TAG, "Processed applications cache cleared")
    }

    /**
     * 1. Employer accepts application and creates work session with OTP
     */
    suspend fun acceptApplicationWithOtp(applicationId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val userId = authRepository.getCurrentUserId() ?: return@withContext Result.failure(Exception("Not authenticated"))

            // Get application details
            val application = supabaseClient
                .table("applications")
                .select {
                    filter { eq("id", applicationId) }
                }
                .decodeSingleOrNull<Application>() ?: return@withContext Result.failure(Exception("Application not found"))

            // Get job details to verify employer ownership
            val job = supabaseClient
                .table("jobs")
                .select {
                    filter { eq("id", application.jobId) }
                }
                .decodeSingleOrNull<Job>() ?: return@withContext Result.failure(Exception("Job not found"))

            if (job.employerId != userId) {
                return@withContext Result.failure(Exception("Unauthorized"))
            }

            // Generate OTP and expiry
            val otp = generateOtp()
            val expiry = Instant.now().plusSeconds(TimeUnit.MINUTES.toSeconds(30)).toString()
            val now = Instant.now().toString()

            // Update application status to HIRED
            supabaseClient.table("applications")
                .update(mapOf(
                    "status" to "HIRED",
                    "updated_at" to now
                )) {
                    filter { eq("id", applicationId) }
                }

            // Create work session
            supabaseClient.table("work_sessions")
                .insert(mapOf(
                    "application_id" to applicationId,
                    "job_id" to application.jobId,
                    "employee_id" to application.employeeId,
                    "employer_id" to userId,
                    "otp" to otp,
                    "otp_expiry" to expiry,
                    "status" to "OTP_GENERATED",
                    "created_at" to now,
                    "updated_at" to now
                ))

            Log.d(TAG, "Generated OTP $otp for application $applicationId")
            // TODO: Send OTP to employer (SMS/email/in-app notification)

            Result.success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Error accepting application with OTP: ${e.message}")
            Result.failure(e)
        }
    }


    /**
     * 2. Employee starts work with OTP
     */
    /**
     * 🚀 ENHANCED: Employee starts work with OTP - with better error handling
     */
// First, let's add comprehensive debugging to the OTP verification process

    /**
     * Enhanced startWorkWithOtp with detailed debugging
     */
    // Add this enhanced method to ApplicationRepository.kt

    /**
     * Enhanced startWorkWithOtp with better error handling and expired OTP management
     */
    // 🚀 ENHANCED DEBUG VERSION: Add this to your ApplicationRepository.kt

    // 🚀 ENHANCED startWorkWithOtp method for ApplicationRepository.kt

    /**
     * 🚀 COMPLETE startWorkWithOtp function for ApplicationRepository.kt
     * This function handles OTP verification and starts work sessions
     */
    suspend fun startWorkWithOtp(applicationId: String, enteredOtp: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val userId = authRepository.getCurrentUserId()
            Log.d(TAG, "🔍 === ENHANCED OTP VERIFICATION START ===")
            Log.d(TAG, "🔍 Application ID: $applicationId")
            Log.d(TAG, "🔍 Employee ID: $userId")
            Log.d(TAG, "🔍 Entered OTP: '$enteredOtp'")

            if (userId == null) {
                Log.e(TAG, "❌ User not authenticated")
                return@withContext Result.failure(Exception("Not authenticated"))
            }

            // 🚀 STEP 1: Get work session with enhanced error handling
            Log.d(TAG, "🔍 === STEP 1: FETCHING WORK SESSION ===")

            val workSessionsForApp = try {
                supabaseClient
                    .table("work_sessions")
                    .select {
                        filter {
                            eq("application_id", applicationId)
                            eq("employee_id", userId)
                        }
                        order("created_at", Order.DESCENDING)
                    }
                    .decodeList<WorkSession>()
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error querying work sessions: ${e.message}")
                return@withContext Result.failure(Exception("Error querying work sessions: ${e.message}"))
            }

            Log.d(TAG, "🔍 Found ${workSessionsForApp.size} work sessions for this application")

            // Find the most recent work session
            val workSession = workSessionsForApp
                .sortedByDescending { it.createdAt }
                .firstOrNull()

            if (workSession == null) {
                Log.e(TAG, "❌ No work session found")
                return@withContext Result.failure(Exception("No work session found. Please contact your employer to generate an OTP."))
            }

            Log.d(TAG, "🔍 === STEP 2: ANALYZING WORK SESSION ===")
            Log.d(TAG, "🔍 Work Session ID: ${workSession.id}")
            Log.d(TAG, "🔍 Work Session Status: ${workSession.status}")
            Log.d(TAG, "🔍 Work Session OTP: '${workSession.otp}'")
            Log.d(TAG, "🔍 Work Session OTP Length: ${workSession.otp.length}")
            Log.d(TAG, "🔍 Work Session Expiry: ${workSession.otpExpiry}")

            // Check if OTP is expired
            if (workSession.status == "EXPIRED") {
                Log.e(TAG, "❌ Work session OTP has expired")
                return@withContext Result.failure(Exception("The OTP has expired. Please contact your employer for a new OTP."))
            }

            // Check if work has already started
            if (workSession.status == "WORK_STARTED") {
                Log.w(TAG, "⚠️ Work has already started for this session")
                return@withContext Result.success(true)
            }

            // Only proceed if status is OTP_GENERATED
            if (workSession.status != "OTP_GENERATED") {
                Log.e(TAG, "❌ Invalid work session status: ${workSession.status}")
                return@withContext Result.failure(Exception("Work session is in invalid state: ${workSession.status}"))
            }

            // 🚀 STEP 3: ENHANCED OTP COMPARISON
            Log.d(TAG, "🔍 === STEP 3: ENHANCED OTP COMPARISON ===")

            val sessionOtp = workSession.otp
            val inputOtp = enteredOtp.trim()

            Log.d(TAG, "🔍 Session OTP: '$sessionOtp'")
            Log.d(TAG, "🔍 Input OTP: '$inputOtp'")
            Log.d(TAG, "🔍 Session OTP Length: ${sessionOtp.length}")
            Log.d(TAG, "🔍 Input OTP Length: ${inputOtp.length}")

            // 🚀 ENHANCED: Multiple comparison methods with normalization
            val normalizedSessionOtp = sessionOtp.padStart(6, '0')
            val normalizedInputOtp = inputOtp.padStart(6, '0')

            Log.d(TAG, "🔍 Normalized Session OTP: '$normalizedSessionOtp'")
            Log.d(TAG, "🔍 Normalized Input OTP: '$normalizedInputOtp'")

            // Try different comparison methods
            val method1 = normalizedSessionOtp == normalizedInputOtp
            val method2 = sessionOtp == inputOtp
            val method3 = try {
                sessionOtp.toInt() == inputOtp.toInt()
            } catch (e: Exception) {
                Log.d(TAG, "   Method 3 exception: ${e.message}")
                false
            }
            val method4 = sessionOtp.trimStart('0') == inputOtp.trimStart('0')

            Log.d(TAG, "🔍 === COMPARISON METHODS ===")
            Log.d(TAG, "   Method 1 (Normalized): $method1")
            Log.d(TAG, "   Method 2 (Direct): $method2")
            Log.d(TAG, "   Method 3 (Integer): $method3")
            Log.d(TAG, "   Method 4 (No Leading Zeros): $method4")

            val otpMatches = method1 || method2 || method3 || method4
            Log.d(TAG, "🔍 Final OTP Match Result: $otpMatches")

            if (!otpMatches) {
                Log.e(TAG, "❌ ALL OTP COMPARISON METHODS FAILED!")
                Log.e(TAG, "   Session OTP: '$sessionOtp'")
                Log.e(TAG, "   Input OTP: '$inputOtp'")
                Log.e(TAG, "   Normalized Session: '$normalizedSessionOtp'")
                Log.e(TAG, "   Normalized Input: '$normalizedInputOtp'")

                // 🚀 Additional debug info
                Log.e(TAG, "   Session OTP chars: ${sessionOtp.toCharArray().joinToString(",") { "'$it'(${it.code})" }}")
                Log.e(TAG, "   Input OTP chars: ${inputOtp.toCharArray().joinToString(",") { "'$it'(${it.code})" }}")

                return@withContext Result.failure(Exception("Invalid OTP. Please check and try again."))
            }

            Log.d(TAG, "✅ OTP validation successful!")

            // 🚀 STEP 4: Check expiry (but don't fail if parsing fails)
            try {
                if (workSession.otpExpiry.isNotBlank()) {
                    val expiry = java.time.Instant.parse(workSession.otpExpiry)
                    val now = java.time.Instant.now()

                    if (now.isAfter(expiry)) {
                        Log.e(TAG, "❌ OTP expired at: $expiry")
                        return@withContext Result.failure(Exception("OTP has expired. Please contact your employer for a new one."))
                    } else {
                        Log.d(TAG, "✅ OTP is still valid (expires at: $expiry)")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ Could not parse expiry time, continuing: ${e.message}")
            }

            // 🚀 STEP 5: Update database
            val now = java.time.Instant.now().toString()
            Log.d(TAG, "🔍 === STEP 5: UPDATING DATABASE ===")

            try {
                supabaseClient.table("work_sessions")
                    .update(mapOf(
                        "status" to "WORK_STARTED",
                        "otp_used_at" to now,
                        "work_start_time" to now,
                        "updated_at" to now
                    )) {
                        filter { eq("id", workSession.id) }
                    }

                Log.d(TAG, "✅ Work session updated successfully")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error updating work session: ${e.message}")
                return@withContext Result.failure(Exception("Failed to start work session: ${e.message}"))
            }

            try {
                supabaseClient.table("applications")
                    .update(mapOf(
                        "status" to "WORK_IN_PROGRESS",
                        "updated_at" to now
                    )) {
                        filter { eq("id", applicationId) }
                    }

                Log.d(TAG, "✅ Application status updated successfully")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error updating application status: ${e.message}")
                return@withContext Result.failure(Exception("Failed to update application status: ${e.message}"))
            }

            Log.d(TAG, "🔍 === OTP VERIFICATION SUCCESS ===")
            Result.success(true)

        } catch (e: Exception) {
            Log.e(TAG, "❌ === OTP VERIFICATION FAILED ===")
            Log.e(TAG, "❌ Unexpected error: ${e.message}", e)
            Result.failure(Exception("Unexpected error during OTP verification: ${e.message}"))
        }
    }

    // Add these methods to your existing ApplicationRepository.kt

    /**
     * Generate OTP when accepting an application (employer side)
     */
    suspend fun acceptApplicationAndGenerateOtp(applicationId: String): Result<String> = withContext(Dispatchers.IO) {
        return@withContext try {
            Log.d(TAG, "🚀 === STARTING OTP GENERATION ===")
            Log.d(TAG, "🚀 Application ID: $applicationId")

            val userId = authRepository.getCurrentUserId()
            if (userId == null) {
                Log.e(TAG, "❌ User not authenticated")
                return@withContext Result.failure(Exception("Not authenticated"))
            }

            Log.d(TAG, "🚀 Employer ID: ${userId.take(8)}...")

            // Step 1: Get application details
            val application = supabaseClient
                .table("applications")
                .select {
                    filter { eq("id", applicationId) }
                }
                .decodeSingleOrNull<Application>()

            if (application == null) {
                Log.e(TAG, "❌ Application not found: $applicationId")
                return@withContext Result.failure(Exception("Application not found"))
            }

            Log.d(TAG, "✅ Found application: ${application.id}")
            Log.d(TAG, "   Job ID: ${application.jobId}")
            Log.d(TAG, "   Employee ID: ${application.employeeId}")
            Log.d(TAG, "   Current Status: ${application.status}")

            // Step 2: Get job details to verify employer ownership
            val job = supabaseClient
                .table("jobs")
                .select {
                    filter { eq("id", application.jobId) }
                }
                .decodeSingleOrNull<Job>()

            if (job == null) {
                Log.e(TAG, "❌ Job not found: ${application.jobId}")
                return@withContext Result.failure(Exception("Job not found"))
            }

            Log.d(TAG, "✅ Found job: ${job.title}")
            Log.d(TAG, "   Job Employer ID: ${job.employerId}")

            if (job.employerId != userId) {
                Log.e(TAG, "❌ Unauthorized: Current user $userId is not the job employer ${job.employerId}")
                return@withContext Result.failure(Exception("Unauthorized - You can only generate OTP for your own job applications"))
            }

            Log.d(TAG, "✅ Authorization verified")

            // Step 3: Check if work session already exists
            val existingSession = supabaseClient
                .table("work_sessions")
                .select {
                    filter {
                        eq("application_id", applicationId)
                    }
                    order("created_at", Order.DESCENDING)
                    limit(1)
                }
                .decodeSingleOrNull<WorkSession>()

            if (existingSession != null) {
                Log.d(TAG, "ℹ️ Found existing work session: ${existingSession.id}")
                Log.d(TAG, "   Status: ${existingSession.status}")
                Log.d(TAG, "   OTP: ${existingSession.otp}")
            }

            // Step 4: Generate new OTP and expiry
            val otp = generateOtp()
            val expiry = Instant.now().plusSeconds(TimeUnit.MINUTES.toSeconds(30)).toString()
            val now = Instant.now().toString()

            Log.d(TAG, "🔐 Generated new OTP: $otp")
            Log.d(TAG, "⏰ Expiry time: $expiry")

            // Step 5: Update or create work session
            if (existingSession != null) {
                Log.d(TAG, "🔄 Updating existing work session...")

                supabaseClient.table("work_sessions")
                    .update(mapOf(
                        "otp" to otp,
                        "otp_expiry" to expiry,
                        "status" to "OTP_GENERATED",
                        "updated_at" to now
                    )) {
                        filter { eq("id", existingSession.id) }
                    }

                Log.d(TAG, "✅ Updated existing work session with new OTP")
            } else {
                Log.d(TAG, "➕ Creating new work session...")

                val workSessionData = mapOf(
                    "application_id" to applicationId,
                    "job_id" to application.jobId,
                    "employee_id" to application.employeeId,
                    "employer_id" to userId,
                    "otp" to otp,
                    "otp_expiry" to expiry,
                    "status" to "OTP_GENERATED",
                    "created_at" to now,
                    "updated_at" to now
                )

                Log.d(TAG, "📝 Work session data: $workSessionData")

                supabaseClient.table("work_sessions")
                    .insert(workSessionData)

                Log.d(TAG, "✅ Created new work session")
            }

            // Step 6: Update application status to SELECTED
            Log.d(TAG, "🔄 Updating application status to SELECTED...")

            supabaseClient.table("applications")
                .update(mapOf(
                    "status" to "SELECTED",
                    "updated_at" to now
                )) {
                    filter { eq("id", applicationId) }
                }

            Log.d(TAG, "✅ Updated application status to SELECTED")

            Log.d(TAG, "🚀 === OTP GENERATION COMPLETED SUCCESSFULLY ===")
            Log.d(TAG, "🎉 Final OTP: $otp")

            Result.success(otp)

        } catch (e: Exception) {
            Log.e(TAG, "❌ === OTP GENERATION FAILED ===")
            Log.e(TAG, "❌ Error: ${e.message}", e)
            Result.failure(Exception("Failed to generate OTP: ${e.message}"))
        }
    }

    /**
     * Get work session details for an application (employer side)
     */
    suspend fun getWorkSessionForApplication(applicationId: String): Result<WorkSession?> = withContext(Dispatchers.IO) {
        return@withContext try {
            Log.d(TAG, "🔍 Getting work session for application: $applicationId")

            val userId = authRepository.getCurrentUserId()
            if (userId == null) {
                Log.e(TAG, "❌ User not authenticated")
                return@withContext Result.failure(Exception("Not authenticated"))
            }

            val workSession = supabaseClient
                .table("work_sessions")
                .select {
                    filter {
                        eq("application_id", applicationId)
                    }
                    order("created_at", Order.DESCENDING)
                    limit(1)
                }
                .decodeSingleOrNull<WorkSession>()

            if (workSession != null) {
                Log.d(TAG, "✅ Found work session for application $applicationId")
                Log.d(TAG, "   Session ID: ${workSession.id}")
                Log.d(TAG, "   Status: ${workSession.status}")
                Log.d(TAG, "   OTP: ${workSession.otp}")
                Log.d(TAG, "   Expiry: ${workSession.otpExpiry}")
            } else {
                Log.d(TAG, "ℹ️ No work session found for application: $applicationId")
            }

            Result.success(workSession)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting work session for application $applicationId: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Request a new OTP for an expired work session
     */
    suspend fun requestNewOtp(applicationId: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val userId = authRepository.getCurrentUserId()
                ?: return@withContext Result.failure(Exception("Not authenticated"))

            // Get the application details
            val application = supabaseClient
                .table("applications")
                .select {
                    filter {
                        eq("id", applicationId)
                        eq("employee_id", userId)
                    }
                }
                .decodeSingleOrNull<Application>()
                ?: return@withContext Result.failure(Exception("Application not found"))

            // Check if status is SELECTED or HIRED
            if (application.status !in listOf("SELECTED", "HIRED")) {
                return@withContext Result.failure(Exception("Application status must be SELECTED or HIRED to request OTP"))
            }

            // Get job details to find employer
            val job = supabaseClient
                .table("jobs")
                .select {
                    filter { eq("id", application.jobId) }
                }
                .decodeSingleOrNull<Job>()
                ?: return@withContext Result.failure(Exception("Job not found"))

            // Update any existing EXPIRED work session or create new one
            val existingSession = supabaseClient
                .table("work_sessions")
                .select {
                    filter {
                        eq("application_id", applicationId)
                        eq("employee_id", userId)
                        eq("status", "EXPIRED")
                    }
                }
                .decodeSingleOrNull<WorkSession>()

            val newOtp = generateOtp()
            val expiry = Instant.now().plusSeconds(TimeUnit.MINUTES.toSeconds(30)).toString()
            val now = Instant.now().toString()

            if (existingSession != null) {
                // Update existing expired session
                supabaseClient.table("work_sessions")
                    .update(mapOf(
                        "otp" to newOtp,
                        "otp_expiry" to expiry,
                        "status" to "OTP_GENERATED",
                        "updated_at" to now
                    )) {
                        filter { eq("id", existingSession.id) }
                    }
            } else {
                // Create new work session
                supabaseClient.table("work_sessions")
                    .insert(mapOf(
                        "application_id" to applicationId,
                        "job_id" to application.jobId,
                        "employee_id" to userId,
                        "employer_id" to job.employerId,
                        "otp" to newOtp,
                        "otp_expiry" to expiry,
                        "status" to "OTP_GENERATED",
                        "created_at" to now,
                        "updated_at" to now
                    ))
            }

            Log.d(TAG, "✅ New OTP generated: $newOtp for application $applicationId")

            // In a real app, you would send this OTP to the employer
            // For now, return it so it can be displayed
            Result.success("New OTP requested. Please contact your employer for the code.")

        } catch (e: Exception) {
            Log.e(TAG, "Error requesting new OTP: ${e.message}")
            Result.failure(e)
        }
    }


    /**
     * 4. Get application with work session data
     */
    suspend fun getApplicationWithWorkSession(applicationId: String): Result<ApplicationWithJob> = withContext(Dispatchers.IO) {
        try {
            // Get application
            val application = supabaseClient
                .table("applications")
                .select {
                    filter { eq("id", applicationId) }
                }
                .decodeSingleOrNull<Application>() ?: return@withContext Result.failure(Exception("Application not found"))

            // Get job
            val job = supabaseClient
                .table("jobs")
                .select {
                    filter { eq("id", application.jobId) }
                }
                .decodeSingleOrNull<Job>() ?: return@withContext Result.failure(Exception("Job not found"))

            // Get work session (if exists)
            val workSession = supabaseClient
                .table("work_sessions")
                .select {
                    filter { eq("application_id", applicationId) }
                    order("created_at", Order.DESCENDING)
                    limit(1)
                }
                .decodeSingleOrNull<WorkSession>()

            val applicationWithJob = ApplicationWithJob(
                id = application.id,
                jobId = application.jobId,
                employeeId = application.employeeId,
                status = ApplicationStatus.valueOf(application.status.uppercase()),
                appliedAt = application.appliedAt ?: application.createdAt, // Use appliedAt or fallback to createdAt
                updatedAt = application.updatedAt,
                job = job,
                workSession = workSession
            )

            Result.success(applicationWithJob)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting application with work session: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * 🚀 STEP 2: Employer verifies completion OTP and finalizes work
     */
    suspend fun completeWorkWithOtp(applicationId: String, completionOtp: String): Result<WorkCompletionResult> = withContext(Dispatchers.IO) {
        try {
            val userId = authRepository.getCurrentUserId()
                ?: return@withContext Result.failure(Exception("Not authenticated"))

            Log.d(TAG, "🚀 === COMPLETING WORK WITH OTP ===")
            Log.d(TAG, "Application ID: $applicationId")
            Log.d(TAG, "Employer ID: $userId")
            Log.d(TAG, "Completion OTP: $completionOtp")

            // 🚀 Get application and verify employer ownership
            val application = supabaseClient
                .table("applications")
                .select {
                    filter { eq("id", applicationId) }
                }
                .decodeSingleOrNull<Application>()
                ?: return@withContext Result.failure(Exception("Application not found"))

            // 🚀 Get job to verify employer
            val job = supabaseClient
                .table("jobs")
                .select {
                    filter { eq("id", application.jobId) }
                }
                .decodeSingleOrNull<Job>()
                ?: return@withContext Result.failure(Exception("Job not found"))

            if (job.employerId != userId) {
                return@withContext Result.failure(Exception("Unauthorized - not your job"))
            }

            // 🚀 Get work session
            val workSession = supabaseClient
                .table("work_sessions")
                .select {
                    filter {
                        eq("application_id", applicationId)
                        eq("status", "COMPLETION_PENDING")
                    }
                    order("created_at", Order.DESCENDING)
                    limit(1)
                }
                .decodeSingleOrNull<WorkSession>()
                ?: return@withContext Result.failure(Exception("No pending completion found"))

            // 🚀 Verify completion OTP
            val sessionOtp = workSession.otp
            val inputOtp = completionOtp.trim()

            Log.d(TAG, "Comparing OTPs: '$sessionOtp' vs '$inputOtp'")

            val otpMatches = sessionOtp == inputOtp ||
                    sessionOtp.toIntOrNull() == inputOtp.toIntOrNull()

            if (!otpMatches) {
                Log.e(TAG, "❌ Completion OTP mismatch")
                return@withContext Result.failure(Exception("Invalid completion OTP"))
            }

            // 🚀 Check OTP expiry
            try {
                if (workSession.otpExpiry.isNotBlank()) {
                    val expiry = java.time.Instant.parse(workSession.otpExpiry)
                    if (java.time.Instant.now().isAfter(expiry)) {
                        return@withContext Result.failure(Exception("Completion OTP has expired"))
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not parse expiry, continuing: ${e.message}")
            }

            // 🚀 Calculate wages
            val durationMinutes = workSession.workDurationMinutes ?: 0
            val hourlyRate = extractHourlyRateFromSalary(job.salaryRange)
            val totalWages = calculateWages(durationMinutes, hourlyRate)

            val now = java.time.Instant.now().toString()

            // 🚀 Update work session to completed
            supabaseClient.table("work_sessions")
                .update(mapOf(
                    "status" to "WORK_COMPLETED",
                    "otp_used_at" to now,
                    "updated_at" to now
                )) {
                    filter { eq("id", workSession.id) }
                }

            // 🚀 Update application to completed
            supabaseClient.table("applications")
                .update(mapOf(
                    "status" to "COMPLETED",
                    "updated_at" to now
                )) {
                    filter { eq("id", applicationId) }
                }

            // 🚀 Create completion result
            val result = WorkCompletionResult(
                applicationId = applicationId,
                employeeId = application.employeeId,
                employerId = userId,
                jobTitle = job.title,
                workDurationMinutes = durationMinutes,
                hourlyRate = hourlyRate,
                totalWages = totalWages,
                workStartTime = workSession.workStartTime,
                workEndTime = workSession.workEndTime,
                completedAt = now
            )

            Log.d(TAG, "✅ Work completed successfully")
            Log.d(TAG, "Duration: $durationMinutes minutes")
            Log.d(TAG, "Hourly Rate: ₹$hourlyRate")
            Log.d(TAG, "Total Wages: ₹$totalWages")

            Result.success(result)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error completing work with OTP: ${e.message}", e)
            Result.failure(Exception("Failed to complete work: ${e.message}"))
        }
    }

    /**
     * 🚀 FIXED: Simple work completion - CRITICAL SERIALIZATION FIX
     */
    suspend fun completeWork(applicationId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val userId = authRepository.getCurrentUserId()
                ?: return@withContext Result.failure(Exception("Not authenticated"))

            Log.d(TAG, "🚀 === INITIATING EMPLOYEE WORK COMPLETION ===")
            Log.d(TAG, "Application ID: $applicationId")
            Log.d(TAG, "Employee ID: $userId")

            // 🚀 Get active work session
            val workSession = supabaseClient
                .table("work_sessions")
                .select {
                    filter {
                        eq("application_id", applicationId)
                        eq("employee_id", userId)
                        eq("status", "WORK_STARTED")
                    }
                    order("created_at", Order.DESCENDING)
                    limit(1)
                }
                .decodeSingleOrNull<WorkSession>()
                ?: return@withContext Result.failure(Exception("No active work session found"))

            Log.d(TAG, "✅ Found active work session: ${workSession.id}")

            // 🚀 Generate completion OTP for employer verification
            val completionOtp = generateOtp()
            val otpExpiry = Instant.now().plusSeconds(TimeUnit.MINUTES.toSeconds(30)).toString()
            val now = Instant.now().toString()

            // 🚀 Calculate work duration
            val workStartTime = if (workSession.workStartTime != null) {
                try {
                    Instant.parse(workSession.workStartTime)
                } catch (e: Exception) {
                    Instant.now().minusSeconds(3600) // Default to 1 hour ago
                }
            } else {
                Instant.now().minusSeconds(3600)
            }

            val workEndTime = Instant.now()
            val durationMinutes = Duration.between(workStartTime, workEndTime).toMinutes().toInt()

            Log.d(TAG, "⏱️ Work duration calculated: $durationMinutes minutes")
            Log.d(TAG, "🔐 Generated completion OTP: $completionOtp")

            // 🚀 CRITICAL FIX: Use specific types instead of Any to avoid serialization error
            val workSessionUpdateData = mapOf(
                "status" to "COMPLETION_PENDING",
                "work_end_time" to now,
                "work_duration_minutes" to durationMinutes,
                "completion_otp" to completionOtp,  // Store completion OTP
                "otp_expiry" to otpExpiry,          // Expiry for completion OTP
                "updated_at" to now
            )

            val applicationUpdateData = mapOf(
                "status" to "COMPLETION_PENDING",
                "updated_at" to now
            )

            // 🚀 Update work session with completion data
            supabaseClient.table("work_sessions")
                .update(workSessionUpdateData) {
                    filter { eq("id", workSession.id) }
                }

            Log.d(TAG, "✅ Work session updated to COMPLETION_PENDING")

            // 🚀 Update application status
            supabaseClient.table("applications")
                .update(applicationUpdateData) {
                    filter { eq("id", applicationId) }
                }

            Log.d(TAG, "✅ Application updated to COMPLETION_PENDING")
            Log.d(TAG, "🎉 Work completion initiated successfully!")
            Log.d(TAG, "📱 Completion OTP generated: $completionOtp (valid for 30 minutes)")

            Result.success(true)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in employee work completion: ${e.message}", e)
            Result.failure(Exception("Failed to complete work: ${e.message}"))
        }
    }

    /**
     * 🚀 NEW: Get work session with completion data
     */
    suspend fun getWorkSessionWithCompletion(applicationId: String): Result<WorkSession?> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🔍 Getting work session with completion data for: $applicationId")

            val workSession = supabaseClient
                .table("work_sessions")
                .select {
                    filter { eq("application_id", applicationId) }
                    order("created_at", Order.DESCENDING)
                    limit(1)
                }
                .decodeSingleOrNull<WorkSession>()

            if (workSession != null) {
                Log.d(TAG, "✅ Found work session: ${workSession.id}")
                Log.d(TAG, "   Status: ${workSession.status}")
                if (workSession.status == "COMPLETION_PENDING") {
                    Log.d(TAG, "   Completion OTP available: ${workSession.otp}")
                }
            } else {
                Log.d(TAG, "ℹ️ No work session found")
            }

            Result.success(workSession)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting work session: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * 🚀 FIXED: Employee initiates work completion and generates completion OTP
     */
    suspend fun initiateWorkCompletion(applicationId: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val userId = authRepository.getCurrentUserId()
                ?: return@withContext Result.failure(Exception("Not authenticated"))

            Log.d(TAG, "🚀 === INITIATING WORK COMPLETION ===")
            Log.d(TAG, "Application ID: $applicationId")
            Log.d(TAG, "Employee ID: $userId")

            // 🚀 Get active work session
            val workSession = supabaseClient
                .table("work_sessions")
                .select {
                    filter {
                        eq("application_id", applicationId)
                        eq("employee_id", userId)
                        eq("status", "WORK_STARTED")
                    }
                    order("created_at", Order.DESCENDING)
                    limit(1)
                }
                .decodeSingleOrNull<WorkSession>()
                ?: return@withContext Result.failure(Exception("No active work session found"))

            Log.d(TAG, "Found work session: ${workSession.id}")

            // 🚀 Generate completion OTP
            val completionOtp = generateOtp()
            val otpExpiry = Instant.now().plusSeconds(TimeUnit.MINUTES.toSeconds(30)).toString()
            val now = Instant.now().toString()

            // 🚀 Calculate work duration
            val workStartTime = if (workSession.workStartTime != null) {
                Instant.parse(workSession.workStartTime)
            } else {
                Instant.now().minusSeconds(3600) // Default to 1 hour ago if no start time
            }
            val workEndTime = Instant.now()
            val durationMinutes = Duration.between(workStartTime, workEndTime).toMinutes().toInt()

            Log.d(TAG, "Work duration: $durationMinutes minutes")

            // 🚀 CRITICAL FIX: Use specific types instead of Any for serialization
            val workSessionUpdateData = mapOf(
                "status" to "COMPLETION_PENDING",
                "work_end_time" to now,
                "work_duration_minutes" to durationMinutes,
                "completion_otp" to completionOtp,
                "otp_expiry" to otpExpiry,
                "updated_at" to now
            )

            val applicationUpdateData = mapOf(
                "status" to "COMPLETION_PENDING",
                "updated_at" to now
            )

            // 🚀 Update work session with completion data
            supabaseClient.table("work_sessions")
                .update(workSessionUpdateData) {
                    filter { eq("id", workSession.id) }
                }

            // 🚀 Update application status
            supabaseClient.table("applications")
                .update(applicationUpdateData) {
                    filter { eq("id", applicationId) }
                }

            Log.d(TAG, "✅ Work completion initiated. OTP: $completionOtp")

            Result.success("Work completion initiated. Share this OTP with your employer: $completionOtp")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error initiating work completion: ${e.message}", e)
            Result.failure(Exception("Failed to initiate work completion: ${e.message}"))
        }
    }

    /**
     * 🚀 NEW: Get completion OTP for employee display
     */
    suspend fun getCompletionOtp(applicationId: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val userId = authRepository.getCurrentUserId()
                ?: return@withContext Result.failure(Exception("Not authenticated"))

            Log.d(TAG, "🔍 Getting completion OTP for application: $applicationId")

            // Get work session with completion OTP
            val workSession = supabaseClient
                .table("work_sessions")
                .select {
                    filter {
                        eq("application_id", applicationId)
                        eq("employee_id", userId)
                        eq("status", "COMPLETION_PENDING")
                    }
                    order("created_at", Order.DESCENDING)
                    limit(1)
                }
                .decodeSingleOrNull<WorkSession>()
                ?: return@withContext Result.failure(Exception("No completion pending session found"))

            // Get completion OTP from work session
            val completionOtp = workSession.completionOtp ?: ""

            if (completionOtp.isBlank()) {
                return@withContext Result.failure(Exception("No completion OTP found"))
            }

            Log.d(TAG, "✅ Found completion OTP: $completionOtp")
            Result.success(completionOtp)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting completion OTP: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * 🚀 NEW: Employer verifies completion OTP and finalizes work with wage calculation
     */
    suspend fun verifyCompletionOtpAndFinalize(
        applicationId: String,
        enteredOtp: String
    ): Result<WorkCompletionResult> = withContext(Dispatchers.IO) {
        try {
            val userId = authRepository.getCurrentUserId()
                ?: return@withContext Result.failure(Exception("Not authenticated"))

            Log.d(TAG, "🚀 === EMPLOYER COMPLETION OTP VERIFICATION ===")
            Log.d(TAG, "Application ID: $applicationId")
            Log.d(TAG, "Employer ID: $userId")
            Log.d(TAG, "Entered OTP: $enteredOtp")

            // 🚀 Get application and verify employer ownership
            val application = supabaseClient
                .table("applications")
                .select {
                    filter { eq("id", applicationId) }
                }
                .decodeSingleOrNull<Application>()
                ?: return@withContext Result.failure(Exception("Application not found"))

            // 🚀 Get job to verify employer ownership
            val job = supabaseClient
                .table("jobs")
                .select {
                    filter { eq("id", application.jobId) }
                }
                .decodeSingleOrNull<Job>()
                ?: return@withContext Result.failure(Exception("Job not found"))

            if (job.employerId != userId) {
                return@withContext Result.failure(Exception("Unauthorized - not your job"))
            }

            Log.d(TAG, "✅ Employer ownership verified")

            // 🚀 Get work session with completion OTP
            val workSession = supabaseClient
                .table("work_sessions")
                .select {
                    filter {
                        eq("application_id", applicationId)
                        eq("status", "COMPLETION_PENDING")
                    }
                    order("created_at", Order.DESCENDING)
                    limit(1)
                }
                .decodeSingleOrNull<WorkSession>()
                ?: return@withContext Result.failure(Exception("No completion pending session found"))

            // 🚀 Verify completion OTP
            val sessionOtp = workSession.completionOtp ?: ""
            val inputOtp = enteredOtp.trim()

            Log.d(TAG, "🔍 Comparing completion OTPs:")
            Log.d(TAG, "   Session OTP: '$sessionOtp'")
            Log.d(TAG, "   Input OTP: '$inputOtp'")

            // 🚀 Enhanced OTP comparison
            val otpMatches = sessionOtp == inputOtp ||
                    sessionOtp.toIntOrNull() == inputOtp.toIntOrNull() ||
                    sessionOtp.padStart(6, '0') == inputOtp.padStart(6, '0')

            if (!otpMatches) {
                Log.e(TAG, "❌ Completion OTP mismatch!")
                return@withContext Result.failure(Exception("Invalid completion OTP"))
            }

            Log.d(TAG, "✅ Completion OTP verified successfully!")

            // 🚀 Check OTP expiry
            try {
                if (workSession.otpExpiry.isNotBlank()) {
                    val expiry = Instant.parse(workSession.otpExpiry)
                    if (Instant.now().isAfter(expiry)) {
                        return@withContext Result.failure(Exception("Completion OTP has expired"))
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not parse expiry, continuing: ${e.message}")
            }

            // 🚀 Calculate wages
            val durationMinutes = workSession.workDurationMinutes ?: 0
            val hourlyRate = extractHourlyRateFromSalary(job.salaryRange)
            val totalWages = calculateWages(durationMinutes, hourlyRate)

            Log.d(TAG, "💰 Wage calculation:")
            Log.d(TAG, "   Duration: $durationMinutes minutes")
            Log.d(TAG, "   Hourly Rate: ₹$hourlyRate")
            Log.d(TAG, "   Total Wages: ₹$totalWages")

            val now = Instant.now().toString()

            // 🚀 CRITICAL FIX: Use specific types for serialization
            val completedWorkSessionData = mapOf(
                "status" to "WORK_COMPLETED",
                "completion_otp_used_at" to now,
                "total_wages_calculated" to totalWages.toString(),
                "hourly_rate_used" to hourlyRate.toString(),
                "updated_at" to now
            )

            supabaseClient.table("work_sessions")
                .update(completedWorkSessionData) {
                    filter { eq("id", workSession.id) }
                }

            Log.d(TAG, "✅ Work session marked as completed")

            // 🚀 Update application to completed
            val completedApplicationData = mapOf(
                "status" to "COMPLETED",
                "updated_at" to now
            )

            supabaseClient.table("applications")
                .update(completedApplicationData) {
                    filter { eq("id", applicationId) }
                }

            Log.d(TAG, "✅ Application marked as completed")

            // 🚀 Create completion result
            val result = WorkCompletionResult(
                applicationId = applicationId,
                employeeId = application.employeeId,
                employerId = userId,
                jobTitle = job.title,
                workDurationMinutes = durationMinutes,
                hourlyRate = hourlyRate,
                totalWages = totalWages,
                workStartTime = workSession.workStartTime,
                workEndTime = workSession.workEndTime,
                completedAt = now
            )

            Log.d(TAG, "🎉 === WORK COMPLETION SUCCESSFUL ===")
            Log.d(TAG, "💼 Job: ${job.title}")
            Log.d(TAG, "⏰ Duration: $durationMinutes minutes (${String.format("%.1f", durationMinutes / 60.0)} hours)")
            Log.d(TAG, "💰 Total Payment: ₹$totalWages")

            Result.success(result)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in employer completion verification: ${e.message}", e)
            Result.failure(Exception("Failed to verify completion: ${e.message}"))
        }
    }


    /**
     * Generate a 6-digit random OTP
     */
    private fun generateOtp(): String {
        val otp = (100000..999999).random().toString()
        Log.d(TAG, "🎲 Generated OTP: $otp")
        return otp
    }

    // Helper functions for wage calculation
    private fun extractHourlyRateFromSalary(salaryRange: String?): Double {
        if (salaryRange.isNullOrBlank()) return 50.0 // Default rate

        try {
            // Extract numbers from salary range (e.g., "₹40-60" -> 50.0)
            val numbers = salaryRange.replace("[^0-9-.]".toRegex(), "")
                .split("-")
                .mapNotNull { it.trim().toDoubleOrNull() }

            return when {
                numbers.size >= 2 -> (numbers[0] + numbers[1]) / 2
                numbers.size == 1 -> numbers[0]
                else -> 50.0
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not parse salary range: $salaryRange, using default")
            return 50.0
        }
    }

    private fun calculateWages(durationMinutes: Int, hourlyRate: Double): Double {
        val hours = durationMinutes / 60.0
        return hours * hourlyRate
    }


    // 🚀 Data class for work completion result
    @Serializable
    data class WorkCompletionResult(
        val applicationId: String,
        val employeeId: String,
        val employerId: String,
        val jobTitle: String,
        val workDurationMinutes: Int,
        val hourlyRate: Double,
        val totalWages: Double,
        val workStartTime: String?,
        val workEndTime: String?,
        val completedAt: String
    )
}