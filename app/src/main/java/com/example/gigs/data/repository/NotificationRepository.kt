package com.example.gigs.data.repository

import com.example.gigs.data.model.Notification
import com.example.gigs.data.model.NotificationType
import com.example.gigs.data.remote.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Count
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationRepository @Inject constructor(
    private val supabaseClient: SupabaseClient,
    private val authRepository: AuthRepository
) {
    /**
     * Create a new notification
     */
    suspend fun createNotification(notification: Notification): Flow<Result<Notification>> = flow {
        try {
            val result = supabaseClient.client.from("notifications")
                .insert(notification)
                .decodeSingle<Notification>()

            emit(Result.success(result))
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }

    /**
     * Get all notifications for the current user
     */
    suspend fun getMyNotifications(): Flow<Result<List<Notification>>> = flow {
        try {
            val userId = authRepository.getCurrentUserId() ?: throw Exception("User not authenticated")

            val notifications = supabaseClient.client.from("notifications")
                .select {
                    filter { eq("user_id", userId) }
                    order("created_at", Order.DESCENDING)
                }
                .decodeList<Notification>()

            emit(Result.success(notifications))
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }

    /**
     * Alias for getMyNotifications to maintain compatibility with existing code
     */
    suspend fun getNotifications(): Flow<Result<List<Notification>>> = getMyNotifications()

    /**
     * Get unread notifications count for the current user
     */
    suspend fun getUnreadNotificationsCount(): Flow<Result<Int>> = flow {
        try {
            val userId = authRepository.getCurrentUserId() ?: throw Exception("User not authenticated")

            val result = supabaseClient.client.from("notifications")
                .select {
                    filter {
                        eq("user_id", userId)
                        eq("is_read", false)
                    }
                    count(Count.EXACT)
                }
                .decodeList<Map<String, Any>>()

            val count = result.size
            emit(Result.success(count))
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }

    /**
     * Alias for getUnreadNotificationsCount to maintain compatibility
     */
    suspend fun getUnreadCount(): Flow<Result<Int>> = getUnreadNotificationsCount()

    /**
     * Mark a notification as read
     */
    suspend fun markNotificationAsRead(notificationId: String): Flow<Result<Notification>> = flow {
        try {
            val userId = authRepository.getCurrentUserId() ?: throw Exception("User not authenticated")

            val updatedNotification = supabaseClient.client.from("notifications")
                .update(mapOf("is_read" to true)) {
                    filter {
                        eq("id", notificationId)
                        eq("user_id", userId) // Make sure the notification belongs to the user
                    }
                }
                .decodeSingle<Notification>()

            emit(Result.success(updatedNotification))
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }

    /**
     * Alias for markNotificationAsRead to maintain compatibility
     */
    suspend fun markAsRead(notificationId: String): Flow<Result<Boolean>> = flow {
        try {
            markNotificationAsRead(notificationId).collect { result ->
                emit(Result.success(result.isSuccess))
            }
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }

    /**
     * Mark all notifications as read
     */
    suspend fun markAllNotificationsAsRead(): Flow<Result<Boolean>> = flow {
        try {
            val userId = authRepository.getCurrentUserId() ?: throw Exception("User not authenticated")

            supabaseClient.client.from("notifications")
                .update(mapOf("is_read" to true)) {
                    filter {
                        eq("user_id", userId)
                        eq("is_read", false)
                    }
                }

            emit(Result.success(true))
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }

    /**
     * Alias for markAllNotificationsAsRead to maintain compatibility
     */
    suspend fun markAllAsRead(): Flow<Result<Boolean>> = markAllNotificationsAsRead()

    /**
     * Delete a notification
     */
    suspend fun deleteNotification(notificationId: String): Flow<Result<Boolean>> = flow {
        try {
            val userId = authRepository.getCurrentUserId() ?: throw Exception("User not authenticated")

            supabaseClient.client.from("notifications")
                .delete {
                    filter {
                        eq("id", notificationId)
                        eq("user_id", userId) // Make sure the notification belongs to the user
                    }
                }

            emit(Result.success(true))
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }

    /**
     * Get notifications of a specific type
     */
    suspend fun getNotificationsByType(type: NotificationType): Flow<Result<List<Notification>>> = flow {
        try {
            val userId = authRepository.getCurrentUserId() ?: throw Exception("User not authenticated")

            val notifications = supabaseClient.client.from("notifications")
                .select {
                    filter {
                        eq("user_id", userId)
                        eq("type", type.toString())
                    }
                    order("created_at", Order.DESCENDING)
                }
                .decodeList<Notification>()

            emit(Result.success(notifications))
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }

    /**
     * Create job approval notification
     */
    suspend fun createJobApprovalNotification(
        userId: String,
        jobId: String,
        jobTitle: String
    ): Flow<Result<Notification>> {
        val notification = Notification(
            id = "",
            userId = userId,
            type = NotificationType.JOB_APPROVAL.toString(),
            title = "Job Approved",
            message = "Your job \"$jobTitle\" has been approved and is now visible to job seekers.",
            relatedId = jobId,
            isRead = false
        )
        return createNotification(notification)
    }

    /**
     * Create job rejection notification
     */
    suspend fun createJobRejectionNotification(
        userId: String,
        jobId: String,
        jobTitle: String,
        reason: String
    ): Flow<Result<Notification>> {
        val notification = Notification(
            id = "",
            userId = userId,
            type = NotificationType.JOB_REJECTION.toString(),
            title = "Job Rejected",
            message = "Your job \"$jobTitle\" has been rejected. Reason: $reason",
            relatedId = jobId,
            isRead = false
        )
        return createNotification(notification)
    }
}