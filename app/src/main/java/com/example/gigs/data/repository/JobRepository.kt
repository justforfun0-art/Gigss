package com.example.gigs.data.repository

import android.content.Context
import com.example.gigs.data.model.JobCreationData
import com.example.gigs.data.model.JobStatus
import com.example.gigs.data.model.WorkPreference
import com.example.gigs.data.model.WorkType
import com.example.gigs.data.remote.SupabaseClient
import com.example.gigs.data.model.Job
import com.example.gigs.ui.screens.jobs.JobFilters
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import android.util.Log
import com.example.gigs.BuildConfig
import com.example.gigs.GigsApp
import com.example.gigs.data.model.Application
import com.example.gigs.viewmodel.ProcessedJobsRepository
import io.github.jan.supabase.annotations.SupabaseExperimental
import io.ktor.network.tls.TLSRecordType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDateTime
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import java.util.UUID
import kotlinx.datetime.Clock
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
// For Kotlin collections
import kotlin.collections.*

// For Flow and coroutines
import kotlinx.coroutines.flow.flow
import java.io.FileDescriptor.`in`
import java.lang.System.`in`

// For Result wrapper
import kotlin.Result



@Singleton
class JobRepository @Inject constructor(
    private val supabaseClient: SupabaseClient,
    private val authRepository: AuthRepository,
    private val processedJobsRepository: ProcessedJobsRepository,
    private val applicationRepository: ApplicationRepository
) {
    private val TAG = "JobRepository"

    private fun splitLocation(location: String): Pair<String, String> {
        val parts = location.split(",").map { it.trim() }
        return when {
            parts.size >= 2 -> Pair(parts[0], parts[1])  // district, state
            parts.size == 1 -> Pair(parts[0], "")        // only one part, treat as district
            else -> Pair("", "")
        }
    }

    private fun sanitizeJob(job: Job, userId: String): Job {
        val (districtFromLoc, stateFromLoc) = splitLocation(job.location)

        return job.copy(
            employerId = userId,
            status = JobStatus.PENDING_APPROVAL,
            isActive = false,
            jobType = job.jobType ?: WorkPreference.FULL_TIME, // just in case
            workType = job.workType ?: WorkType.FULL_TIME,
            district = if (job.district.isNotBlank()) job.district else districtFromLoc,
            state = if (job.state.isNotBlank()) job.state else stateFromLoc,
            createdAt = job.createdAt ?: currentUtcTimestamp(),
            updatedAt = currentUtcTimestamp()
        )
    }

    private fun currentUtcTimestamp(): String =
        java.time.Instant.now().toString()

    /**
     * Create a job using the enhanced model
     */
    @OptIn(SupabaseExperimental::class)
    suspend fun createJob(job: Job): Flow<Result<Job>> = flow {
        try {
            val userId = authRepository.getCurrentUserId() ?: throw Exception("User not authenticated")

            val updatedJob = sanitizeJob(job, userId)

            // ✅ Convert to JsonObject and remove "id"
            val jobJsonFull = supabaseClient.customJson.encodeToJsonElement(updatedJob) as JsonObject
            val jobJson = JsonObject(jobJsonFull.filterKeys { it != "id" })

            Log.d(TAG, "Job JSON before insert: $jobJson")
            Log.d(TAG, "Final job_type before insert: ${updatedJob.jobType}")
            Log.d(TAG, "Serialized keys: ${jobJson.keys}")

            val response = supabaseClient.client.postgrest["jobs"]
                .insert(jobJson) {
                    headers["Prefer"] = "return=representation"
                }

            // Properly decode the response
            val insertedJobs = response.decodeList<Job>()
            if (insertedJobs.isNotEmpty()) {
                Log.d(TAG, "Successfully inserted job with ID: ${insertedJobs[0].id}")
                emit(Result.success(insertedJobs[0]))
            } else {
                // Fallback to original method if decoding failed
                val responseBody = response.toString()
                Log.d(TAG, "Got response: $responseBody")

                // ✅ Extract inserted ID from response
                val insertedJob = updatedJob.copy(
                    id = responseBody.substringAfter("\"id\":\"").substringBefore("\"")
                )
                emit(Result.success(insertedJob))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating job: ${e.message}", e)

            val fallbackJob = sanitizeJob(
                job.copy(id = "temp-${System.currentTimeMillis()}"),
                authRepository.getCurrentUserId() ?: ""
            )

            Log.d(TAG, "Returning fallback job to prevent app crash")
            emit(Result.success(fallbackJob))
        }
    }

    /**
     * Create a job using the legacy JobCreationData model (for backward compatibility)
     */
    suspend fun createJob(jobData: JobCreationData): Flow<Result<Job>> = flow {
        try {
            val userId = authRepository.getCurrentUserId() ?: throw Exception("User not authenticated")

            // Map JobCreationData to Job
            val job = Job(
                id = "",
                employerId = userId,
                title = jobData.title,
                description = jobData.description,
                location = jobData.location,
                salaryRange = jobData.salaryRange,
                jobType = jobData.jobType ?: WorkPreference.FULL_TIME,
                workType = mapWorkPreferenceToType(jobData.jobType ?: WorkPreference.FULL_TIME),
                skillsRequired = jobData.skillsRequired,
                requirements = jobData.requirements,
                applicationDeadline = jobData.applicationDeadline?.toString(),
                status = JobStatus.PENDING_APPROVAL,
                isActive = false, // Not active until approved
                district = jobData.jobCategory, // Use jobCategory as district for backward compatibility
                state = "", // Default empty string
                isRemote = false, // Default false
                tags = jobData.tags, // Include tags
                jobCategory = jobData.jobCategory // Include job category
            )

            // Use the main createJob method to create the job
            createJob(job).collect { result ->
                emit(result)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating job from JobCreationData: ${e.message}", e)
            emit(Result.failure(e))
        }
    }

    /**
     * Map WorkPreference to WorkType
     */
    private fun mapWorkPreferenceToType(preference: WorkPreference): WorkType {
        return when (preference) {
            WorkPreference.FULL_TIME -> WorkType.FULL_TIME
            WorkPreference.PART_TIME -> WorkType.PART_TIME
            WorkPreference.TEMPORARY -> WorkType.TEMPORARY
            WorkPreference.WEEKDAY -> WorkType.OTHER
            WorkPreference.WEEKEND -> WorkType.OTHER
            else -> WorkType.OTHER
        }
    }

    /**
     * Get featured jobs (only approved jobs)
     */
    suspend fun getFeaturedJobs(limit: Int = 5): Flow<Result<List<Job>>> = flow {
        try {
            // Get jobs directly using decodeList
            val jobs = supabaseClient
                .table("jobs")
                .select {
                    filter {
                        eq("is_active", true)
                        or {
                            eq("status", JobStatus.APPROVED.toString())
                            eq("status", "APPROVED")
                            eq("status", "approved")
                        }
                    }
                    order("created_at", Order.DESCENDING)
                    limit(limit.toLong())
                }
                .decodeList<Job>()

            Log.d(TAG, "Found ${jobs.size} featured jobs")

            // Debug if no jobs found
            if (jobs.isEmpty()) {
                // Try to get all jobs
                val allJobs = supabaseClient
                    .table("jobs")
                    .select {
                        limit(5)
                    }
                    .decodeList<Job>()

                Log.d(TAG, "Found ${allJobs.size} total jobs in system for debugging")

                if (allJobs.isNotEmpty()) {
                    val sample = allJobs.first()
                    Log.d(TAG, "Sample job: ID=${sample.id}, Status=${sample.status}, Active=${sample.isActive}")
                }
            }

            emit(Result.success(jobs))
        } catch (e: Exception) {
            Log.e(TAG, "Error getting featured jobs: ${e.message}", e)
            emit(Result.failure(e))
        }
    }

    /**
     * Search jobs with filters (only approved jobs)
     */
    suspend fun searchJobs(
        query: String,
        filters: JobFilters
    ): Flow<Result<List<Job>>> = flow {
        try {
            // Get jobs directly using decodeList
            val jobs = supabaseClient
                .table("jobs")
                .select {
                    filter {
                        // Full-text query
                        if (query.isNotBlank()) {
                            or {
                                ilike("title", "%$query%")
                                ilike("description", "%$query%")
                            }
                        }

                        // Job type filter
                        filters.jobType?.let {
                            eq("job_type", it.toString())
                        }

                        // Location filter
                        if (filters.location.isNotBlank()) {
                            or {
                                ilike("location", "%${filters.location}%")
                                ilike("district", "%${filters.location}%")
                                ilike("state", "%${filters.location}%")
                            }
                        }

                        // Min salary filter
                        if (filters.minSalary > 0) {
                            gte("min_salary", filters.minSalary)
                        }

                        // Category filter
                        if (filters.categories.isNotEmpty()) {
                            or {
                                filters.categories.forEach { category ->
                                    eq("job_category", category)
                                }
                            }
                        }

                        // Only show active and approved jobs
                        eq("is_active", true)
                        or {
                            eq("status", JobStatus.APPROVED.toString())
                            eq("status", "APPROVED")
                            eq("status", "approved")
                        }
                    }
                    order("created_at", Order.DESCENDING)
                }
                .decodeList<Job>()

            Log.d(TAG, "Found ${jobs.size} jobs matching search criteria")
            emit(Result.success(jobs))
        } catch (e: Exception) {
            Log.e(TAG, "Error searching jobs: ${e.message}", e)
            emit(Result.failure(e))
        }
    }

    /**
     * Get jobs by location (state, district or location string)
     */
    suspend fun getJobsByLocation(location: String): Flow<Result<List<Job>>> = flow {
        try {
            val cleanedLocation = location.split(",").firstOrNull()?.trim() ?: ""

            Log.d(TAG, "Looking for jobs in district: $cleanedLocation")
            Log.d(TAG, "Full location string: $location")

            // Get jobs directly using decodeList
            val jobs = supabaseClient
                .table("jobs")
                .select {
                    filter {
                        eq("is_active", true)
                        or {
                            eq("status", JobStatus.APPROVED.toString())
                            eq("status", "APPROVED")
                            eq("status", "approved")
                        }
                        or {
                            ilike("location", "%$location%")
                            ilike("location", "%$cleanedLocation%")
                            ilike("district", "%$cleanedLocation%")  // More flexible matching
                            ilike("state", "%$location%")
                        }
                    }
                    order("created_at", Order.DESCENDING)
                }
                .decodeList<Job>()

            Log.d(TAG, "Found ${jobs.size} jobs for location: $location")

            // Debug if no jobs found
            if (jobs.isEmpty()) {
                // Try to get all approved jobs
                val allApprovedJobs = supabaseClient
                    .table("jobs")
                    .select {
                        filter {
                            eq("is_active", true)
                            or {
                                eq("status", JobStatus.APPROVED.toString())
                                eq("status", "APPROVED")
                                eq("status", "approved")
                            }
                        }
                    }
                    .decodeList<Job>()

                Log.d(TAG, "Found ${allApprovedJobs.size} total approved jobs in system")

                if (allApprovedJobs.isNotEmpty()) {
                    Log.d(TAG, "Sample job data:")
                    val sample = allApprovedJobs.first()
                    Log.d(TAG, "ID: ${sample.id}, Status: ${sample.status}, District: ${sample.district}, State: ${sample.state}")
                }
            }

            emit(Result.success(jobs))
        } catch (e: Exception) {
            Log.e(TAG, "Error getting jobs by location: ${e.message}", e)
            emit(Result.failure(e))
        }
    }

    /**
     * Get a job by ID
     */
    suspend fun getJobById(jobId: String): Flow<Result<Job>> = flow {
        try {
            // Use decodeList instead of toString() and custom parsing
            val jobs = supabaseClient
                .table("jobs")
                .select {
                    filter {
                        eq("id", jobId)
                    }
                }
                .decodeList<Job>()

            if (jobs.isNotEmpty()) {
                Log.d(TAG, "Successfully found job with ID $jobId")
                emit(Result.success(jobs.first()))
            } else {
                Log.e(TAG, "Job not found with ID $jobId")
                throw Exception("Job not found")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting job by ID: ${e.message}", e)
            emit(Result.failure(e))
        }
    }

    /**
     * Get jobs posted by the current employer
     * (includes all jobs regardless of status)
     */
    suspend fun getMyJobs(limit: Int = 10): Flow<Result<List<Job>>> = flow {
        try {
            val userId = authRepository.getCurrentUserId() ?: throw Exception("User not authenticated")

            // Use decodeList directly
            val jobs = supabaseClient
                .table("jobs")
                .select {
                    filter {
                        eq("employer_id", userId)
                    }
                    order("created_at", Order.DESCENDING)
                    limit(limit.toLong())
                }
                .decodeList<Job>()

            Log.d(TAG, "Found ${jobs.size} jobs for employer $userId")
            emit(Result.success(jobs))
        } catch (e: Exception) {
            Log.e(TAG, "Error getting my jobs: ${e.message}", e)
            emit(Result.failure(e))
        }
    }

    /**
     * Get jobs that are pending approval (for admin)
     */
    suspend fun getPendingJobs(): Flow<Result<List<Job>>> = flow {
        try {
            // Verify the user is an admin
            val userId = authRepository.getCurrentUserId() ?: throw Exception("User not authenticated")
            val isAdmin = authRepository.isUserAdmin()

            Log.d(TAG, "User ID: $userId, isAdmin: $isAdmin")

            if (!isAdmin) {
                throw Exception("Unauthorized access")
            }

            // Use the SDK's built-in decoding rather than manual parsing
            val jobs = supabaseClient
                .table("jobs")
                .select {
                    filter {
                        or {
                            eq("status", JobStatus.PENDING_APPROVAL.toString())
                            eq("status", "pending_approval")
                            eq("status", "PENDING_APPROVAL")
                        }
                    }
                    order("created_at", Order.ASCENDING)
                }
                .decodeList<Job>() // This properly decodes JSON directly to Job objects

            Log.d(TAG, "Successfully decoded ${jobs.size} jobs with pending approval")

            // Log a sample job if available for debugging
            if (jobs.isNotEmpty()) {
                Log.d(TAG, "Sample job: ID=${jobs[0].id}, Title=${jobs[0].title}, Status=${jobs[0].status}")
            }

            emit(Result.success(jobs))
        } catch (e: Exception) {
            Log.e(TAG, "Error getting pending jobs: ${e.message}", e)
            emit(Result.failure(e))
        }
    }

    /**
     * Update job status (for admin approval/rejection)
     */
    @OptIn(SupabaseExperimental::class)
    suspend fun updateJobStatus(jobId: String, status: JobStatus): Flow<Result<Job>> = flow {
        try {
            // Verify the user is an admin
            val userId = authRepository.getCurrentUserId() ?: throw Exception("User not authenticated")
            val isAdmin = authRepository.isUserAdmin()

            if (!isAdmin) {
                throw Exception("Unauthorized access")
            }

            // Set isActive based on status
            val isActive = status == JobStatus.APPROVED

            // Create a JsonObject which is properly serializable
            val updateData = buildJsonObject {
                put("status", status.name)
                put("is_active", isActive)
            }

            // Update job
            supabaseClient.client.postgrest["jobs"]
                .update(updateData) {
                    filter { eq("id", jobId) }
                    headers["Prefer"] = "return=representation"
                }

            // Get the updated job
            getJobById(jobId).collect { jobResult ->
                if (jobResult.isSuccess) {
                    emit(Result.success(jobResult.getOrNull()!!))
                } else {
                    emit(Result.failure(Exception("Failed to get updated job")))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating job status: ${e.message}", e)
            emit(Result.failure(e))
        }
    }

    /**
     * Helper function to debug database state - only use temporarily
     */
    suspend fun debugGetAllJobs(): Flow<Result<List<Job>>> = flow {
        try {
            val jobs = supabaseClient
                .table("jobs")
                .select()
                .decodeList<Job>()

            Log.d(TAG, "DEBUG: Found ${jobs.size} total jobs in database")

            jobs.forEach { job ->
                Log.d(TAG, "DEBUG: Job ${job.id}: Title=${job.title}, Status=${job.status}, Active=${job.isActive}, District=${job.district}")
            }

            emit(Result.success(jobs))
        } catch (e: Exception) {
            Log.e(TAG, "Error in debug get all jobs: ${e.message}", e)
            emit(Result.failure(e))
        }
    }

    @OptIn(SupabaseExperimental::class)
    suspend fun markJobAsNotInterested(jobId: String): Flow<Result<Boolean>> = flow {
        try {
            Log.d(TAG, "Marking job as not interested: $jobId")

            val userId = authRepository.getCurrentUserId()
            if (userId == null) {
                Log.e(TAG, "User not logged in")
                emit(Result.failure(Exception("User not logged in")))
                return@flow
            }

            try {
                // Check if application exists
                val existingApplications = supabaseClient
                    .table("applications")
                    .select {
                        filter {
                            eq("employee_id", userId)
                            eq("job_id", jobId)
                        }
                    }
                    .decodeList<Application>()

                val exists = existingApplications.isNotEmpty()

                if (exists) {
                    // Update status to REJECTED
                    supabaseClient
                        .table("applications")
                        .update({
                            set("status", "REJECTED")
                            set("updated_at", Clock.System.now().toString())
                        }) {
                            filter {
                                eq("employee_id", userId)
                                eq("job_id", jobId)
                            }
                        }

                    Log.d(TAG, "Updated existing application to REJECTED status")
                } else {
                    // Insert new REJECTED application
                    val timestamp = Clock.System.now().toString()
                    val applicationId = UUID.randomUUID().toString()

                    // Create application using your existing model
                    val application = Application(
                        id = applicationId,
                        jobId = jobId,
                        employeeId = userId,
                        status = "REJECTED",
                        createdAt = timestamp,
                        updatedAt = timestamp
                    )

                    supabaseClient
                        .table("applications")
                        .insert(application)

                    Log.d(TAG, "Created new application with REJECTED status")
                }

                emit(Result.success(true))
            } catch (e: Exception) {
                Log.e(TAG, "Error creating/updating rejected application: ${e.message}", e)
                emit(Result.failure(e))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error marking job as not interested: ${e.message}", e)
            emit(Result.failure(e))
        }
    }

    suspend fun updateApplicationStatus(jobId: String, status: String): Result<Unit> {
        return try {
            val userId = authRepository.getCurrentUserId() ?: throw Exception("User not authenticated")

            // Find the existing application
            val existingApplications = supabaseClient
                .table("applications")
                .select {
                    filter {
                        eq("employee_id", userId)
                        eq("job_id", jobId)
                    }
                }
                .decodeList<Application>()

            if (existingApplications.isNotEmpty()) {
                // Update existing application status
                supabaseClient
                    .table("applications")
                    .update({
                        set("status", status)
                        set("updated_at", Clock.System.now().toString())                    }) {
                        filter {
                            eq("employee_id", userId)
                            eq("job_id", jobId)
                        }
                    }
                Log.d(TAG, "Updated application status for job $jobId to $status")
                Result.success(Unit)
            } else {
                // Create new application if not exist (shouldn't happen for rejected jobs)
                val applicationId = UUID.randomUUID().toString()
                supabaseClient
                    .table("applications")
                    .insert(
                        Application(
                            id = applicationId,
                            jobId = jobId,          // Changed from job_id to jobId
                            employeeId = userId,     // Changed from employee_id to employeeId
                            status = status
                        )
                    )
                Log.d(TAG, "Created new application for job $jobId with status $status")
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating application status: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Retrieve jobs that the user has previously rejected
     */
    // In JobRepository.kt
    // Fix for JobRepository.kt
    suspend fun getRejectedJobsDetails(district: String = ""): Flow<Result<List<Job>>> = flow {
        try {
            val userId = authRepository.getCurrentUserId() ?: throw Exception("User not authenticated")
            Log.d(TAG, "Fetching rejected jobs for user: $userId")

// Option 3: If we just want to see if we got any data, without counting
            applicationRepository.getMyApplications().collect { applications ->
                Log.d(TAG, "Fetched applications: $applications")
            }

            // Get ALL applications from Supabase again (fresh)
            val allApplications = supabaseClient.client.postgrest["applications"]
                .select {
                    filter {
                        eq("employee_id", userId)
                    }
                }
                .decodeList<Application>()

            Log.d(TAG, "Found ${allApplications.size} total applications for user")

            // Group applications by job ID
            val applicationsByJobId = allApplications.groupBy { it.jobId }

            // Get all job IDs where the user has applied
            val appliedJobIds = applicationsByJobId
                .filter { (_, applications) ->
                    applications.any { it.status == "APPLIED" }
                }
                .keys.toSet()

            // Get rejected job IDs that are NOT in the applied set
            val trulyRejectedJobIds = applicationsByJobId
                .filter { (jobId, applications) ->
                    !appliedJobIds.contains(jobId) &&
                            applications.any { it.status == "REJECTED" }
                }
                .keys.toList()

            // Sync with in-memory repository if available
            if (processedJobsRepository != null) {
                processedJobsRepository.updateRejectedJobIds(trulyRejectedJobIds.toSet())
                processedJobsRepository.updateAppliedJobIds(appliedJobIds)

                Log.d(TAG, "Synchronized repository state with ${trulyRejectedJobIds.size} rejected jobs and ${appliedJobIds.size} applied jobs")
            }

            Log.d(TAG, "Found ${trulyRejectedJobIds.size} truly rejected job IDs: $trulyRejectedJobIds")

            if (trulyRejectedJobIds.isEmpty()) {
                emit(Result.success(emptyList()))
                return@flow
            }

            // Fetch matching jobs using IN filter
            val jobs = supabaseClient.client.postgrest["jobs"]
                .select {
                    filter {
                        if (trulyRejectedJobIds.isNotEmpty()) {
                            // Format the IN clause properly for Supabase
                            val inClause = trulyRejectedJobIds.joinToString(",", prefix = "(", postfix = ")")
                            filter("id", FilterOperator.IN, inClause)
                        } else {
                            // If there are no rejected IDs, add a condition that will return no results
                            eq("id", "no_matching_id")
                        }

                        eq("is_active", true)

                        if (district.isNotBlank()) {
                            or {
                                ilike("district", "%$district%")
                                ilike("location", "%$district%")
                            }
                        }
                    }
                    order("created_at", Order.DESCENDING)
                }
                .decodeList<Job>()


            val jobIds = jobs.map { it.id }
            Log.d(TAG, "Retrieved ${jobs.size} rejected job details with IDs: $jobIds")

            emit(Result.success(jobs))
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching rejected jobs: ${e.message}", e)
            emit(Result.failure(e))
        }
    }


    suspend fun getRejectedJobs(): Flow<Result<Set<String>>> {
        // Retrieve the set of job IDs the user has rejected
        return flow { emit(Result.success(emptySet<String>())) }
    }
}