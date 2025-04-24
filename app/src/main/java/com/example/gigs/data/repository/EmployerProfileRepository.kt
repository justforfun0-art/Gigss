package com.example.gigs.data.repository

import com.example.gigs.data.model.EmployerProfile
import com.example.gigs.data.remote.SupabaseClient
import io.github.jan.supabase.annotations.SupabaseExperimental
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Count
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.json.encodeToJsonElement


@Singleton
class EmployerProfileRepository @Inject constructor(
    private val supabaseClient: SupabaseClient,
    private val authRepository: AuthRepository
) {
    /**
     * Get employer profile by user ID
     */
    fun getEmployerProfile(userId: String): Flow<Result<EmployerProfile>> = flow {
        try {
            val profile = supabaseClient
                .table("employer_profiles")
                .select {
                    filter {
                        eq("user_id", userId)
                    }
                }
                .decodeSingleOrNull<EmployerProfile>()


            if (profile != null) {
                emit(Result.success(profile))
            } else {
                emit(Result.failure(Exception("Employer profile not found")))
            }
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }

    /**
     * Get current employer's profile
     */
    suspend fun getCurrentEmployerProfile(): Flow<Result<EmployerProfile>> = flow {
        try {
            val userId = authRepository.getCurrentUserId() ?: throw Exception("User not authenticated")

            getEmployerProfile(userId).collect { result ->
                emit(result)
            }
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }

    /**
     * Update employer profile
     */
    @OptIn(SupabaseExperimental::class)
    suspend fun updateEmployerProfile(
        employerProfile: EmployerProfile
    ): Flow<Result<EmployerProfile>> = flow {
        try {
            val userId = authRepository.getCurrentUserId() ?: throw Exception("User not authenticated")

            if (userId != employerProfile.userId) {
                throw Exception("You can only update your own profile")
            }

            // ✅ Convert to JsonObject (this prevents bad payload issues)
            val profileJson = supabaseClient.customJson.encodeToJsonElement(employerProfile)

            // ⛔ DO NOT send raw Kotlin object
            val result = supabaseClient.client.postgrest["employer_profiles"]
                .update(profileJson) {
                    filter {
                        eq("user_id", userId)
                    }
                    headers["Prefer"] = "return=representation"
                }
                .decodeSingle<EmployerProfile>()

            emit(Result.success(result))

        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }


    /**
     * Get employer statistics - jobs posted, active jobs, applications received
     */
    suspend fun getEmployerStats(): Flow<Result<EmployerStats>> = flow {
        try {
            val userId = authRepository.getCurrentUserId() ?: throw Exception("User not authenticated")

            // Get stats from employer_dashboard view
            val stats = supabaseClient
                .table("employer_dashboard")
                .select {
                    filter {
                        eq("user_id", userId)
                    }
                }
                .decodeSingleOrNull<EmployerStats>()

            if (stats != null) {
                emit(Result.success(stats))
            } else {
                // If no stats found, return default empty stats
                emit(Result.success(EmployerStats(userId = userId)))
            }
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }

    /**
     * Check if a user is an employer
     */
    suspend fun isEmployer(userId: String): Flow<Result<Boolean>> = flow {
        try {
            val result = supabaseClient.client.postgrest["employer_profiles"]
                .select {
                    filter {
                        eq("user_id", userId)
                    }
                    count(Count.EXACT)
                }
                .decodeList<Map<String, Any>>()

            val count = result.size
            emit(Result.success(count > 0))
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }
}

/**
 * Model for employer statistics
 */
data class EmployerStats(
    val userId: String,
    val totalJobs: Int = 0,
    val activeJobs: Int = 0,
    val totalApplicationsReceived: Int = 0,
    val averageRating: Float = 0f,
    val reviewCount: Int = 0
)