package com.example.gigs.data.repository

// AuthRepository.kt

import com.example.gigs.data.model.AuthState
import com.example.gigs.data.model.OtpState
import com.example.gigs.data.model.User
import com.example.gigs.data.remote.SupabaseClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton
import android.app.Activity
import android.util.Log
import com.example.gigs.data.remote.FirebaseAuthManager
import kotlinx.coroutines.delay
import com.example.gigs.data.model.UserType
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await


@Singleton
class AuthRepository @Inject constructor(
    private val firebaseAuthManager: FirebaseAuthManager,
    private val supabaseClient: SupabaseClient
) {
    // Send OTP to the phone number using Firebase
    fun sendOtp(phoneNumber: String, activity: Activity): Flow<OtpState> {
        return firebaseAuthManager.sendOtp(phoneNumber, activity)
    }

    // Verify OTP and sign in using Firebase
    fun verifyOtp(otp: String): Flow<OtpState> {
        return firebaseAuthManager.verifyOtp(otp)
    }

    suspend fun getCurrentSessionToken(): String? {
        return supabaseClient.getCurrentSessionToken()
    }

    // Add this method to AuthRepository
    suspend fun getIdToken(): String? {
        // Get the token from Firebase
        return try {
            FirebaseAuth.getInstance().currentUser?.getIdToken(false)?.await()?.token
        } catch (e: Exception) {
            Log.e("AuthRepository", "Error getting ID token", e)
            null
        }
    }

    // Check authentication state
    suspend fun getAuthState(): Flow<AuthState> = flow {
        emit(AuthState.Initial)

        try {
            if (firebaseAuthManager.isAuthenticated()) {
                val userId = firebaseAuthManager.getCurrentUserId()
                if (userId != null) {
                    // Get user from Supabase database
                    var user = getUserById(userId)

                    // If user doesn't exist in Supabase, create it
                    if (user == null) {
                        // Get phone number from Firebase or use default if not available
                        val phone = firebaseAuthManager.getCurrentUserPhone() ?: "Unknown Phone"

                        println("DEBUG: User not found in Supabase. Creating new user with ID: $userId")

                        // Create new user
                        val newUser = User(
                            userId = userId,
                            phone = phone,
                            isProfileCompleted = false,
                            userType = UserType.UNDEFINED
                        )

                        try {
                            supabaseClient.table("users")
                                .insert(newUser)
                                .decodeSingle<User>()

                            println("DEBUG: User created successfully in Supabase")

                            // Fetch the user we just created
                            user = getUserById(userId)
                        } catch (e: Exception) {
                            println("ERROR: Failed to create user in Supabase: ${e.message}")
                            emit(AuthState.ProfileIncomplete) // Continue with profile setup anyway
                            return@flow
                        }
                    }

                    // Now check profile completion status
                    if (user != null && user.isProfileCompleted) {
                        emit(AuthState.Authenticated)
                    } else {
                        emit(AuthState.ProfileIncomplete)
                    }
                } else {
                    emit(AuthState.Unauthenticated)
                }
            } else {
                emit(AuthState.Unauthenticated)
            }
        } catch (e: Exception) {
            println("ERROR in getAuthState: ${e.message}")
            emit(AuthState.Error(e.message ?: "Unknown error occurred"))
        }
    }

    // Sign out user from Firebase
    fun signOut() {
        firebaseAuthManager.signOut()
    }

    // Get user by id from Supabase database
    private suspend fun getUserById(userId: String): User? {
        return try {
            supabaseClient.table("users")
                .select {
                    filter {
                        eq("user_id", userId)
                    }
                }
                .decodeSingleOrNull<User>()
        } catch (e: Exception) {
            null
        }
    }

    // Get current user ID from Firebase
    fun getCurrentUserId(): String? {
        return firebaseAuthManager.getCurrentUserId()
    }
/*
    suspend fun isUserAdmin(userId: String): Boolean {
        // This is a placeholder implementation - in a real app you would:
        // 1. Check a user_roles table in your database
        // 2. Or check a claims/roles field in the user's JWT token
        // 3. Or use a dedicated admin service/API

        val adminUserIds = listOf(
            "admin1", // Add your admin user IDs here
            "admin2",
            userId // For testing purposes, consider the requesting user an admin (remove in production)
        )

        return adminUserIds.contains(userId)
    }


 */
    /**
     * Check if the current user has admin privileges
     */
    suspend fun isUserAdmin(): Boolean {
        return try {
            val userId = getCurrentUserId() ?: return false

            val user = supabaseClient
                .table("users")
                .select {
                    filter {
                        eq("user_id", userId)
                    }
                    limit(1)
                }
                .decodeSingleOrNull<User>() // or decodeList<User>().firstOrNull()

            user?.isAdmin ?: false
        } catch (e: Exception) {
            println("ERROR in isCurrentUserAdmin: ${e.message}")
            false
        }
    }


}
