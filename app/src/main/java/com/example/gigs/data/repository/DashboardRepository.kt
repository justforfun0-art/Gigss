package com.example.gigs.data.repository

import com.example.gigs.data.model.Activity
import com.example.gigs.data.model.CategoryStat
import com.example.gigs.data.model.EmployeeDashboardData
import com.example.gigs.data.model.EmployerDashboardData
import com.example.gigs.data.model.LocationStat
import com.example.gigs.data.remote.SupabaseClient
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

class DashboardRepository @Inject constructor(
    private val supabaseClient: SupabaseClient,
    private val authRepository: AuthRepository,
    private val profileRepository: ProfileRepository
) {
    suspend fun getEmployeeDashboardData(): Flow<Result<EmployeeDashboardData>> = flow {
        try {
            val userId = authRepository.getCurrentUserId() ?: throw Exception("User not authenticated")

            val result = supabaseClient
                .table("employee_dashboard")
                .select {
                    filter {
                        eq("user_id", userId)
                    }
                }
                .decodeSingleOrNull<EmployeeDashboardData>()

            emit(Result.success(result ?: EmployeeDashboardData(userId)))
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }

    suspend fun getEmployerDashboardData(): Flow<Result<EmployerDashboardData>> = flow {
        try {
            val userId = authRepository.getCurrentUserId() ?: throw Exception("User not authenticated")

            val result = supabaseClient
                .table("employer_dashboard")
                .select {
                    filter {
                        eq("user_id", userId)
                    }
                }
                .decodeSingleOrNull<EmployerDashboardData>()

            emit(Result.success(result ?: EmployerDashboardData(userId)))
        } catch (e: Exception) {
            emit(Result.failure(e))
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
        } catch (e: Exception) {
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
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }

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
            // Enhance activities with user names
            val enhancedActivities = result.map { activity ->
                // Get user name
                val userName = profileRepository.getProfileByUserId(activity.userId)?.fullName ?: "Unknown User"

                // Get target user name if exists
                val targetUserName = if (activity.targetUserId != null) {
                    profileRepository.getProfileByUserId(activity.targetUserId)?.fullName ?: "Unknown User"
                } else {
                    ""
                }

                activity.copy(
                    userName = userName,
                    targetUserName = targetUserName
                )
            }

            emit(Result.success(enhancedActivities))
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }
}