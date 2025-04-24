package com.example.gigs.data.model

import kotlinx.serialization.SerialName

@kotlinx.serialization.Serializable
data class UserProfileStatusUpdate(
    @SerialName("is_profile_completed") val isProfileCompleted: Boolean,
    @SerialName("user_type") val userType: String
)
