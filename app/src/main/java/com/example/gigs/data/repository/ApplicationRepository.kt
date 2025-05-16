package com.example.gigs.data.repository

import android.content.ContentValues.TAG
import android.util.Log
import com.example.gigs.data.model.Application
import com.example.gigs.data.model.ApplicationStatus
import com.example.gigs.data.model.ApplicationWithJob
import com.example.gigs.data.model.Job
import com.example.gigs.data.remote.SupabaseClient
import io.github.jan.supabase.annotations.SupabaseExperimental
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.LocalDateTime
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
            // Step 1: Get current user ID
            val userId = authRepository.getCurrentUserId()
            Log.d("ApplicationRepo", "Getting applications for user ID: $userId")

            if (userId == null) {
                Log.e("ApplicationRepo", "ERROR: User not authenticated!")
                emit(Result.failure(Exception("User not authenticated")))
                return@flow
            }

            Log.d("ApplicationRepo", "Querying applications table for employee_id=$userId with limit=$limit")

            try {
                // Step 2: Query applications
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

                Log.d("ApplicationRepo", "Query complete. Found ${applications.size} applications")

                // Log each application for debugging
                applications.forEachIndexed { index, app ->
                    Log.d("ApplicationRepo", "Application $index: id=${app.id}, jobId=${app.jobId}, status=${app.status}")
                }

                // Step 3: Attach job details to each application
                Log.d("ApplicationRepo", "Starting to attach job details to applications")
                val applicationWithJobs = mutableListOf<ApplicationWithJob>()

                applications.forEachIndexed { index, application ->
                    try {
                        Log.d("ApplicationRepo", "Getting job details for application $index, jobId=${application.jobId}")
                        val job = getJobById(application.jobId)
                        if (job != null) {
                            Log.d("ApplicationRepo", "Found job: id=${job.id}, title=${job.title}")
                            applicationWithJobs.add(application.copy(job = job))
                        } else {
                            Log.e("ApplicationRepo", "Job not found for jobId=${application.jobId}, using empty job object")
                            applicationWithJobs.add(application.copy(job = Job()))
                        }
                    } catch (e: Exception) {
                        Log.e("ApplicationRepo", "Error attaching job to application $index: ${e.message}", e)
                        // Still add the application with an empty job to avoid losing data
                        applicationWithJobs.add(application.copy(job = Job()))
                    }
                }

                Log.d("ApplicationRepo", "Completed processing all applications. Final count: ${applicationWithJobs.size}")
                emit(Result.success(applicationWithJobs))

            } catch (e: Exception) {
                Log.e("ApplicationRepo", "Error querying applications: ${e.message}", e)
                emit(Result.failure(e))
            }
        } catch (e: Exception) {
            Log.e("ApplicationRepo", "Fatal error in getMyApplications: ${e.message}", e)
            e.printStackTrace()
            emit(Result.failure(e))
        }
    }

    // In ApplicationRepository.kt
    suspend fun updateApplicationStatus(jobId: String, status: String): Flow<Result<Unit>> = flow {
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
                .decodeList<Application>()

            if (existingApplications.isNotEmpty()) {
                // Update existing application status
                supabaseClient
                    .table("applications")
                    .update({
                        set("status", status)
                        set("updated_at", LocalDateTime.now().toString())
                    }) {
                        filter {
                            eq("employee_id", userId)
                            eq("job_id", jobId)
                        }
                    }

                Log.d(TAG, "Updated application status for job $jobId to $status")
                emit(Result.success(Unit))
            } else {
                // Create new application instead
                // We need to collect from the other Flow
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

    /**
     * Apply for a job
     */
    @OptIn(SupabaseExperimental::class)
    suspend fun applyForJob(jobId: String): Flow<Result<ApplicationWithJob>> = flow {
        val userId = authRepository.getCurrentUserId() ?: throw Exception("User not authenticated")

        try {
            Log.d(TAG, "Attempting to apply for job: $jobId by user: $userId")

            // Check if an application already exists (regardless of status)
            val existingApplication = supabaseClient
                .table("applications")
                .select {
                    filter {
                        eq("job_id", jobId)
                        eq("employee_id", userId)
                    }
                }
                .decodeList<Application>()

            if (existingApplication.isNotEmpty()) {
                // Application exists - UPDATE instead of INSERT
                val applicationId = existingApplication.first().id
                val timestamp = java.time.Instant.now().toString()

                Log.d(TAG, "Existing application found with ID $applicationId. Updating to APPLIED status")

                // UPDATE the existing application
                supabaseClient
                    .table("applications")
                    .update({
                        set("status", "APPLIED")
                        set("updated_at", timestamp)
                    }) {
                        filter {
                            eq("id", applicationId)
                        }
                    }

                Log.d(TAG, "Successfully updated application status to APPLIED")

                // Get job details to return a complete ApplicationWithJob
                val job = getJobById(jobId)

                // Create updated application object
                val updatedApplication = ApplicationWithJob(
                    id = applicationId,
                    jobId = jobId,
                    employeeId = userId,
                    status = ApplicationStatus.APPLIED,
                    appliedAt = existingApplication.first().createdAt,  // Map createdAt to appliedAt
                    updatedAt = timestamp,
                    job = job ?: Job()
                )

                emit(Result.success(updatedApplication))
            } else {
                // New application - proceed with INSERT
                val job = getJobById(jobId) ?: throw Exception("Job not found")

                val timestamp = java.time.Instant.now().toString()
                val applicationData = buildJsonObject {
                    put("job_id", jobId)
                    put("employee_id", userId)
                    put("status", "APPLIED")
                    // Use created_at which will map to createdAt
                    put("created_at", timestamp)
                    put("updated_at", timestamp)
                }

                // Insert the new application
                supabaseClient
                    .table("applications")
                    .insert(applicationData)

                Log.d(TAG, "Successfully created new application for job $jobId")

                // Fetch the newly created application
                val newApplication = supabaseClient
                    .table("applications")
                    .select {
                        filter {
                            eq("job_id", jobId)
                            eq("employee_id", userId)
                        }
                    }
                    .decodeSingleOrNull<Application>()

                if (newApplication != null) {
                    emit(Result.success(ApplicationWithJob(
                        id = newApplication.id,
                        jobId = jobId,
                        employeeId = userId,
                        status = ApplicationStatus.APPLIED,
                        appliedAt = newApplication.createdAt,  // Map createdAt to appliedAt
                        updatedAt = newApplication.updatedAt,
                        job = job
                    )))
                } else {
                    emit(Result.failure(Exception("Failed to create application")))
                }
            }
        } catch (e: Exception) {
            // Fallback for handling unique constraint errors
            if (e.message?.contains("unique constraint") == true) {
                try {
                    Log.d(TAG, "Unique constraint error, trying direct update")
                    val timestamp = java.time.Instant.now().toString()

                    // Direct update using job_id and employee_id filter
                    supabaseClient
                        .table("applications")
                        .update({
                            set("status", "APPLIED")
                            set("updated_at", timestamp)
                        }) {
                            filter {
                                eq("job_id", jobId)
                                eq("employee_id", userId)  // userId is in scope here
                            }
                        }

                    // Get updated application
                    val job = getJobById(jobId)
                    val updatedApp = supabaseClient
                        .table("applications")
                        .select {
                            filter {
                                eq("job_id", jobId)
                                eq("employee_id", userId)
                            }
                        }
                        .decodeSingleOrNull<Application>()

                    if (updatedApp != null && job != null) {
                        emit(Result.success(ApplicationWithJob(
                            id = updatedApp.id,
                            jobId = jobId,
                            employeeId = userId,
                            status = ApplicationStatus.APPLIED,
                            appliedAt = updatedApp.createdAt,  // Use createdAt instead of appliedAt
                            updatedAt = timestamp,
                            job = job
                        )))
                    } else {
                        emit(Result.failure(Exception("Failed to update application")))
                    }
                } catch (e2: Exception) {
                    Log.e(TAG, "Error in fallback update: ${e2.message}")
                    emit(Result.failure(e2))
                }
            } else {
                Log.e(TAG, "Error applying for job: ${e.message}")
                emit(Result.failure(e))
            }
        }
    }

    // Add this helper method to retrieve an application
    private suspend fun getApplicationForJob(jobId: String): Flow<Result<ApplicationWithJob>> = flow {
        try {
            val userId = authRepository.getCurrentUserId() ?: throw Exception("User not authenticated")

            val application = supabaseClient
                .table("applications")
                .select {
                    filter {
                        eq("job_id", jobId)
                        eq("employee_id", userId)
                    }
                }
                .decodeSingleOrNull<ApplicationWithJob>()

            if (application != null) {
                // Get job details
                val job = getJobById(jobId)
                emit(Result.success(application.copy(job = job ?: Job())))
            } else {
                emit(Result.failure(Exception("Application not found")))
            }
        } catch (e: Exception) {
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