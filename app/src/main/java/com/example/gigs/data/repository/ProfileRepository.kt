// Updated ProfileRepository.kt
package com.example.gigs.data.repository

import com.example.gigs.data.model.User

// Updated ProfileRepository.kt for Supabase 3.0.3

import com.example.gigs.data.model.EmployeeProfile
import com.example.gigs.data.model.EmployeeProfileWithUserInfo
import com.example.gigs.data.model.EmployerProfile
import com.example.gigs.data.model.Profile
import com.example.gigs.data.model.UserType
import com.example.gigs.data.remote.FirebaseAuthManager
import com.example.gigs.data.remote.SupabaseClient
import io.github.jan.supabase.annotations.SupabaseExperimental
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Count
import io.github.jan.supabase.storage.upload
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton
import com.example.gigs.data.model.UserProfileStatusUpdate

@Singleton
class ProfileRepository @Inject constructor(
    val firebaseAuthManager: FirebaseAuthManager,
    val supabaseClient: SupabaseClient
) {


    suspend fun ProfileRepository.getEmployerProfileByUserId(userId: String): kotlinx.coroutines.flow.Flow<Result<EmployerProfile>> = kotlinx.coroutines.flow.flow {
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

    // Create or update basic profile
    suspend fun createOrUpdateProfile(profile: Profile): Flow<Result<Profile>> = flow {
        try {
            // First, check if the user exists in Firebase
            val userId = firebaseAuthManager.getCurrentUserId() ?: throw Exception("User not authenticated")

            // Check if profile already exists in Supabase
            val existingProfile = getProfileByUserId(userId)

            val updatedProfile = profile.copy(userId = userId)

            // Upsert the profile (insert if not exists, update if exists)
            val result = if (existingProfile == null) {
                // Create new profile
                supabaseClient.table("profiles")
                    .insert(updatedProfile)
                    .decodeSingle<Profile>()
            } else {
                // Update existing profile
                supabaseClient.table("profiles")
                    .update(updatedProfile) {
                        filter {
                            eq("user_id", userId)
                        }
                    }
                    .decodeSingle<Profile>()
            }

            // Also update the user's isProfileCompleted flag
            updateUserProfileStatus(userId, true, profile.userType)

            emit(Result.success(result))
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }

    @OptIn(SupabaseExperimental::class)
    suspend fun createOrUpdateEmployeeProfile(employeeProfile: EmployeeProfile): Flow<Result<EmployeeProfile>> = flow {
        try {
            val userId = firebaseAuthManager.getCurrentUserId() ?: throw Exception("User not authenticated")

            // Debug logs
            println("DEBUG: userId value: $userId")
            println("DEBUG: userId type: ${userId::class.simpleName}")

            val updatedEmployeeProfile = employeeProfile.copy(userId = userId)

            println("DEBUG: updatedEmployeeProfile = $updatedEmployeeProfile")

            // Check if the user exists
            val user = getUserById(userId)

            if (user == null) {
                println("DEBUG: User doesn't exist, creating it first")
                val phone = firebaseAuthManager.getCurrentUserPhone() ?: "Unknown"

                val newUser = User(
                    userId = userId,
                    phone = phone,
                    isProfileCompleted = false,
                    userType = UserType.EMPLOYEE
                )

                try {
                    // Use the same pattern as in your getUserById method
                    supabaseClient.table("users")
                        .insert(newUser) {
                            headers["Prefer"] = "return=representation"
                        }
                        .decodeSingleOrNull<User>()

                    println("DEBUG: User created successfully")
                } catch (e: Exception) {
                    println("ERROR: Failed to create user: ${e.message}")
                    emit(Result.failure(Exception("Failed to create user record: ${e.message}")))
                    return@flow
                }
            }

            // Try a simpler approach - insert first without decoding to avoid EOF error
            try {
                // Step 1: Insert without expecting data back
                supabaseClient.table("employee_profiles")
                    .insert(updatedEmployeeProfile) {
                        headers["Prefer"] = "return=minimal" // Don't try to return data
                    }

                println("DEBUG: Profile inserted successfully")

                // Step 2: Then fetch the profile separately
                val createdProfile = supabaseClient.table("employee_profiles")
                    .select {
                        filter {
                            eq("user_id", userId)
                        }
                    }
                    .decodeSingleOrNull<EmployeeProfile>()

                if (createdProfile != null) {
                    try {
                        updateUserProfileStatus(userId, true, UserType.EMPLOYEE)
                        println("DEBUG: User profile status updated successfully")
                    } catch (e: Exception) {
                        println("WARNING: Updated profile but failed to update user status: ${e.message}")
                    }

                    emit(Result.success(createdProfile))
                } else {
                    emit(Result.failure(Exception("Profile was created but couldn't be retrieved")))
                }
            } catch (e: Exception) {
                println("ERROR in insert: ${e.message}")
                println("Error details: ${e.stackTraceToString()}")
                emit(Result.failure(e))
            }
        } catch (e: Exception) {
            println("ERROR in createOrUpdateEmployeeProfile: ${e.message}")
            println("JSON input: $employeeProfile")
            emit(Result.failure(e))
        }
    }



    // Upload profile photo to Supabase storage
    suspend fun uploadProfilePhoto(userId: String, photoBytes: ByteArray): Flow<Result<String>> = flow {
        try {
            val filename = "profile_$userId.jpg"
            val bucket = supabaseClient.bucket("profile-photos")

            // Upload the photo - with correct ContentType
            bucket.upload(path = filename, data = photoBytes) {
                upsert = true
                contentType = io.ktor.http.ContentType.Image.JPEG
            }

            // Get the public URL
            val publicUrl = bucket.publicUrl(filename)

            emit(Result.success(publicUrl))
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }

    // Create or update employer profile
    @OptIn(SupabaseExperimental::class)
    suspend fun createOrUpdateEmployerProfile(employerProfile: EmployerProfile): Flow<Result<EmployerProfile>> = flow {
        try {
            val userId = firebaseAuthManager.getCurrentUserId() ?: throw Exception("User not authenticated")

            println("DEBUG: userId value: $userId")

            // Create updated profile with the correct userId
            val updatedEmployerProfile = employerProfile.copy(userId = userId)
            println("DEBUG: updatedEmployerProfile = $updatedEmployerProfile")

            // Ensure user exists in the users table
            val user = getUserById(userId)

            if (user == null) {
                println("DEBUG: User doesn't exist, creating it first")

                val phone = firebaseAuthManager.getCurrentUserPhone() ?: "Unknown"
                val newUser = User(
                    userId = userId,
                    phone = phone,
                    isProfileCompleted = false,
                    userType = UserType.EMPLOYER
                )

                try {
                    supabaseClient.table("users")
                        .insert(newUser) {
                            headers["Prefer"] = "return=minimal"  // Don't expect data back to avoid EOF errors
                        }

                    println("DEBUG: User created successfully")
                } catch (e: Exception) {
                    println("ERROR: Failed to create user: ${e.message}")
                    emit(Result.failure(Exception("Failed to create user record: ${e.message}")))
                    return@flow
                }
            }

            // Try a similar approach to what worked in createOrUpdateEmployeeProfile
            try {
                // First insert with minimal return to avoid EOF error
                supabaseClient.table("employer_profiles")
                    .upsert(listOf(updatedEmployerProfile)) {
                        onConflict = "user_id"
                        headers["Prefer"] = "return=minimal"  // Don't try to return data
                    }

                println("DEBUG: Profile inserted/updated successfully")

                // Then fetch the profile separately
                val resultProfile = supabaseClient.table("employer_profiles")
                    .select {
                        filter {
                            eq("user_id", userId)
                        }
                    }
                    .decodeSingleOrNull<EmployerProfile>()

                if (resultProfile != null) {
                    // Update user's profile status
                    try {
                        updateUserProfileStatus(userId, true, UserType.EMPLOYER)
                        println("DEBUG: User profile status updated successfully")
                    } catch (e: Exception) {
                        println("WARNING: Updated profile but failed to update user status: ${e.message}")
                    }

                    emit(Result.success(resultProfile))
                } else {
                    emit(Result.failure(Exception("Profile was created/updated but couldn't be retrieved")))
                }
            } catch (e: Exception) {
                println("ERROR in insert/upsert: ${e.message}")
                println("Error details: ${e.stackTraceToString()}")
                emit(Result.failure(e))
            }
        } catch (e: Exception) {
            println("ERROR in createOrUpdateEmployerProfile: ${e.message}")
            println("JSON input: $employerProfile")
            emit(Result.failure(e))
        }
    }

    // Get profile by user ID
    suspend fun getProfileByUserId(userId: String): Profile? {
        return try {
            supabaseClient.table("profiles")
                .select {
                    filter {
                        eq("user_id", userId)
                    }
                }
                .decodeSingleOrNull<Profile>()
        } catch (e: Exception) {
            null
        }
    }

    // Update user's profile completion status
    @OptIn(SupabaseExperimental::class)
    private suspend fun updateUserProfileStatus(userId: String, isCompleted: Boolean, userType: UserType) {
        try {
            val updateData = UserProfileStatusUpdate(
                isCompleted,
                userType.toString()
            )

            supabaseClient.table("users")
                .update(updateData) {
                    filter { eq("user_id", userId) }
                    select(Columns.list("id"))
                    headers["Prefer"] = "return=minimal"
                }

            println("DEBUG: User profile status updated successfully")
        } catch (e: Exception) {
            println("ERROR in updateUserProfileStatus: ${e.message}")
        }
    }
    suspend fun getEmployeeProfileByUserId(userId: String): Flow<Result<EmployeeProfile>> = flow {
        try {
            val profile = supabaseClient
                .table("employee_profiles")
                .select {
                    filter {
                        eq("user_id", userId)
                    }
                }
                .decodeSingleOrNull<EmployeeProfile>()

            if (profile != null) {
                emit(Result.success(profile))
            } else {
                emit(Result.failure(Exception("Employee profile not found")))
            }
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }

    // In ProfileRepository.kt
    // In ProfileRepository.kt
    fun getEmployeeProfileWithUserInfo(userId: String): Flow<Result<EmployeeProfileWithUserInfo>> = flow {
        try {
            // Get user from Supabase users table
            val user = getUserById(userId)

            // Get employee profile from Supabase
            val employeeProfile = supabaseClient.table("employee_profiles")
                .select {
                    filter { eq("user_id", userId) }
                }
                .decodeSingleOrNull<EmployeeProfile>()

            if (user != null && employeeProfile != null) {
                val combinedProfile = EmployeeProfileWithUserInfo(
                    employeeProfile = employeeProfile,
                    phone = user.phone,
                    userType = user.userType,
                    isAdmin = user.isAdmin ?: false // Add null safety
                )
                emit(Result.success(combinedProfile))
            } else {
                emit(Result.failure(Exception("Profile or user information not found")))
            }
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }


    // Create user record in Supabase if it doesn't exist
    suspend fun createUserIfNotExists(phone: String): Result<User> {
        return try {
            val userId = firebaseAuthManager.getCurrentUserId() ?: throw Exception("User not authenticated")

            // Print debug info
            println("DEBUG: Creating user with ID: $userId and phone: $phone")

            // Check if user exists in Supabase database
            val existingUser = getUserById(userId)

            if (existingUser != null) {
                println("DEBUG: User already exists in database")
                Result.success(existingUser)
            } else {
                println("DEBUG: Creating new user in database")
                // Create new user in Supabase
                val newUser = User(
                    userId = userId, // Using Firebase UID as primary key
                    phone = phone,
                    isProfileCompleted = false,
                    userType = UserType.UNDEFINED
                )

                try {
                    val result = supabaseClient.table("users")
                        .insert(newUser)
                        .decodeSingle<User>()

                    println("DEBUG: User created successfully: $result")
                    Result.success(result)
                } catch (e: Exception) {
                    println("ERROR in user creation: ${e.message}")
                    println("ERROR details: ${e.stackTraceToString()}")

                    // Try a simpler insert without decoding
                    try {
                        // Try alternative approach - just insert and don't decode
                        supabaseClient.table("users")
                            .insert(newUser)

                        println("DEBUG: User created with alternative method")

                        // Get the user we just created
                        val createdUser = getUserById(userId)
                        if (createdUser != null) {
                            return Result.success(createdUser)
                        } else {
                            return Result.failure(Exception("User created but couldn't be retrieved"))
                        }
                    } catch (e2: Exception) {
                        println("ERROR in alternative user creation: ${e2.message}")
                        return Result.failure(e2)
                    }
                }
            }
        } catch (e: Exception) {
            println("ERROR in createUserIfNotExists: ${e.message}")
            Result.failure(e)
        }
    }

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
}