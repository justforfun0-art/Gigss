package com.example.gigs.data.repository

import android.content.ContentValues.TAG
import android.util.Log
import com.example.gigs.data.model.ApplicationWithJob
import com.example.gigs.data.model.Job
import com.example.gigs.data.remote.SupabaseClient
import io.github.jan.supabase.annotations.SupabaseExperimental
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApplicationRepository @Inject constructor(
    private val supabaseClient: SupabaseClient,
    private val authRepository: AuthRepository
) {

    /**
     * Get the current user's applications with job details
     * @param limit The maximum number of applications to return. Use 0 for no limit.
     */
    suspend fun getMyApplications(limit: Int = 0): Flow<Result<List<ApplicationWithJob>>> = flow {
        try {
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

            // Attach job to each application
            val applicationWithJobs = applications.map { application ->
                val job = getJobById(application.jobId)
                application.copy(job = job ?: Job())
            }

            emit(Result.success(applicationWithJobs))
        } catch (e: Exception) {
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
            emit(Result.success(hasApplied))
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }



    /**
     * Get applications for a specific job
     */
    suspend fun getApplicationsForJob(jobId: String): Flow<Result<List<ApplicationWithJob>>> = flow {
        try {
            val userId = authRepository.getCurrentUserId() ?: throw Exception("User not authenticated")

            // Get job to verify ownership
            val job = getJobById(jobId)

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
            // For each application, include the job
            val applicationWithJobs = applications.map { application ->
                application.copy(job = job)
            }

            emit(Result.success(applicationWithJobs))
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }

    /**
     * Update application status (for employers)
     */
    suspend fun updateApplicationStatus(
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

            // Get the job to verify ownership
            val job = getJobById(application.jobId)

            if (job?.employerId != userId) {
                emit(Result.failure(Exception("You can only update applications for your own jobs")))
                return@flow
            }

            // Update application status
            val result = supabaseClient
                .table("applications")
                .update(mapOf("status" to newStatus)) {
                    filter {
                        eq("id", applicationId)
                    }
                }
            emit(Result.success(true))
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }

    /**
     * Apply for a job
     */
    @OptIn(SupabaseExperimental::class)
    suspend fun applyForJob(jobId: String): Flow<Result<ApplicationWithJob>> = flow {
        try {
            val userId = authRepository.getCurrentUserId() ?: throw Exception("User not authenticated")

            Log.d(TAG, "Attempting to apply for job: $jobId by user: $userId")

            // Check if user already applied for this job
            val existingApplications = supabaseClient
                .table("applications")
                .select {
                    filter {
                        eq("job_id", jobId)
                        eq("employee_id", userId)
                    }
                }
                .decodeList<ApplicationWithJob>()

            if (existingApplications.isNotEmpty()) {
                Log.d(TAG, "User has already applied for this job")
                emit(Result.failure(Exception("You have already applied for this job")))
                return@flow
            }

            // Get job details
            val jobResult = supabaseClient
                .table("jobs")
                .select {
                    filter {
                        eq("id", jobId)
                    }
                }
                .decodeList<Job>()

            val jobObject = if (jobResult.isNotEmpty()) jobResult.first() else null

            if (jobObject == null) {
                Log.e(TAG, "Job not found with ID: $jobId")
                emit(Result.failure(Exception("Job not found")))
                return@flow
            }

            // Create application using a simpler approach
            Log.d(TAG, "Creating application for job: $jobId")
            val timestamp = java.time.Instant.now().toString()

            // Use executeStatement instead of trying to decode the response
            supabaseClient
                .table("applications")
                .insert(
                    """
                {
                  "job_id": "$jobId",
                  "employee_id": "$userId",
                  "status": "APPLIED",
                  "applied_at": "$timestamp"
                }
                """
                ) {
                    headers["Prefer"] = "return=minimal"
                }

            // Now check if it was created
            val createdApplication = supabaseClient
                .table("applications")
                .select {
                    filter {
                        eq("job_id", jobId)
                        eq("employee_id", userId)
                    }
                }
                .decodeList<ApplicationWithJob>()

            if (createdApplication.isNotEmpty()) {
                Log.d(TAG, "Successfully applied for job: $jobId")
                emit(Result.success(createdApplication.first().copy(job = jobObject)))
            } else {
                Log.e(TAG, "Failed to create application - not found after insert")
                emit(Result.failure(Exception("Failed to create application")))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error applying for job: ${e.message}", e)
            emit(Result.failure(e))
        }
    }

    // Helper method to get job by ID
    private suspend fun getJobById(jobId: String): Job? {
        return try {
            supabaseClient
                .table("jobs")
                .select {
                    filter {
                        eq("id", jobId)
                    }
                }
                .decodeSingleOrNull<Job>()
        } catch (e: Exception) {
            null
        }
    }
}