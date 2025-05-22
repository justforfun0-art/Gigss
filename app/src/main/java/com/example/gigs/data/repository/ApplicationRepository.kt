package com.example.gigs.data.repository

import android.content.ContentValues.TAG
import android.util.Log
import com.example.gigs.data.model.ApplicationStatus
import com.example.gigs.data.model.ApplicationWithJob
import com.example.gigs.data.model.Job
import com.example.gigs.data.remote.SupabaseClient
import io.github.jan.supabase.annotations.SupabaseExperimental
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton
import java.util.concurrent.ConcurrentHashMap

/**
 * Repository for managing job applications with built-in caching
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
     * Get the current user's applications with job details
     * @param limit The maximum number of applications to return. Use 0 for no limit.
     */
    suspend fun getMyApplications(limit: Int = 0): Flow<Result<List<ApplicationWithJob>>> = flow {
        try {
            Log.d(TAG, "Fetching applications for current user")
            val userId = authRepository.getCurrentUserId()
                ?: throw Exception("User not authenticated")

            val applications = supabaseClient
                .table("applications")
                .select {
                    filter {
                        eq("employee_id", userId)
                    }
                    order("applied_at", Order.DESCENDING) // Newest first
                    if (limit > 0) {
                        limit(limit.toLong())
                    }
                }
                .decodeList<ApplicationWithJob>()

            Log.d(TAG, "Found ${applications.size} applications")

            if (applications.isEmpty()) {
                emit(Result.success(emptyList()))
                return@flow
            }

            // Extract all unique job IDs
            val jobIds = applications.map { it.jobId }.distinct()
            Log.d(TAG, "Need to fetch ${jobIds.size} unique jobs")

            // Check which jobs are not in cache or have expired
            val jobsToFetch = jobIds.filter { jobId ->
                val expirationTime = cacheExpiration[jobId] ?: 0L
                !jobCache.containsKey(jobId) || System.currentTimeMillis() > expirationTime
            }

            // Fetch all needed jobs in a single batch query
            if (jobsToFetch.isNotEmpty()) {
                Log.d(TAG, "Fetching ${jobsToFetch.size} jobs in a single batch query")

                // Process in batches to avoid query size limitations
                val batchSize = 50 // Adjust based on your database limits
                val allFetchedJobs = mutableListOf<Job>()

                jobsToFetch.chunked(batchSize).forEach { batch ->
                    try {
                        val fetchedJobs = supabaseClient
                            .table("jobs")
                            .select {
                                filter {
                                    // Use proper or condition since we can't use in/list operators
                                    or {
                                        batch.forEach { jobId ->
                                            eq("id", jobId)
                                        }
                                    }
                                }
                            }
                            .decodeList<Job>()

                        allFetchedJobs.addAll(fetchedJobs)
                        Log.d(TAG, "Fetched batch of ${fetchedJobs.size} jobs")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error fetching batch of jobs: ${e.message}")
                    }
                }

                // Update cache with fetched jobs
                val currentTime = System.currentTimeMillis()
                allFetchedJobs.forEach { job ->
                    jobCache[job.id] = job
                    cacheExpiration[job.id] = currentTime + cacheDuration
                    Log.d(TAG, "Cached job ${job.id} until ${cacheExpiration[job.id]}")
                }

                // Check for missing jobs
                val fetchedJobIds = allFetchedJobs.map { it.id }.toSet()
                val missingJobIds = jobsToFetch.filter { !fetchedJobIds.contains(it) }
                if (missingJobIds.isNotEmpty()) {
                    Log.w(TAG, "Could not find ${missingJobIds.size} jobs: $missingJobIds")
                }

                Log.d(TAG, "Successfully fetched and cached ${allFetchedJobs.size} jobs")
            } else {
                Log.d(TAG, "All jobs found in cache")
            }

            // Map jobs to applications using cache
            val applicationWithJobs = applications.map { application ->
                val job = jobCache[application.jobId] ?: Job()
                application.copy(job = job)
            }

            emit(Result.success(applicationWithJobs))
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching applications: ${e.message}", e)
            emit(Result.failure(e))
        }
    }

    /**
     * Check if the current user has applied to a specific job
     */
    suspend fun hasUserAppliedToJob(jobId: String): Flow<Result<Boolean>> = flow {
        try {
            val userId = authRepository.getCurrentUserId()
                ?: throw Exception("User not authenticated")

            // First check if we've processed this application recently
            if (processedApplicationIds.contains(jobId)) {
                emit(Result.success(true))
                return@flow
            }

            // Check if application exists
            val result = supabaseClient
                .table("applications")
                .select {
                    filter {
                        eq("job_id", jobId)
                        eq("employee_id", userId)
                    }
                }
                .decodeList<Map<String, Any>>()

            // If we found any applications, the user has applied
            val hasApplied = result.isNotEmpty()

            // Cache the result if applied
            if (hasApplied) {
                processedApplicationIds.add(jobId)
            }

            emit(Result.success(hasApplied))
        } catch (e: Exception) {
            Log.e(TAG, "Error checking if user applied to job: ${e.message}", e)
            emit(Result.failure(e))
        }
    }

    /**
     * Get applications for a specific job
     */
    suspend fun getApplicationsForJob(jobId: String): Flow<Result<List<ApplicationWithJob>>> = flow {
        try {
            val userId = authRepository.getCurrentUserId() ?: throw Exception("User not authenticated")

            // First check if job is in cache
            val job = if (jobCache.containsKey(jobId) &&
                System.currentTimeMillis() <= (cacheExpiration[jobId] ?: 0L)) {
                jobCache[jobId]
            } else {
                // Fetch job and update cache
                val fetchedJob = supabaseClient
                    .table("jobs")
                    .select {
                        filter {
                            eq("id", jobId)
                        }
                    }
                    .decodeSingleOrNull<Job>()

                if (fetchedJob != null) {
                    jobCache[jobId] = fetchedJob
                    cacheExpiration[jobId] = System.currentTimeMillis() + cacheDuration
                }

                fetchedJob
            }

            if (job == null) {
                emit(Result.failure(Exception("Job not found")))
                return@flow
            }

            if (job.employerId != userId) {
                emit(Result.failure(Exception("You can only view applications for your own jobs")))
                return@flow
            }

            // Get applications for this job
            val applications = supabaseClient
                .table("applications")
                .select {
                    filter {
                        eq("job_id", jobId)
                    }
                    order("applied_at", Order.ASCENDING)
                }
                .decodeList<ApplicationWithJob>()

            // Attach job to each application (reusing the same job object)
            val applicationWithJobs = applications.map { application ->
                application.copy(job = job)
            }

            emit(Result.success(applicationWithJobs))
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching applications for job: ${e.message}", e)
            emit(Result.failure(e))
        }
    }

    /**
     * Update application status for employers (using application ID)
     */
    suspend fun updateEmployerApplicationStatus(
        applicationId: String,
        newStatus: String
    ): Flow<Result<Boolean>> = flow {
        try {
            val userId = authRepository.getCurrentUserId() ?: throw Exception("User not authenticated")

            // Get the application with job info
            val application = supabaseClient
                .table("applications")
                .select {
                    filter {
                        eq("id", applicationId)
                    }
                }
                .decodeSingleOrNull<ApplicationWithJob>()

            if (application == null) {
                emit(Result.failure(Exception("Application not found")))
                return@flow
            }

            // Get job to verify ownership from cache first
            val job = if (jobCache.containsKey(application.jobId) &&
                System.currentTimeMillis() <= (cacheExpiration[application.jobId] ?: 0L)) {
                jobCache[application.jobId]
            } else {
                getJobById(application.jobId)
            }

            if (job?.employerId != userId) {
                emit(Result.failure(Exception("You can only update applications for your own jobs")))
                return@flow
            }

            // Update application status
            supabaseClient
                .table("applications")
                .update(mapOf(
                    "status" to newStatus,
                    "updated_at" to java.time.Instant.now().toString()
                )) {
                    filter {
                        eq("id", applicationId)
                    }
                }
            emit(Result.success(true))
        } catch (e: Exception) {
            Log.e(TAG, "Error updating application status: ${e.message}", e)
            emit(Result.failure(e))
        }
    }

    /**
     * Update application status for employees (using job ID)
     */
    suspend fun updateEmployeeApplicationStatus(jobId: String, status: String): Flow<Result<Unit>> = flow {
        try {
            val userId = authRepository.getCurrentUserId() ?: throw Exception("User not authenticated")

            // Find existing application
            val existingApplications = supabaseClient
                .table("applications")
                .select {
                    filter {
                        eq("employee_id", userId)
                        eq("job_id", jobId)
                    }
                }
                .decodeList<ApplicationWithJob>()

            if (existingApplications.isNotEmpty()) {
                // Update existing application status
                supabaseClient
                    .table("applications")
                    .update(mapOf(
                        "status" to status,
                        "updated_at" to java.time.Instant.now().toString()
                    )) {
                        filter {
                            eq("employee_id", userId)
                            eq("job_id", jobId)
                        }
                    }

                Log.d(TAG, "Updated application status for job $jobId to $status")
                emit(Result.success(Unit))
            } else {
                // Create new application instead
                var success = false
                var error: Throwable? = null

                applyForJob(jobId).collect { result ->
                    if (result.isSuccess) {
                        success = true
                    } else {
                        error = result.exceptionOrNull()
                    }
                }

                if (success) {
                    emit(Result.success(Unit))
                } else {
                    emit(Result.failure(error ?: Exception("Unknown error applying for job")))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating application status: ${e.message}", e)
            emit(Result.failure(e))
        }
    }

    /**
     * Update application status - compatibility method for ViewModels
     * IMPORTANT: To be used only by employer ViewModels that expect to update by application ID
     */
    suspend fun updateApplicationStatus(applicationId: String, newStatus: String): Flow<Result<Boolean>> =
        updateEmployerApplicationStatus(applicationId, newStatus)

    /**
     * Apply for a job
     */
    /**
     * Apply for a job or update status for reconsidered rejected jobs
     */
    @OptIn(SupabaseExperimental::class)
    suspend fun applyForJob(jobId: String): Flow<Result<ApplicationWithJob>> = flow {
        try {
            val userId = authRepository.getCurrentUserId() ?: throw Exception("User not authenticated")

            Log.d(TAG, "Attempting to apply for job: $jobId by user: $userId")

            // Skip duplicate check for now to help with debugging
            // Mark as processed to prevent race conditions
            processedApplicationIds.add(jobId)

            // STEP 1: Look for existing application with detailed logging
            Log.d(TAG, "Looking for existing application for jobId=$jobId, userId=$userId")

            // Define a simple class to avoid serialization issues
            @Serializable
            data class AppData(
                val id: String,
                val job_id: String,
                val employee_id: String,
                val status: String
            )

            val existingApplications = supabaseClient
                .table("applications")
                .select {
                    filter {
                        eq("job_id", jobId)
                        eq("employee_id", userId)
                    }
                }
                .decodeList<AppData>()

            Log.d(TAG, "Found ${existingApplications.size} existing applications")
            existingApplications.forEach { app ->
                Log.d(TAG, "Existing application: id=${app.id}, jobId=${app.job_id}, status=${app.status}")
            }

            // Get job details
            val jobObject = getJobById(jobId) ?: run {
                Log.e(TAG, "Job not found with ID: $jobId")
                emit(Result.failure(Exception("Job not found")))
                return@flow
            }

            // STEP 2: Handle existing application with DIRECT SQL approach
            if (existingApplications.isNotEmpty()) {
                val existingApp = existingApplications.first()

                // If already applied, no need to update
                if (existingApp.status.equals("APPLIED", ignoreCase = true)) {
                    Log.d(TAG, "Application already has APPLIED status, no update needed")

                    // Fetch full application with job data
                    val fullApp = supabaseClient
                        .table("applications")
                        .select {
                            filter {
                                eq("id", existingApp.id)
                            }
                        }
                        .decodeSingleOrNull<ApplicationWithJob>()

                    if (fullApp != null) {
                        emit(Result.success(fullApp.copy(job = jobObject)))
                    } else {
                        Log.e(TAG, "Could not fetch full application details")
                        emit(Result.failure(Exception("Failed to fetch application details")))
                    }

                    return@flow
                }

                // CRITICAL FIX: Use PostgreSQL-style update with status as string
                Log.d(TAG, "UPDATING application ${existingApp.id} from ${existingApp.status} to APPLIED")
                val timestamp = java.time.Instant.now().toString()

                try {
                    // Direct update with minimal data model
                    val updateData = mapOf(
                        "status" to "APPLIED",
                        "updated_at" to timestamp
                    )

                    Log.d(TAG, "Executing update with data: $updateData")

                    // Execute the update
                    supabaseClient
                        .table("applications")
                        .update(updateData) {
                            filter {
                                eq("id", existingApp.id)
                            }
                            // Add return=representation to get back the updated row
                            headers["Prefer"] = "return=representation"
                        }

                    Log.d(TAG, "Update operation completed successfully")

                    // Verify the update immediately
                    val verifyApp = supabaseClient
                        .table("applications")
                        .select {
                            filter {
                                eq("id", existingApp.id)
                            }
                        }
                        .decodeSingleOrNull<AppData>()

                    if (verifyApp != null) {
                        Log.d(TAG, "Verification check: Application now has status=${verifyApp.status}")

                        if (!verifyApp.status.equals("APPLIED", ignoreCase = true)) {
                            Log.e(TAG, "STATUS UPDATE FAILED! Database still shows ${verifyApp.status}")

                            // Let's try an alternative super-simple approach
                            Log.d(TAG, "Trying direct SQL approach...")

                            // Use direct SQL-style approach as a last resort
                            val sql = """
                            UPDATE applications 
                            SET status = 'APPLIED', updated_at = '$timestamp' 
                            WHERE id = '${existingApp.id}'
                        """.trimIndent()

                            Log.d(TAG, "Executing SQL: $sql")

                            try {
                                // Note: This is a workaround - check if your Supabase client
                                // supports direct SQL execution or use RPC
                                supabaseClient
                                    .table("applications")
                                    .update("""{"status": "APPLIED", "updated_at": "$timestamp"}""") {
                                        filter {
                                            eq("id", existingApp.id)
                                        }
                                    }

                                Log.d(TAG, "Direct SQL update completed")
                            } catch (e: Exception) {
                                Log.e(TAG, "Direct SQL update failed: ${e.message}", e)
                            }
                        }
                    } else {
                        Log.e(TAG, "Could not verify update - verification query returned null")
                    }

                    // Get the updated application with job details (force refresh)
                    val updatedApp = supabaseClient
                        .table("applications")
                        .select {
                            filter {
                                eq("id", existingApp.id)
                            }
                        }
                        .decodeSingleOrNull<ApplicationWithJob>()

                    if (updatedApp != null) {
                        Log.d(TAG, "Successfully fetched updated application with status=${updatedApp.status}")
                        emit(Result.success(updatedApp.copy(job = jobObject)))
                    } else {
                        // Create a new application with applied status as fallback
                        Log.e(TAG, "Failed to fetch updated application, using fallback")
                        val fallbackApp = ApplicationWithJob(
                            id = existingApp.id,
                            jobId = jobId,
                            employeeId = userId,
                            status = ApplicationStatus.APPLIED,  // Force APPLIED status in the model
                            updatedAt = timestamp,
                            job = jobObject
                        )
                        emit(Result.success(fallbackApp))
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Exception during update operation: ${e.message}", e)

                    // If update fails, at least return a locally updated model
                    val fallbackApp = ApplicationWithJob(
                        id = existingApp.id,
                        jobId = jobId,
                        employeeId = userId,
                        status = ApplicationStatus.APPLIED,  // Force APPLIED status in the model
                        updatedAt = timestamp,
                        job = jobObject
                    )

                    emit(Result.success(fallbackApp))
                }

                return@flow
            }

            // STEP 3: Create a new application if needed
            Log.d(TAG, "No existing application found, creating new one for job: $jobId")
            val timestamp = java.time.Instant.now().toString()

            // Use Map instead of JsonObject for insert
            val applicationData = mapOf(
                "job_id" to jobId,
                "employee_id" to userId,
                "status" to "APPLIED",  // Use string for status
                "applied_at" to timestamp,
                "created_at" to timestamp,
                "updated_at" to timestamp
            )

            try {
                // Insert with minimal options
                Log.d(TAG, "Inserting new application with data: $applicationData")

                supabaseClient
                    .table("applications")
                    .insert(applicationData) {
                        headers["Prefer"] = "return=minimal"
                    }

                Log.d(TAG, "Insert completed successfully")

                // Get the created application
                val createdApplications = supabaseClient
                    .table("applications")
                    .select {
                        filter {
                            eq("job_id", jobId)
                            eq("employee_id", userId)
                        }
                    }
                    .decodeList<ApplicationWithJob>()

                if (createdApplications.isNotEmpty()) {
                    Log.d(TAG, "Successfully created application with status=${createdApplications.first().status}")
                    emit(Result.success(createdApplications.first().copy(job = jobObject)))
                } else {
                    Log.e(TAG, "Failed to create application - not found after insert")
                    emit(Result.failure(Exception("Failed to create application")))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during insert operation: ${e.message}", e)
                emit(Result.failure(e))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error applying for job: ${e.message}", e)
            emit(Result.failure(e))
        }
    }

    /**
     * Helper method to get job by ID
     * This method checks the cache first and updates it if needed
     */
    private suspend fun getJobById(jobId: String): Job? {
        return try {
            // Check cache first
            if (jobCache.containsKey(jobId) && System.currentTimeMillis() <= (cacheExpiration[jobId] ?: 0L)) {
                return jobCache[jobId]
            }

            // If not in cache or expired, fetch from database
            val job = supabaseClient
                .table("jobs")
                .select {
                    filter {
                        eq("id", jobId)
                    }
                }
                .decodeSingleOrNull<Job>()

            // Cache the job if found
            if (job != null) {
                jobCache[job.id] = job
                cacheExpiration[job.id] = System.currentTimeMillis() + cacheDuration
            }

            job
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching job by ID: ${e.message}", e)
            null
        }
    }

    /**
     * Clear all caches
     * Useful for testing or when needing to refresh all data
     */
    fun clearCache() {
        jobCache.clear()
        cacheExpiration.clear()
        processedApplicationIds.clear()
        Log.d(TAG, "Cache cleared")
    }

    /**
     * Clear processed application IDs only
     * Useful when user wants to redo applications
     */
    fun clearProcessedApplications() {
        processedApplicationIds.clear()
        Log.d(TAG, "Processed applications cache cleared")
    }
}