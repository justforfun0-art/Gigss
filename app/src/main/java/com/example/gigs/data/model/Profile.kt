package com.example.gigs.data.model

// Profile.kt
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


@Serializable
data class Profile(
    val id: String = "",

    @SerialName("user_id")
    val userId: String = "",

    @SerialName("full_name")
    val fullName: String = "",

    val email: String? = null,

    @SerialName("avatar_url")
    val avatarUrl: String? = null,

    @SerialName("user_type")
    val userType: UserType = UserType.UNDEFINED,

    @SerialName("is_profile_complete")
    val isProfileComplete: Boolean = false,

    @SerialName("created_at")
    val createdAt: String? = null,

    @SerialName("updated_at")
    val updatedAt: String? = null
)

@Serializable
data class EmployerProfile(
    @SerialName("profile_id")
    val profileId: String = "",

    @SerialName("user_id")
    val userId: String = "", // Primary key linking to auth user

    @SerialName("company_name")
    val companyName: String = "",

    val industry: String = "",

    @SerialName("company_size")
    val companySize: String = "",

    val state: String = "",

    val district: String = "",

    val website: String? = null,

    val description: String? = null, // Changed to nullable to match DB

    @SerialName("created_at")
    val createdAt: String? = null,

    @SerialName("updated_at")
    val updatedAt: String? = null
)

data class JobWithEmployer(
    val job: Job,
    val employerName: String
)