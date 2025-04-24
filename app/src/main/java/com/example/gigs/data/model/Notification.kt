package com.example.gigs.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Notification(
    val id: String = "",

    @SerialName("user_id")
    val userId: String,

    val type: String,

    val title: String,

    val message: String,

    @SerialName("related_id")
    val relatedId: String? = null,

    @SerialName("is_read")
    val isRead: Boolean = false,

    @SerialName("created_at")
    val createdAt: String? = null
)

@Serializable
enum class NotificationType {
    JOB_APPROVAL,
    JOB_REJECTION,
    NEW_APPLICATION,
    APPLICATION_UPDATE,
    NEW_MESSAGE,
    JOB_MATCH,
    REVIEW_RECEIVED,
    PAYMENT,
    GENERAL;

    // Convert enum to string for database storage
    override fun toString(): String {
        return name.lowercase()
    }

    companion object {
        // Convert string to enum for database retrieval
        fun fromString(value: String): NotificationType {
            return try {
                valueOf(value.uppercase())
            } catch (e: Exception) {
                GENERAL
            }
        }
    }
}