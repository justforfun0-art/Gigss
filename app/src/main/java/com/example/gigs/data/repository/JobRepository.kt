// 🚀 COMPLETE JobRepository.kt - All Functions with Clean Architecture

package com.example.gigs.data.repository

import android.util.Log
import com.example.gigs.data.model.Application
import com.example.gigs.data.model.ApplicationStatus
import com.example.gigs.data.model.ApplicationWithJob
import com.example.gigs.data.model.Job
import com.example.gigs.data.model.JobAlert
import com.example.gigs.data.model.JobCreationData
import com.example.gigs.data.model.JobStatus
import com.example.gigs.data.model.WorkPreference
import com.example.gigs.data.model.WorkType
import com.example.gigs.data.remote.SupabaseClient
import com.example.gigs.data.util.PerformanceUtils
import com.example.gigs.ui.screens.jobs.JobFilters
import com.example.gigs.viewmodel.ProcessedJobsRepository
import io.github.jan.supabase.annotations.SupabaseExperimental
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.catch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.util.concurrent.ConcurrentHashMap
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class UserJobDataBatch(
    val jobs: List<Job>,
    val applications: List<ApplicationWithJob>,
    val rejectedJobIds: Set<String>
)

@Singleton
class JobRepository @Inject constructor(
    internal val supabaseClient: SupabaseClient,
    private val authRepository: AuthRepository,
    private val processedJobsRepository: ProcessedJobsRepository,
    private val applicationRepository: ApplicationRepository,
    private val reconsiderationStorage: ReconsiderationStorageManager // 🚀 NEW

) {
    private val TAG = "JobRepository"

    // 🚀 PERFORMANCE: Caching infrastructure
    private val jobsCache = PerformanceUtils.LRUCache<String, List<Job>>(
        maxSize = 50,
        expiryTimeMs = 5 * 60 * 1000L // 5 minutes
    )
    private val jobDetailsCache = PerformanceUtils.LRUCache<String, Job>(
        maxSize = 100,
        expiryTimeMs = 10 * 60 * 1000L // 10 minutes
    )
    private val applicationsCache = PerformanceUtils.LRUCache<String, List<ApplicationWithJob>>(
        maxSize = 20,
        expiryTimeMs = 2 * 60 * 1000L // 2 minutes
    )

    // 🚀 OPERATION TRACKING: Single mechanism for all operations
    private val operationTracking = ConcurrentHashMap<String, Long>()
    private val pendingRequests = ConcurrentHashMap<String, Deferred<Result<List<Job>>>>()

    // 🚀 PERFORMANCE: Memory pressure handling
    private val memoryCallback = {
        Log.d(TAG, "Memory pressure detected, clearing caches")
        clearAllCaches()
    }

    init {
        PerformanceUtils.MemoryMonitor.addLowMemoryCallback(memoryCallback)
    }

    // 🚀 BATCH OPERATIONS
    suspend fun getUserJobDataBatchOptimized(userId: String, district: String): UserJobDataBatch {
        return try {
            PerformanceUtils.PerformanceMetrics.measureOperation("batch_user_data_optimized", "database") {
                coroutineScope {
                    val jobsDeferred = async(Dispatchers.IO) {
                        try {
                            withTimeoutOrNull(3000) {
                                getJobsByLocationDirectSimple(district)
                            } ?: emptyList()
                        } catch (e: Exception) {
                            Log.e(TAG, "Jobs fetch failed: ${e.message}")
                            emptyList()
                        }
                    }

                    val applicationsDeferred = async(Dispatchers.IO) {
                        try {
                            withTimeoutOrNull(2000) {
                                getApplicationsDirectSimple(userId)
                            } ?: emptyList()
                        } catch (e: Exception) {
                            Log.e(TAG, "Applications fetch failed: ${e.message}")
                            emptyList()
                        }
                    }

                    val rejectedDeferred = async(Dispatchers.IO) {
                        try {
                            withTimeoutOrNull(1000) {
                                processedJobsRepository.getRejectedJobIds()
                            } ?: emptySet()
                        } catch (e: Exception) {
                            Log.e(TAG, "Rejected jobs fetch failed: ${e.message}")
                            emptySet()
                        }
                    }

                    UserJobDataBatch(
                        jobs = jobsDeferred.await(),
                        applications = applicationsDeferred.await(),
                        rejectedJobIds = rejectedDeferred.await()
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Batch operation failed completely: ${e.message}")
            UserJobDataBatch(
                jobs = emptyList(),
                applications = emptyList(),
                rejectedJobIds = emptySet()
            )
        }
    }

    // 🚀 CORE JOB FETCHING METHODS
    suspend fun getJobsByLocationDirectPublic(location: String): List<Job> {
        return getJobsByLocationDirectSimple(location)
    }

    /**
     * 🚀 NEW: Fetch rejected jobs eligible for one-time reconsideration
     * This method filters out jobs that have already been reconsidered
     */
    suspend fun fetchEligibleRejectedJobs(
        district: String = "",
        processedJobsRepository: ProcessedJobsRepository
    ): Flow<Result<List<Job>>> = flow {
        try {
            val userId = authRepository.getCurrentUserId() ?: throw Exception("User not authenticated")
            Log.d(TAG, "🔄 Fetching jobs eligible for one-time reconsideration for user: ${userId.take(8)}...")

            // Step 1: Get all NOT_INTERESTED applications for this user
            val notInterestedApplications = supabaseClient
                .table("applications")
                .select {
                    filter {
                        eq("employee_id", userId)
                        eq("status", "NOT_INTERESTED")
                    }
                    order("updated_at", Order.DESCENDING)
                    limit(100)
                }
                .decodeList<Application>()

            Log.d(TAG, "Found ${notInterestedApplications.size} NOT_INTERESTED applications")

            if (notInterestedApplications.isEmpty()) {
                emit(Result.success(emptyList()))
                return@flow
            }

            // Step 2: Load reconsidered job IDs from persistent storage
            val reconsideredJobIds = reconsiderationStorage.loadReconsideredJobIds()
            Log.d(TAG, "Found ${reconsideredJobIds.size} jobs already reconsidered")

            // Step 3: Filter out already reconsidered jobs
            val eligibleJobIds = notInterestedApplications
                .map { it.jobId }
                .filter { jobId -> !reconsideredJobIds.contains(jobId) }
                .distinct()

            Log.d(TAG, "📊 ELIGIBILITY FILTER:")
            Log.d(TAG, "   Total NOT_INTERESTED: ${notInterestedApplications.size}")
            Log.d(TAG, "   Already reconsidered: ${reconsideredJobIds.size}")
            Log.d(TAG, "   Eligible for reconsideration: ${eligibleJobIds.size}")

            if (eligibleJobIds.isEmpty()) {
                // Update repository to reflect no eligible jobs
                processedJobsRepository.updateRejectedJobIds(emptySet())
                emit(Result.success(emptyList()))
                return@flow
            }

            // Step 4: Fetch job details for eligible jobs
            val eligibleJobs = fetchJobDetailsBatch(eligibleJobIds, district)

            // Step 5: Update repository with eligible jobs only
            processedJobsRepository.updateRejectedJobIds(eligibleJobs.map { it.id }.toSet())

            Log.d(TAG, "✅ Retrieved ${eligibleJobs.size} jobs eligible for reconsideration")
            emit(Result.success(eligibleJobs))

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error fetching eligible rejected jobs: ${e.message}", e)
            emit(Result.failure(e))
        }
    }

    /**
     * 🚀 NEW: Batch fetch job details with district filtering
     */
    private suspend fun fetchJobDetailsBatch(
        jobIds: List<String>,
        district: String = ""
    ): List<Job> {
        return try {
            if (jobIds.isEmpty()) return emptyList()

            val jobs = if (jobIds.size == 1) {
                // Single job query
                val job = supabaseClient
                    .table("jobs")
                    .select {
                        filter {
                            eq("id", jobIds.first())
                            eq("is_active", true)
                            if (district.isNotBlank()) {
                                or {
                                    ilike("district", "%$district%")
                                    ilike("location", "%$district%")
                                }
                            }
                        }
                    }
                    .decodeSingleOrNull<Job>()
                listOfNotNull(job)
            } else {
                // Batch query with chunking for large sets
                val jobs = mutableListOf<Job>()

                jobIds.chunked(10).forEach { chunk ->
                    try {
                        val inClause = chunk.joinToString(",") { "\"$it\"" }
                        val batchJobs = supabaseClient
                            .table("jobs")
                            .select {
                                filter {
                                    filter("id", FilterOperator.IN, "($inClause)")
                                    eq("is_active", true)
                                    if (district.isNotBlank()) {
                                        or {
                                            ilike("district", "%$district%")
                                            ilike("location", "%$district%")
                                        }
                                    }
                                }
                            }
                            .decodeList<Job>()
                        jobs.addAll(batchJobs)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error fetching batch: ${e.message}")

                        // Fallback to individual queries for failed chunks
                        chunk.forEach { jobId ->
                            try {
                                val job = supabaseClient
                                    .table("jobs")
                                    .select {
                                        filter {
                                            eq("id", jobId)
                                            eq("is_active", true)
                                            if (district.isNotBlank()) {
                                                or {
                                                    ilike("district", "%$district%")
                                                    ilike("location", "%$district%")
                                                }
                                            }
                                        }
                                    }
                                    .decodeSingleOrNull<Job>()
                                job?.let { jobs.add(it) }
                            } catch (individualError: Exception) {
                                Log.e(TAG, "Failed to fetch individual job $jobId: ${individualError.message}")
                            }
                        }
                    }
                }

                jobs.sortedByDescending { it.createdAt ?: "" }
            }

            jobs
        } catch (e: Exception) {
            Log.e(TAG, "Error in batch fetch: ${e.message}")
            emptyList()
        }
    }

    /**
     * 🚀 NEW: Mark job as reconsidered in both memory and persistent storage
     */
    suspend fun markJobAsReconsidered(jobId: String): Boolean {
        return try {
            Log.d(TAG, "🔄 Marking job $jobId as reconsidered (permanent)")

            // Update in-memory state
            processedJobsRepository.markJobAsReconsidered(jobId)

            // Save to persistent storage
            reconsiderationStorage.addReconsideredJobId(jobId)

            Log.d(TAG, "✅ Job $jobId marked as reconsidered permanently")
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to mark job as reconsidered: ${e.message}")
            false
        }
    }

    /**
     * 🚀 NEW: Mark multiple jobs as reconsidered (for session completion)
     */
    suspend fun markMultipleJobsAsReconsidered(jobIds: Collection<String>): Boolean {
        return try {
            if (jobIds.isEmpty()) return true

            Log.d(TAG, "🔄 Marking ${jobIds.size} jobs as reconsidered (permanent)")

            // Update in-memory state
            processedJobsRepository.markMultipleJobsAsReconsidered(jobIds)

            // Save to persistent storage
            reconsiderationStorage.addMultipleReconsideredJobIds(jobIds)

            Log.d(TAG, "✅ ${jobIds.size} jobs marked as reconsidered permanently")
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to mark multiple jobs as reconsidered: ${e.message}")
            false
        }
    }

    /**
     * 🚀 NEW: Check if a job has been reconsidered
     */
    suspend fun isJobReconsidered(jobId: String): Boolean {
        return try {
            reconsiderationStorage.isJobReconsidered(jobId)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking if job is reconsidered: ${e.message}")
            false
        }
    }

    /**
     * 🚀 NEW: Get reconsideration statistics for UI
     */
    suspend fun getReconsiderationStatistics(): ReconsiderationStatistics {
        return try {
            val userId = authRepository.getCurrentUserId()
            if (userId == null) {
                return ReconsiderationStatistics(0, 0, 0)
            }

            // Get total NOT_INTERESTED jobs
            val totalNotInterested = supabaseClient
                .table("applications")
                .select {
                    filter {
                        eq("employee_id", userId)
                        eq("status", "NOT_INTERESTED")
                    }
                }
                .decodeList<Application>()
                .map { it.jobId }
                .distinct()
                .size

            // Get reconsidered count from storage
            val reconsideredCount = reconsiderationStorage.loadReconsideredJobIds().size

            // Calculate eligible
            val eligibleCount = maxOf(0, totalNotInterested - reconsideredCount)

            ReconsiderationStatistics(
                totalRejected = totalNotInterested,
                reconsidered = reconsideredCount,
                eligibleForReconsideration = eligibleCount
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting reconsideration statistics: ${e.message}")
            ReconsiderationStatistics(0, 0, 0)
        }
    }



    /**
     * 🚀 NEW: Reset reconsideration data (for testing/debugging)
     */
    suspend fun resetReconsiderationData(): Boolean {
        return try {
            Log.w(TAG, "🔄 RESETTING all reconsideration data")

            // Clear persistent storage
            reconsiderationStorage.clearReconsideredJobIds()

            // Clear in-memory state
            processedJobsRepository.initializeReconsideredJobs(emptySet())

            Log.w(TAG, "✅ All reconsideration data reset")
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to reset reconsideration data: ${e.message}")
            false
        }
    }

    /**
     * 🚀 NEW: Export reconsideration data for backup
     */
    suspend fun exportReconsiderationData(): ReconsiderationBackupData {
        return try {
            reconsiderationStorage.exportReconsiderationData()
        } catch (e: Exception) {
            Log.e(TAG, "Error exporting reconsideration data: ${e.message}")
            ReconsiderationBackupData(emptySet(), 0L)
        }
    }

    /**
     * 🚀 NEW: Import reconsideration data from backup
     */
    suspend fun importReconsiderationData(backupData: ReconsiderationBackupData): Boolean {
        return try {
            reconsiderationStorage.importReconsiderationData(backupData)

            // Update in-memory state
            processedJobsRepository.initializeReconsideredJobs(backupData.reconsideredJobIds)

            Log.d(TAG, "✅ Imported reconsideration data: ${backupData.reconsideredJobIds.size} jobs")
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to import reconsideration data: ${e.message}")
            false
        }
    }


    private suspend fun getJobsByLocationDirectSimple(location: String): List<Job> {
        return try {
            val cacheKey = "jobs_district_$location"
            jobsCache.get(cacheKey)?.let { return it }

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
                            ilike("district", "%$location%")
                            ilike("state", "%$location%")
                        }
                    }
                    order("updated_at", Order.DESCENDING)
                    limit(50)
                }
                .decodeList<Job>()

            jobsCache.put(cacheKey, jobs)
            Log.d(TAG, "Fetched and cached ${jobs.size} jobs for location: $location")
            jobs
        } catch (e: Exception) {
            Log.e(TAG, "Error in getJobsByLocationDirectSimple: ${e.message}")
            emptyList()
        }
    }

    suspend fun getFeaturedJobsDirect(limit: Int = 5): List<Job> {
        return try {
            val cacheKey = "featured_jobs_$limit"
            jobsCache.get(cacheKey)?.let { return it }

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

            jobsCache.put(cacheKey, jobs)
            Log.d(TAG, "Found ${jobs.size} featured jobs directly")
            jobs
        } catch (e: Exception) {
            Log.e(TAG, "Error getting featured jobs directly: ${e.message}")
            emptyList()
        }
    }

    suspend fun getJobByIdDirect(jobId: String): Job? {
        return try {
            jobDetailsCache.get(jobId)?.let { return it }

            val jobs = supabaseClient
                .table("jobs")
                .select {
                    filter { eq("id", jobId) }
                }
                .decodeList<Job>()

            if (jobs.isNotEmpty()) {
                val job = jobs.first()
                jobDetailsCache.put(jobId, job)
                job
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting job by ID directly: ${e.message}")
            null
        }
    }

    suspend fun getMyJobsDirect(limit: Int = 10): List<Job> {
        return try {
            val userId = authRepository.getCurrentUserId() ?: return emptyList()

            supabaseClient
                .table("jobs")
                .select {
                    filter { eq("employer_id", userId) }
                    order("created_at", Order.DESCENDING)
                    limit(limit.toLong())
                }
                .decodeList<Job>()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting my jobs directly: ${e.message}")
            emptyList()
        }
    }

    // 🚀 APPLICATION METHODS
    suspend fun getApplicationsForUserDirectPublic(userId: String): List<ApplicationWithJob> {
        return getApplicationsDirectSimple(userId)
    }

    private suspend fun getApplicationsDirectSimple(userId: String): List<ApplicationWithJob> {
        return try {
            val cacheKey = "applications_$userId"
            applicationsCache.get(cacheKey)?.let { return it }

            @Serializable
            data class ApplicationResponse(
                val id: String,
                val job_id: String,
                val employee_id: String,
                val status: String,
                val applied_at: String? = null,
                val created_at: String? = null,
                val updated_at: String? = null
            )

            val applications = supabaseClient
                .table("applications")
                .select {
                    filter { eq("employee_id", userId) }
                    order("updated_at", Order.DESCENDING)
                    limit(50)
                }
                .decodeList<ApplicationResponse>()

            Log.d(TAG, "Found ${applications.size} applications")

            if (applications.isEmpty()) {
                return emptyList()
            }

            val jobIds = applications.map { it.job_id }.distinct()
            Log.d(TAG, "Need to fetch ${jobIds.size} unique jobs")

            if (jobIds.size <= 10) {
                val jobs = try {
                    val inClause = jobIds.joinToString(",")
                    supabaseClient
                        .table("jobs")
                        .select {
                            filter {
                                filter("id", FilterOperator.IN, "($inClause)")
                            }
                        }
                        .decodeList<Job>()
                } catch (e: Exception) {
                    Log.w(TAG, "Batch fetch failed, trying individual requests: ${e.message}")
                    val individualJobs = mutableListOf<Job>()
                    jobIds.take(5).forEach { jobId ->
                        try {
                            getJobByIdDirect(jobId)?.let { individualJobs.add(it) }
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to fetch job $jobId: ${e.message}")
                        }
                    }
                    individualJobs
                }

                jobs.forEach { job ->
                    jobDetailsCache.put(job.id, job)
                }

                val jobMap = jobs.associateBy { it.id }
                val applicationsWithJobs = applications.mapNotNull { app ->
                    jobMap[app.job_id]?.let { job ->
                        ApplicationWithJob(
                            id = app.id,
                            jobId = app.job_id,
                            employeeId = app.employee_id,
                            status = try {
                                ApplicationStatus.valueOf(app.status.uppercase())
                            } catch (e: Exception) {
                                ApplicationStatus.APPLIED
                            },
                            appliedAt = app.applied_at,
                            updatedAt = app.updated_at,
                            job = job
                        )
                    }
                }

                applicationsCache.put(cacheKey, applicationsWithJobs)
                applicationsWithJobs
            } else {
                Log.w(TAG, "Too many jobs to fetch (${jobIds.size}), returning applications without job details")
                emptyList()
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error in getApplicationsDirectSimple: ${e.message}")
            emptyList()
        }
    }

    // 🚀 JOB REJECTION METHOD - DATABASE OPERATIONS ONLY
    @OptIn(SupabaseExperimental::class)
    suspend fun markJobAsNotInterested(jobId: String): Boolean {
        val operationKey = "not_interested_$jobId"
        val currentTime = System.currentTimeMillis()

        if (isOperationInProgress(operationKey)) {
            Log.w(TAG, "🚫 NOT_INTERESTED operation already in progress for job $jobId")
            return true
        }

        operationTracking[operationKey] = currentTime

        return try {
            val userId = authRepository.getCurrentUserId() ?: return false
            val timestamp = java.time.Instant.now().toString()

            Log.d(TAG, "⚡ REPOSITORY: Database NOT_INTERESTED for jobId: $jobId, userId: $userId")

            @Serializable
            data class ApplicationCheck(
                val id: String,
                val status: String
            )

            val existingApplications = withTimeoutOrNull(1000) {
                supabaseClient
                    .table("applications")
                    .select(columns = io.github.jan.supabase.postgrest.query.Columns.list("id", "status")) {
                        filter {
                            eq("job_id", jobId)
                            eq("employee_id", userId)
                        }
                        limit(1)
                    }
                    .decodeList<ApplicationCheck>()
            } ?: emptyList()

            if (existingApplications.isNotEmpty()) {
                val existingApp = existingApplications.first()
                supabaseClient
                    .table("applications")
                    .update(mapOf(
                        "status" to "NOT_INTERESTED",
                        "updated_at" to timestamp
                    )) {
                        filter { eq("id", existingApp.id) }
                    }
                Log.d(TAG, "✅ REPOSITORY: Updated existing application to NOT_INTERESTED")
            } else {
                supabaseClient
                    .table("applications")
                    .insert(mapOf(
                        "job_id" to jobId,
                        "employee_id" to userId,
                        "status" to "NOT_INTERESTED",
                        "applied_at" to timestamp,
                        "created_at" to timestamp,
                        "updated_at" to timestamp
                    )) {
                        headers["Prefer"] = "return=minimal"
                    }
                Log.d(TAG, "✅ REPOSITORY: Created new NOT_INTERESTED application")
            }

            // 🚀 NEW: If this action happened during reconsideration, mark as reconsidered
            if (processedJobsRepository.isShowingRejectedJobs.value) {
                markJobAsReconsidered(jobId)
                Log.d(TAG, "🔄 Job $jobId marked as reconsidered due to action in reconsideration mode")
            }

            // Clear relevant caches
            applicationsCache.remove("applications_$userId")
            Log.d(TAG, "✅ REPOSITORY: Database NOT_INTERESTED completed for job $jobId")
            true

        } catch (e: Exception) {
            Log.e(TAG, "❌ Repository database NOT_INTERESTED failed for $jobId: ${e.message}")
            false
        } finally {
            operationTracking.remove(operationKey)
        }
    }

    // 🚀 JOB CREATION METHODS
    @OptIn(SupabaseExperimental::class)
    suspend fun createJob(job: Job): Flow<Result<Job>> = flow {
        try {
            val userId = authRepository.getCurrentUserId() ?: throw Exception("User not authenticated")
            val updatedJob = sanitizeJob(job, userId)
            val jobJsonFull = supabaseClient.customJson.encodeToJsonElement(updatedJob) as JsonObject
            val jobJson = JsonObject(jobJsonFull.filterKeys { it != "id" })

            Log.d(TAG, "Job JSON before insert: $jobJson")

            val response = supabaseClient.client.postgrest["jobs"]
                .insert(jobJson) {
                    headers["Prefer"] = "return=representation"
                }

            val insertedJobs = response.decodeList<Job>()
            if (insertedJobs.isNotEmpty()) {
                Log.d(TAG, "Successfully inserted job with ID: ${insertedJobs[0].id}")
                clearLocationCaches()
                emit(Result.success(insertedJobs[0]))
            } else {
                val responseBody = response.toString()
                val insertedJob = updatedJob.copy(
                    id = responseBody.substringAfter("\"id\":\"").substringBefore("\"")
                )
                clearLocationCaches()
                emit(Result.success(insertedJob))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating job: ${e.message}", e)
            val fallbackJob = sanitizeJob(
                job.copy(id = "temp-${System.currentTimeMillis()}"),
                authRepository.getCurrentUserId() ?: ""
            )
            emit(Result.success(fallbackJob))
        }
    }

    suspend fun createJob(jobData: JobCreationData): Flow<Result<Job>> = flow {
        try {
            val userId = authRepository.getCurrentUserId() ?: throw Exception("User not authenticated")

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
                isActive = false,
                district = jobData.jobCategory,
                state = "",
                isRemote = false,
                tags = jobData.tags,
                jobCategory = jobData.jobCategory
            )

            createJob(job).collect { result ->
                emit(result)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating job from JobCreationData: ${e.message}", e)
            emit(Result.failure(e))
        }
    }

    @OptIn(SupabaseExperimental::class)
    suspend fun createJobAlert(alert: JobAlert): Flow<Result<Unit>> = flow {
        try {
            val userId = authRepository.getCurrentUserId() ?: throw Exception("User not authenticated")

            val alertWithUser = alert.copy(
                id = if (alert.id.isBlank()) UUID.randomUUID().toString() else alert.id,
                userId = userId,
                createdAt = System.currentTimeMillis()
            )

            val workTypesJson = JsonArray(
                alertWithUser.workTypes.map { JsonPrimitive(it.toString()) }
            )

            val keywordsJson = JsonArray(
                alertWithUser.keywords.map { JsonPrimitive(it) }
            )

            val alertJson = buildJsonObject {
                put("id", alertWithUser.id)
                put("user_id", alertWithUser.userId)
                put("district", alertWithUser.district)
                put("work_types", workTypesJson)
                put("keywords", keywordsJson)
                alertWithUser.minWage?.let { put("min_wage", it) }
                alertWithUser.maxWage?.let { put("max_wage", it) }
                put("is_active", alertWithUser.isActive)
                put("created_at", java.time.Instant.ofEpochMilli(alertWithUser.createdAt).toString())
            }

            supabaseClient.client.postgrest["job_alerts"]
                .insert(alertJson) {
                    headers["Prefer"] = "return=minimal"
                }

            Log.d(TAG, "Successfully created job alert for user $userId")
            emit(Result.success(Unit))
        } catch (e: Exception) {
            Log.e(TAG, "Error creating job alert: ${e.message}", e)
            emit(Result.failure(e))
        }
    }

    // 🚀 SEARCH AND FILTER METHODS
    suspend fun searchJobs(query: String, filters: JobFilters): Flow<Result<List<Job>>> = flow {
        try {
            val jobs = supabaseClient
                .table("jobs")
                .select {
                    filter {
                        if (query.isNotBlank()) {
                            or {
                                ilike("title", "%$query%")
                                ilike("description", "%$query%")
                            }
                        }

                        filters.jobType?.let {
                            eq("job_type", it.toString())
                        }

                        if (filters.location.isNotBlank()) {
                            or {
                                ilike("location", "%${filters.location}%")
                                ilike("district", "%${filters.location}%")
                                ilike("state", "%${filters.location}%")
                            }
                        }

                        if (filters.minSalary > 0) {
                            gte("min_salary", filters.minSalary)
                        }

                        if (filters.categories.isNotEmpty()) {
                            or {
                                filters.categories.forEach { category ->
                                    eq("job_category", category)
                                }
                            }
                        }

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

    // 🚀 ADMIN METHODS
    suspend fun getPendingJobs(): Flow<Result<List<Job>>> = flow {
        try {
            val userId = authRepository.getCurrentUserId() ?: throw Exception("User not authenticated")
            val isAdmin = authRepository.isUserAdmin()

            if (!isAdmin) {
                throw Exception("Unauthorized access")
            }

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
                .decodeList<Job>()

            Log.d(TAG, "Successfully decoded ${jobs.size} jobs with pending approval")
            emit(Result.success(jobs))
        } catch (e: Exception) {
            Log.e(TAG, "Error getting pending jobs: ${e.message}", e)
            emit(Result.failure(e))
        }
    }

    @OptIn(SupabaseExperimental::class)
    suspend fun updateJobStatus(jobId: String, status: JobStatus): Flow<Result<Job>> = flow {
        try {
            val userId = authRepository.getCurrentUserId() ?: throw Exception("User not authenticated")
            val isAdmin = authRepository.isUserAdmin()

            if (!isAdmin) {
                throw Exception("Unauthorized access")
            }

            val isActive = status == JobStatus.APPROVED
            val updateData = buildJsonObject {
                put("status", status.name)
                put("is_active", isActive)
            }

            supabaseClient.client.postgrest["jobs"]
                .update(updateData) {
                    filter { eq("id", jobId) }
                    headers["Prefer"] = "return=representation"
                }

            val updatedJob = getJobByIdDirect(jobId)
            if (updatedJob != null) {
                emit(Result.success(updatedJob))
            } else {
                emit(Result.failure(Exception("Failed to get updated job")))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating job status: ${e.message}", e)
            emit(Result.failure(e))
        }
    }

    // 🚀 REJECTED JOBS METHODS
    suspend fun fetchAndSyncRejectedJobs(processedJobsRepository: ProcessedJobsRepository): Flow<Result<List<Job>>> = flow {
        try {
            val userId = authRepository.getCurrentUserId() ?: throw Exception("User not authenticated")
            Log.d(TAG, "Fetching NOT_INTERESTED jobs for user: $userId")

            @Serializable
            data class NotInterestedApplication(
                val job_id: String,
                val status: String
            )

            // 🚀 FIX: Query for NOT_INTERESTED applications (user rejections)
            val notInterestedApplications = supabaseClient
                .table("applications")
                .select {
                    filter {
                        eq("employee_id", userId)
                        eq("status", "NOT_INTERESTED") // User marked as not interested
                    }
                }
                .decodeList<NotInterestedApplication>()

            Log.d(TAG, "Found ${notInterestedApplications.size} total NOT_INTERESTED applications")

            val notInterestedJobIds = notInterestedApplications.map { it.job_id }.toSet()

            // 🚀 NOTE: Update repository - this represents "not interested" jobs, not employer rejections
            processedJobsRepository.updateRejectedJobIds(notInterestedJobIds)

            val notInterestedJobs = if (notInterestedJobIds.isNotEmpty()) {
                val inClause = notInterestedJobIds.joinToString(",", prefix = "(", postfix = ")") { it }
                try {
                    supabaseClient
                        .table("jobs")
                        .select {
                            filter {
                                filter("id", FilterOperator.IN, inClause)
                            }
                        }
                        .decodeList<Job>()
                } catch (e: Exception) {
                    Log.w(TAG, "Batch fetch failed, falling back to individual requests: ${e.message}")
                    val jobs = mutableListOf<Job>()
                    notInterestedJobIds.take(10).forEach { jobId ->
                        try {
                            getJobByIdDirect(jobId)?.let { jobs.add(it) }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error fetching job $jobId: ${e.message}")
                        }
                    }
                    jobs
                }
            } else {
                emptyList()
            }

            Log.d(TAG, "Retrieved ${notInterestedJobs.size} NOT_INTERESTED job details")
            emit(Result.success(notInterestedJobs))
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching NOT_INTERESTED jobs: ${e.message}", e)
            emit(Result.failure(e))
        }
    }


    suspend fun fetchAndSyncRejectedJobsDirect(processedJobsRepository: ProcessedJobsRepository): List<Job> {
        return try {
            val userId = authRepository.getCurrentUserId() ?: return emptyList()

            @Serializable
            data class RejectedApplication(
                val job_id: String,
                val status: String
            )

            val rejectedApplications = supabaseClient
                .table("applications")
                .select {
                    filter {
                        eq("employee_id", userId)
                        eq("status", "REJECTED")
                    }
                }
                .decodeList<RejectedApplication>()

            val rejectedJobIds = rejectedApplications.map { it.job_id }.toSet()
            processedJobsRepository.updateRejectedJobIds(rejectedJobIds)

            if (rejectedJobIds.isNotEmpty()) {
                val inClause = rejectedJobIds.joinToString(",", prefix = "(", postfix = ")") { it }
                supabaseClient
                    .table("jobs")
                    .select {
                        filter {
                            filter("id", FilterOperator.IN, inClause)
                        }
                    }
                    .decodeList<Job>()
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching rejected jobs directly: ${e.message}")
            emptyList()
        }
    }

    suspend fun getRejectedJobsDetails(district: String = ""): Flow<Result<List<Job>>> = flow {
        try {
            val userId = authRepository.getCurrentUserId() ?: throw Exception("User not authenticated")

            val allApplications = supabaseClient.client.postgrest["applications"]
                .select {
                    filter { eq("employee_id", userId) }
                }
                .decodeList<Application>()

            val applicationsByJobId = allApplications.groupBy { it.jobId }
            val appliedJobIds = applicationsByJobId
                .filter { (_, applications) ->
                    applications.any { it.status == "APPLIED" }
                }
                .keys.toSet()

            val trulyRejectedJobIds = applicationsByJobId
                .filter { (jobId, applications) ->
                    !appliedJobIds.contains(jobId) &&
                            applications.any { it.status == "REJECTED" }
                }
                .keys.toList()

            processedJobsRepository.updateRejectedJobIds(trulyRejectedJobIds.toSet())
            processedJobsRepository.updateAppliedJobIds(appliedJobIds)

            if (trulyRejectedJobIds.isEmpty()) {
                emit(Result.success(emptyList()))
                return@flow
            }

            val jobs = supabaseClient.client.postgrest["jobs"]
                .select {
                    filter {
                        val inClause = trulyRejectedJobIds.joinToString(",", prefix = "(", postfix = ")")
                        filter("id", FilterOperator.IN, inClause)
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

            emit(Result.success(jobs))
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching rejected jobs: ${e.message}", e)
            emit(Result.failure(e))
        }
    }

    suspend fun getRejectedJobsDetailsDirect(district: String = ""): List<Job> {
        return try {
            val userId = authRepository.getCurrentUserId() ?: return emptyList()

            val allApplications = supabaseClient.client.postgrest["applications"]
                .select {
                    filter { eq("employee_id", userId) }
                }
                .decodeList<Application>()

            val applicationsByJobId = allApplications.groupBy { it.jobId }
            val appliedJobIds = applicationsByJobId
                .filter { (_, applications) ->
                    applications.any { it.status == "APPLIED" }
                }
                .keys.toSet()

            // 🚀 FIX: Look for NOT_INTERESTED status (user rejections)
            val notInterestedJobIds = applicationsByJobId
                .filter { (jobId, applications) ->
                    !appliedJobIds.contains(jobId) &&
                            applications.any { it.status == "NOT_INTERESTED" }
                }
                .keys.toList()

            // 🚀 SEPARATE: Could also track employer rejections if needed
            val employerRejectedJobIds = applicationsByJobId
                .filter { (jobId, applications) ->
                    !appliedJobIds.contains(jobId) &&
                            applications.any { it.status == "REJECTED" }
                }
                .keys.toList()

            Log.d(TAG, "Found ${notInterestedJobIds.size} NOT_INTERESTED jobs and ${employerRejectedJobIds.size} employer REJECTED jobs")

            processedJobsRepository.updateRejectedJobIds(notInterestedJobIds.toSet()) // NOT_INTERESTED jobs
            processedJobsRepository.updateAppliedJobIds(appliedJobIds)

            if (notInterestedJobIds.isEmpty()) return emptyList()

            supabaseClient.client.postgrest["jobs"]
                .select {
                    filter {
                        val inClause = notInterestedJobIds.joinToString(",", prefix = "(", postfix = ")")
                        filter("id", FilterOperator.IN, inClause)
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
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching NOT_INTERESTED jobs details directly: ${e.message}")
            emptyList()
        }
    }

    suspend fun getRejectedJobs(): Flow<Result<Set<String>>> {
        return flow { emit(Result.success(emptySet<String>())) }
    }

    // 🚀 APPLICATION STATUS METHODS
    suspend fun updateApplicationStatus(jobId: String, status: String): Result<Unit> {
        return try {
            val userId = authRepository.getCurrentUserId()
                ?: return Result.failure(Exception("User not authenticated"))

            val validatedStatus = validateApplicationStatus(status)

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
                supabaseClient
                    .table("applications")
                    .update(mapOf(
                        "status" to validatedStatus,
                        "updated_at" to java.time.Instant.now().toString()
                    )) {
                        filter {
                            eq("employee_id", userId)
                            eq("job_id", jobId)
                        }
                    }
            } else {
                val timestamp = java.time.Instant.now().toString()
                supabaseClient
                    .table("applications")
                    .insert(mapOf(
                        "job_id" to jobId,
                        "employee_id" to userId,
                        "status" to validatedStatus,
                        "applied_at" to timestamp,
                        "created_at" to timestamp,
                        "updated_at" to timestamp
                    ))
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating application status: ${e.message}", e)
            Result.failure(e)
        }
    }

    // 🚀 FLOW COMPATIBILITY METHODS
    suspend fun getJobsByLocationCached(location: String): Flow<Result<List<Job>>> = flow {
        try {
            val jobs = getJobsByLocationDirectSimple(location)
            emit(Result.success(jobs))
        } catch (e: Exception) {
            throw e
        }
    }.catch { e ->
        Log.e(TAG, "Flow error in getJobsByLocationCached: ${e.message}")
        emit(Result.failure(e))
    }

    suspend fun getApplicationsForUserCached(userId: String): Flow<Result<List<ApplicationWithJob>>> = flow {
        try {
            val applications = getApplicationsDirectSimple(userId)
            emit(Result.success(applications))
        } catch (e: Exception) {
            throw e
        }
    }.catch { e ->
        Log.e(TAG, "Flow error in getApplicationsForUserCached: ${e.message}")
        emit(Result.failure(e))
    }

    suspend fun getFeaturedJobs(limit: Int = 5): Flow<Result<List<Job>>> = flow {
        try {
            val jobs = getFeaturedJobsDirect(limit)
            emit(Result.success(jobs))
        } catch (e: Exception) {
            throw e
        }
    }.catch { e ->
        Log.e(TAG, "Flow error in getFeaturedJobs: ${e.message}")
        emit(Result.failure(e))
    }

    suspend fun getJobsByLocation(location: String): Flow<Result<List<Job>>> = flow {
        try {
            val jobs = getJobsByLocationDirectPublic(location)
            emit(Result.success(jobs))
        } catch (e: Exception) {
            throw e
        }
    }.catch { e ->
        Log.e(TAG, "Flow error in getJobsByLocation: ${e.message}")
        emit(Result.failure(e))
    }

    suspend fun getJobById(jobId: String): Flow<Result<Job>> = flow {
        try {
            val job = getJobByIdDirect(jobId)
            if (job != null) {
                emit(Result.success(job))
            } else {
                emit(Result.failure(Exception("Job not found")))
            }
        } catch (e: Exception) {
            throw e
        }
    }.catch { e ->
        Log.e(TAG, "Flow error in getJobById: ${e.message}")
        emit(Result.failure(e))
    }

    suspend fun getMyJobs(limit: Int = 10): Flow<Result<List<Job>>> = flow {
        try {
            val jobs = getMyJobsDirect(limit)
            emit(Result.success(jobs))
        } catch (e: Exception) {
            throw e
        }
    }.catch { e ->
        Log.e(TAG, "Flow error in getMyJobs: ${e.message}")
        emit(Result.failure(e))
    }

    // 🚀 HELPER METHODS
    private fun isOperationInProgress(operationKey: String): Boolean {
        val currentTime = System.currentTimeMillis()
        val startTime = operationTracking[operationKey] ?: return false

        val timeElapsed = currentTime - startTime
        return if (timeElapsed > 3000L) {
            operationTracking.remove(operationKey)
            Log.w(TAG, "⚠️ Operation $operationKey timed out, allowing retry")
            false
        } else {
            true
        }
    }

    private fun validateApplicationStatus(status: String): String {
        return when (status.uppercase().trim()) {
            "APPLIED" -> "APPLIED"
            "REJECTED" -> "REJECTED"
            "PENDING" -> "PENDING"
            "UNDER_REVIEW" -> "UNDER_REVIEW"
            "ACCEPTED" -> "ACCEPTED"
            "DECLINED" -> "DECLINED"
            "COMPLETED", "COMPLETE" -> {
                Log.w(TAG, "⚠️ COMPLETED is not a valid status, converting to REJECTED")
                "REJECTED"
            }
            else -> {
                Log.w(TAG, "⚠️ Unknown status '$status', defaulting to REJECTED")
                "REJECTED"
            }
        }
    }

    private fun sanitizeJob(job: Job, userId: String): Job {
        val (districtFromLoc, stateFromLoc) = splitLocation(job.location)

        return job.copy(
            employerId = userId,
            status = JobStatus.PENDING_APPROVAL,
            isActive = false,
            jobType = job.jobType ?: WorkPreference.FULL_TIME,
            workType = job.workType ?: WorkType.FULL_TIME,
            district = if (job.district.isNotBlank()) job.district else districtFromLoc,
            state = if (job.state.isNotBlank()) job.state else stateFromLoc,
            createdAt = job.createdAt ?: currentUtcTimestamp(),
            updatedAt = currentUtcTimestamp()
        )
    }

    private fun splitLocation(location: String): Pair<String, String> {
        val parts = location.split(",").map { it.trim() }
        return when {
            parts.size >= 2 -> Pair(parts[0], parts[1])
            parts.size == 1 -> Pair(parts[0], "")
            else -> Pair("", "")
        }
    }

    private fun currentUtcTimestamp(): String = java.time.Instant.now().toString()

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

    // 🚀 CACHE MANAGEMENT
    fun clearAllCaches() {
        jobsCache.clear()
        jobDetailsCache.clear()
        applicationsCache.clear()
        pendingRequests.clear()
        Log.d(TAG, "All caches cleared")
    }

    private fun clearLocationCaches() {
        val keysToRemove = jobsCache.getKeys().filter {
            it.startsWith("jobs_district_") || it.startsWith("featured_jobs_")
        }
        keysToRemove.forEach { jobsCache.remove(it) }
        Log.d(TAG, "Location caches cleared")
    }

    fun getCacheStats(): Map<String, PerformanceUtils.CacheStats> {
        return mapOf(
            "jobs" to jobsCache.getStats(),
            "jobDetails" to jobDetailsCache.getStats(),
            "applications" to applicationsCache.getStats()
        )
    }

    fun logCacheStats() {
        Log.d(TAG, "=== CACHE STATS ===")
        getCacheStats().forEach { (name, stats) ->
            Log.d(TAG, "$name: ${stats}")
        }
        Log.d(TAG, "Pending requests: ${pendingRequests.size}")
        Log.d(TAG, "==================")
    }

    /**
     * 🚀 NEW: Initialize reconsideration system on app start
     */
    suspend fun initializeReconsiderationSystem() {
        try {
            Log.d(TAG, "🔄 Initializing reconsideration system...")

            // Load reconsidered jobs from storage
            val reconsideredJobIds = reconsiderationStorage.loadReconsideredJobIds()

            // Initialize repository
            processedJobsRepository.initializeReconsideredJobs(reconsideredJobIds)

            // Log storage state
            reconsiderationStorage.logStorageState()

            Log.d(TAG, "✅ Reconsideration system initialized with ${reconsideredJobIds.size} reconsidered jobs")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to initialize reconsideration system: ${e.message}")
        }
    }

    // 🚀 ADD these methods to your JobRepository.kt class:

    /**
     * Get all jobs posted by a specific employer
     */
    suspend fun getJobsByEmployer(employerId: String): Flow<Result<List<Job>>> = flow {
        try {
            Log.d(TAG, "🔍 Getting jobs for employer: $employerId")

            val jobs = supabaseClient
                .table("jobs")
                .select {
                    filter { eq("employer_id", employerId) }
                    order("created_at", Order.DESCENDING)
                }
                .decodeList<Job>()

            Log.d(TAG, "✅ Found ${jobs.size} jobs for employer")
            emit(Result.success(jobs))
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting jobs for employer: ${e.message}")
            emit(Result.failure(e))
        }
    }

    /**
     * Get active jobs for employer (approved status only)
     */
    suspend fun getActiveJobsByEmployer(employerId: String): Flow<Result<List<Job>>> = flow {
        try {
            Log.d(TAG, "🔍 Getting active jobs for employer: $employerId")

            val jobs = supabaseClient
                .table("jobs")
                .select {
                    filter {
                        eq("employer_id", employerId)
                        eq("status", JobStatus.APPROVED.toString())
                        eq("is_active", true)
                    }
                    order("created_at", Order.DESCENDING)
                }
                .decodeList<Job>()

            Log.d(TAG, "✅ Found ${jobs.size} active jobs for employer")
            emit(Result.success(jobs))
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting active jobs for employer: ${e.message}")
            emit(Result.failure(e))
        }
    }


    /**
     * 🚀 NEW: Debug method to log reconsideration state
     */
    suspend fun logReconsiderationDebugInfo() {
        try {
            Log.d(TAG, "=== RECONSIDERATION DEBUG INFO ===")

            val stats = getReconsiderationStatistics()
            val storageStats = reconsiderationStorage.getStorageStats()
            val repositoryStats = processedJobsRepository.getReconsiderationStats()

            Log.d(TAG, "Repository stats: $repositoryStats")
            Log.d(TAG, "Database stats: $stats")
            Log.d(TAG, "Storage stats: $storageStats")

            val userId = authRepository.getCurrentUserId()?.take(8) ?: "unknown"
            Log.d(TAG, "Current user: $userId...")

            Log.d(TAG, "==================================")
        } catch (e: Exception) {
            Log.e(TAG, "Error logging debug info: ${e.message}")
        }
    }

    // [Keep all existing methods unchanged...]
    // private val operationTracking = ConcurrentHashMap<String, Long>()
    // private fun isOperationInProgress(operationKey: String): Boolean { ... }
    // ... [all other existing methods remain the same]
}

/**
 * 🚀 NEW: Data class for reconsideration statistics
 */
data class ReconsiderationStatistics(
    val totalRejected: Int,
    val reconsidered: Int,
    val eligibleForReconsideration: Int
) {
    val reconsiderationRate: Double
        get() = if (totalRejected > 0) reconsidered.toDouble() / totalRejected else 0.0

    val hasEligibleJobs: Boolean
        get() = eligibleForReconsideration > 0

}