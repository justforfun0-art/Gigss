package com.example.gigs.data.repository

import android.util.Log
import com.example.gigs.data.model.Activity
import com.example.gigs.data.model.CategoryStat
import com.example.gigs.data.model.EmployeeDashboardData
import com.example.gigs.data.model.EmployerDashboardData
import com.example.gigs.data.model.LocationStat
import com.example.gigs.data.remote.SupabaseClient
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import com.example.gigs.data.model.Application
import com.example.gigs.data.model.Job

class DashboardRepository @Inject constructor(
    private val supabaseClient: SupabaseClient,
    private val authRepository: AuthRepository,
    private val profileRepository: ProfileRepository,
    private val jobRepository: JobRepository,
    private val applicationRepository: ApplicationRepository
) {
    suspend fun getEmployeeDashboardData(): Flow<Result<EmployeeDashboardData>> = flow {
        try {
            val userId = authRepository.getCurrentUserId() ?: throw Exception("User not authenticated")

            // Try to get from employer_dashboard table first
            val result = supabaseClient
                .table("employee_dashboard")
                .select {
                    filter {
                        eq("user_id", userId)
                    }
                }
                .decodeSingleOrNull<EmployeeDashboardData>()

            if (result != null) {
                emit(Result.success(result))
            } else {
                // If not found in table, calculate real-time stats
                try {
                    // 🚀 FIX: Use safe timeout and direct collection
                    val apps = withTimeoutOrNull(5000) {
                        applicationRepository.getMyApplications(0).first()
                    }

                    if (apps?.isSuccess == true) {
                        val applications = apps.getOrNull() ?: emptyList()

                        val totalApplications = applications.size
                        val hiredCount = applications.count {
                            it.status?.name.equals("hired", true)
                        }
                        val rejectedCount = applications.count {
                            it.status?.name.equals("rejected", true)
                        }

                        emit(Result.success(EmployeeDashboardData(
                            userId = userId,
                            totalApplications = totalApplications,
                            hiredCount = hiredCount,
                            rejectedCount = rejectedCount
                        )))
                    } else {
                        emit(Result.success(EmployeeDashboardData(userId)))
                    }
                } catch (e: Exception) {
                    emit(Result.success(EmployeeDashboardData(userId)))
                }
            }
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }

    /**
     * 🚀 FIXED: Employer dashboard data without nested Flow collections
     */
    // 🚀 COMPLETE FIX: Replace the entire getEmployerDashboardData method in DashboardRepository

    suspend fun getEmployerDashboardData(): Flow<Result<EmployerDashboardData>> = flow {
        try {
            val userId = authRepository.getCurrentUserId() ?: throw Exception("User not authenticated")

            Log.d("DashboardRepo", "🔍 Loading employer dashboard data for user: ${userId.take(8)}...")

            // Calculate dashboard data directly
            val dashboardData = calculateEmployerDashboardDataDirect(userId)

            Log.d("DashboardRepo", "✅ Calculated dashboard data directly: $dashboardData")
            emit(Result.success(dashboardData))

        } catch (e: kotlinx.coroutines.CancellationException) {
            // 🚀 CRITICAL: Don't emit anything for AbortFlowException - just let flow complete
            Log.d("DashboardRepo", "🔄 Flow aborted - this is normal when UI cancels collection")
            // Don't emit anything here - flow cancellation is normal
        } catch (e: kotlinx.coroutines.CancellationException) {
            // 🚀 CRITICAL: Don't emit for cancellation either
            Log.d("DashboardRepo", "🔄 Flow cancelled - this is normal")
            // Don't emit anything here - cancellation is normal
        } catch (e: Exception) {
            Log.e("DashboardRepo", "❌ Error in getEmployerDashboardData: ${e.message}", e)
            emit(Result.failure(e))
        }
    }

    /**
     * 🚀 NEW: Direct calculation method that avoids ALL Flow operations
     */
    private suspend fun calculateEmployerDashboardDataDirect(userId: String): EmployerDashboardData {
        return try {
            Log.d("DashboardRepo", "🔍 Direct calculation started for user: ${userId.take(8)}...")

            // Initialize default values
            var totalJobs = 0
            var activeJobs = 0
            var totalApplicationsReceived = 0
            var averageRating = 0.0f

            // 🚀 STEP 1: Get jobs directly from database (NO FLOWS)
            val jobs = try {
                withTimeoutOrNull(3000) {
                    supabaseClient
                        .table("jobs")
                        .select {
                            filter { eq("employer_id", userId) }
                            order("created_at", Order.DESCENDING)
                            limit(100)
                        }
                        .decodeList<Job>()
                } ?: emptyList()
            } catch (e: Exception) {
                Log.e("DashboardRepo", "Error fetching jobs: ${e.message}")
                emptyList()
            }

            totalJobs = jobs.size
            activeJobs = jobs.count { job ->
                job.isActive == true || job.status?.name?.equals("APPROVED", true) == true
            }

            Log.d("DashboardRepo", "📊 Direct calc: Total jobs: $totalJobs, Active jobs: $activeJobs")

            // 🚀 STEP 2: Calculate applications directly (NO FLOWS)
            if (jobs.isNotEmpty()) {
                var applicationCount = 0

                // Process jobs in small batches to avoid timeout
                jobs.take(10).forEach { job ->
                    try {
                        val applications = withTimeoutOrNull(1000) {
                            supabaseClient
                                .table("applications")
                                .select {
                                    filter {
                                        eq("job_id", job.id)
                                        neq("status", "NOT_INTERESTED")
                                    }
                                }
                                .decodeList<Application>()
                        } ?: emptyList()

                        applicationCount += applications.size
                        if (applications.isNotEmpty()) {
                            Log.d("DashboardRepo", "📊 Job ${job.id.take(8)} has ${applications.size} applications")
                        }
                    } catch (e: Exception) {
                        Log.w("DashboardRepo", "Failed to count applications for job ${job.id.take(8)}: ${e.message}")
                    }
                }

                totalApplicationsReceived = applicationCount
            }

            // 🚀 STEP 3: Try to get rating from database (NO FLOWS)
            try {
                val dashboardRecord = withTimeoutOrNull(2000) {
                    supabaseClient
                        .table("employer_dashboard")
                        .select {
                            filter { eq("user_id", userId) }
                        }
                        .decodeSingleOrNull<EmployerDashboardData>()
                }

                averageRating = dashboardRecord?.averageRating ?: 0.0f
            } catch (e: Exception) {
                Log.w("DashboardRepo", "Failed to get rating from dashboard table: ${e.message}")
                averageRating = 0.0f
            }

            val result = EmployerDashboardData(
                userId = userId,
                totalJobs = totalJobs,
                activeJobs = activeJobs,
                totalApplicationsReceived = totalApplicationsReceived,
                averageRating = averageRating
            )

            Log.d("DashboardRepo", "✅ Direct calculation completed: $result")
            result

        } catch (e: Exception) {
            Log.e("DashboardRepo", "❌ Error in direct calculation: ${e.message}", e)
            EmployerDashboardData(
                userId = userId,
                totalJobs = 0,
                activeJobs = 0,
                totalApplicationsReceived = 0,
                averageRating = 0.0f
            )
        }
    }

    suspend fun getApplicationsByLocation(): Flow<Result<List<LocationStat>>> = flow {
        try {
            val result = supabaseClient
                .table("job_applications_by_location")
                .select {
                    order("application_count", Order.ASCENDING)
                }
                .decodeList<LocationStat>()

            emit(Result.success(result))

        } catch (e: kotlinx.coroutines.CancellationException) {
            Log.d("DashboardRepo", "🔄 Location stats flow aborted - normal")
        } catch (e: kotlinx.coroutines.CancellationException) {
            Log.d("DashboardRepo", "🔄 Location stats flow cancelled - normal")
        } catch (e: Exception) {
            Log.e("DashboardRepo", "❌ Error getting location stats: ${e.message}")
            emit(Result.failure(e))
        }
    }

    suspend fun getApplicationsByCategory(): Flow<Result<List<CategoryStat>>> = flow {
        try {
            val result = supabaseClient
                .table("job_applications_by_category")
                .select {
                    order("application_count", Order.ASCENDING)
                }
                .decodeList<CategoryStat>()

            emit(Result.success(result))

        } catch (e: kotlinx.coroutines.CancellationException) {
            Log.d("DashboardRepo", "🔄 Category stats flow aborted - normal")
        } catch (e: kotlinx.coroutines.CancellationException) {
            Log.d("DashboardRepo", "🔄 Category stats flow cancelled - normal")
        } catch (e: Exception) {
            Log.e("DashboardRepo", "❌ Error getting category stats: ${e.message}")
            emit(Result.failure(e))
        }
    }

    // 🚀 CORRECTED: Replace your existing getRecentActivities function with this

    suspend fun getRecentActivities(limit: Int = 10): Flow<Result<List<Activity>>> = flow {
        try {
            val userId = authRepository.getCurrentUserId() ?: throw Exception("User not authenticated")

            val result = supabaseClient
                .table("recent_activities")
                .select {
                    filter {
                        or {
                            eq("user_id", userId)
                            eq("target_user_id", userId)
                        }
                    }
                    order("activity_time", Order.ASCENDING)
                    limit(limit.toLong())
                }
                .decodeList<Activity>()

            // Enhanced activities with user names (simplified to avoid more Flow issues)
            val enhancedActivities = result.map { activity ->
                try {
                    // Get user name safely without Flows
                    val userName = withTimeoutOrNull(1000) {
                        profileRepository.getProfileByUserId(activity.userId)?.fullName
                    } ?: "Unknown User"

                    val targetUserName = if (activity.targetUserId != null) {
                        withTimeoutOrNull(1000) {
                            profileRepository.getProfileByUserId(activity.targetUserId)?.fullName
                        } ?: "Unknown User"
                    } else {
                        ""
                    }

                    activity.copy(
                        userName = userName,
                        targetUserName = targetUserName
                    )
                } catch (e: Exception) {
                    activity.copy(
                        userName = "Unknown User",
                        targetUserName = if (activity.targetUserId != null) "Unknown User" else ""
                    )
                }
            }

            emit(Result.success(enhancedActivities))

        } catch (e: kotlinx.coroutines.CancellationException) {
            // 🚀 FIX 1: Remove duplicate CancellationException catch blocks
            Log.d("DashboardRepo", "🔄 Activities flow cancelled/aborted - normal")
            // Don't emit anything for cancellation
        } catch (e: Exception) {
            // 🚀 FIX 2: Handle missing table/serialization errors gracefully
            if (e.message?.contains("does not exist") == true ||
                e.message?.contains("Serializer for class") == true) {
                Log.w("DashboardRepo", "⚠️ Activities table/serialization not available: ${e.message}")
                emit(Result.success(emptyList<Activity>())) // Return empty list instead of error
            } else {
                Log.e("DashboardRepo", "❌ Error getting recent activities: ${e.message}")
                emit(Result.failure(e))
            }
        }
    }
}