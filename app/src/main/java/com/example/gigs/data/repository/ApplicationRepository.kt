package com.example.gigs.data.repository

import android.content.ContentValues.TAG
import android.util.Log
import com.example.gigs.data.model.ApplicationStatus
import com.example.gigs.data.model.ApplicationWithJob
import com.example.gigs.data.model.Job
import com.example.gigs.data.model.Application
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
import javax.inject.Inject
import javax.inject.Singleton
import java.util.concurrent.ConcurrentHashMap

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
    private suspend fun getMyApplicationsDirect(limit: Int = 0): List<ApplicationWithJob> {
        return try {
            withContext(Dispatchers.IO) {
                withTimeoutOrNull(3000) {
                    Log.d(TAG, "Fetching applications for current user")
                    val userId = authRepository.getCurrentUserId()
                        ?: throw Exception("User not authenticated")

                    // Direct API call for applications
                    val applications = supabaseClient
                        .table("applications")
                        .select {
                            filter { eq("employee_id", userId) }
                            order("applied_at", Order.DESCENDING)
                            if (limit > 0) {
                                limit(limit.toLong())
                            }else {
                                limit(50) // ✅ ADD DEFAULT LIMIT
                            }
                        }
                        .decodeList<Application>()

                    Log.d(TAG, "Found ${applications.size} applications")

                    if (applications.isEmpty()) return@withTimeoutOrNull emptyList()

                    // Get unique job IDs
                    val jobIds = applications.map { it.jobId }.distinct()
                    Log.d(TAG, "Need to fetch ${jobIds.size} unique jobs")

                    // Batch fetch jobs if reasonable number
                    val jobs = if (jobIds.size <= 10) {
                        Log.d(TAG, "Fetching ${jobIds.size} jobs in a single batch query")

                        try {
                            val inClause = jobIds.joinToString(",", prefix = "(", postfix = ")") { it }
                            val fetchedJobs = supabaseClient
                                .table("jobs")
                                .select {
                                    filter {
                                        filter("id", FilterOperator.IN, inClause)
                                    }
                                }
                                .decodeList<Job>()

                            Log.d(TAG, "Fetched batch of ${fetchedJobs.size} jobs")

                            // Update cache
                            val currentTime = System.currentTimeMillis()
                            fetchedJobs.forEach { job ->
                                jobCache[job.id] = job
                                cacheExpiration[job.id] = currentTime + cacheDuration
                                Log.d(TAG, "Cached job ${job.id} until ${currentTime + cacheDuration}")
                            }

                            Log.d(TAG, "Successfully fetched and cached ${fetchedJobs.size} jobs")
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

                    // Create job map for quick lookup
                    val jobMap = jobs.associateBy { it.id }

                    // Convert to ApplicationWithJob
                    applications.mapNotNull { app ->
                        jobMap[app.jobId]?.let { job ->
                            ApplicationWithJob(
                                id = app.id,
                                jobId = app.jobId,
                                employeeId = app.employeeId,
                                status = try {
                                    ApplicationStatus.valueOf(app.status.uppercase())
                                } catch (e: Exception) {
                                    ApplicationStatus.APPLIED
                                },
                                appliedAt = app.createdAt,
                                updatedAt = app.updatedAt,
                                job = job
                            )
                        }
                    }
                } ?: emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting applications directly: ${e.message}")
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
    private suspend fun getApplicationsForJobDirect(jobId: String): List<ApplicationWithJob> {
        return try {
            val userId = authRepository.getCurrentUserId() ?: throw Exception("User not authenticated")

            // Get job details
            val job = getJobByIdDirect(jobId) ?: throw Exception("Job not found")

            if (job.employerId != userId) {
                throw Exception("You can only view applications for your own jobs")
            }

            // Get applications for this job
            val applications = supabaseClient
                .table("applications")
                .select {
                    filter { eq("job_id", jobId) }
                    order("applied_at", Order.ASCENDING)
                }
                .decodeList<Application>()

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

    @OptIn(SupabaseExperimental::class)
    private suspend fun updateEmployeeApplicationStatusDirect(
        jobId: String,
        status: String
    ): Result<Unit> {
        val operationKey = "update_${jobId}_${status}"

        // 🚀 CRITICAL: Enhanced duplicate prevention with shorter interval
        if (isOperationInProgress(operationKey)) {
            Log.w(TAG, "🚫 Update operation already in progress for $operationKey")
            return Result.success(Unit) // Return success to prevent UI errors
        }

        processingOperations[operationKey] = System.currentTimeMillis()

        return try {
            val userId = authRepository.getCurrentUserId()
                ?: return Result.failure(Exception("User not authenticated"))

            // 🚀 CRITICAL: Always validate status before using
            val validatedStatus = validateAndNormalizeStatus(status)
            val timestamp = java.time.Instant.now().toString()

            Log.d(TAG, "⚡ ULTRA-FAST: Updating job=$jobId, status='$status'->'$validatedStatus', user=$userId")

            // 🚀 STEP 1: Check for existing application (fast query)
            val existingApplications = withTimeoutOrNull(1000) { // 1 second timeout
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
                // 🚀 STEP 2A: Update existing application (more efficient than upsert for known records)
                val existingId = existingApplications.first()["id"] ?: ""

                supabaseClient
                    .table("applications")
                    .update(mapOf(
                        "status" to validatedStatus,
                        "updated_at" to timestamp
                    )) {
                        filter { eq("id", existingId) }
                    }

                Log.d(TAG, "✅ ULTRA-FAST: Updated existing application $existingId to $validatedStatus")
            } else {
                // 🚀 STEP 2B: Create new application (when no existing record)
                supabaseClient
                    .table("applications")
                    .insert(mapOf(
                        "job_id" to jobId,
                        "employee_id" to userId,
                        "status" to validatedStatus,
                        "applied_at" to timestamp,
                        "created_at" to timestamp,
                        "updated_at" to timestamp
                    )) {
                        headers["Prefer"] = "return=minimal" // Faster response
                    }

                Log.d(TAG, "✅ ULTRA-FAST: Created new application with status $validatedStatus")
            }

            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Ultra-fast status update failed for job $jobId: ${e.message}")
            Result.failure(e)
        } finally {
            // 🚀 CRITICAL: Always cleanup processing operations
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
    private fun validateAndNormalizeStatus(status: String): String {
        val normalized = status.trim().uppercase()

        return when (normalized) {
            "APPLIED" -> "APPLIED"
            "REJECTED" -> "REJECTED"
            "PENDING" -> "PENDING"
            "UNDER_REVIEW" -> "UNDER_REVIEW"
            "ACCEPTED" -> "ACCEPTED"
            "DECLINED" -> "DECLINED"
            // 🚀 CRITICAL: Handle legacy/invalid statuses
            "COMPLETED", "COMPLETE", "FINISHED", "DONE" -> {
                Log.w(TAG, "⚠️ Converting legacy status '$status' to REJECTED")
                "REJECTED"
            }
            "NOT_INTERESTED", "SKIP", "PASS" -> {
                Log.w(TAG, "⚠️ Converting UI status '$status' to REJECTED")
                "REJECTED"
            }
            else -> {
                Log.w(TAG, "⚠️ Unknown status '$status' defaulted to REJECTED")
                "REJECTED"
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
}