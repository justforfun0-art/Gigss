package com.example.gigs.data.model
// Updated EmployeeProfile.kt
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class EmployeeProfile(
    @SerialName("profile_id")
    val profileId: String = "",

    @SerialName("user_id")
    val userId: String = "", // Primary key linking to auth user

    val name: String = "",

    val dob: String = "", // Store as ISO format string

    val gender: Gender = Gender.OTHER,

    @SerialName("has_computer_knowledge")
    val hasComputerKnowledge: Boolean = false,

    val state: String = "",

    val district: String = "",

    val email: String? = null,

    @SerialName("profile_photo_url")
    val profilePhotoUrl: String? = null,

    @SerialName("work_preferences")
    val workPreferences: List<WorkPreference> = emptyList(),

    @SerialName("created_at")
    val createdAt: String? = null,

    @SerialName("updated_at")
    val updatedAt: String? = null
)

enum class Gender {
    MALE, FEMALE, OTHER
}

@Serializable
enum class WorkPreference {
    FULL_TIME, PART_TIME, WEEKDAY, WEEKEND,TEMPORARY
}