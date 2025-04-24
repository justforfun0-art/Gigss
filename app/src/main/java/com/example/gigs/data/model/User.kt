// User.kt
package com.example.gigs.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class User(
    val id: String = "", // Supabase record ID

    @SerialName("user_id")
    val userId: String = "", // Firebase User ID as primary key

    val phone: String = "",

    @SerialName("is_profile_completed")
    val isProfileCompleted: Boolean = false,

    @SerialName("user_type")
    val userType: UserType = UserType.UNDEFINED,

    @SerialName("is_admin")
    val isAdmin: Boolean = false,

    @SerialName("created_at")
    val createdAt: String? = null
)

enum class UserType {
    EMPLOYEE,
    EMPLOYER,
    UNDEFINED
}