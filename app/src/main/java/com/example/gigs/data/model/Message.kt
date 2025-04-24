package com.example.gigs.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class Message(
    val id: String = "",

    @SerialName("conversation_id")
    val conversationId: String,

    @SerialName("sender_id")
    val senderId: String,

    @SerialName("receiver_id")
    val receiverId: String,

    val message: String,

    @SerialName("is_read")
    val isRead: Boolean = false,

    @SerialName("created_at")
    val createdAt: String? = null
)

@Serializable
data class Conversation(
    val id: String = "",

    @SerialName("job_id")
    val jobId: String? = null,

    @SerialName("employer_id")
    val employerId: String,

    @SerialName("employee_id")
    val employeeId: String,

    @SerialName("last_message_at")
    val lastMessageAt: String? = null,

    @SerialName("created_at")
    val createdAt: String? = null,

    // Additional fields for UI display
    @Transient
    val otherUserName: String = "",

    @Transient
    val lastMessage: String = "",

    @Transient
    val unreadCount: Int = 0
)