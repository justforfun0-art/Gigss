package com.example.gigs.data.repository

import android.service.autofill.Validators.and
import com.example.gigs.data.model.Conversation
import com.example.gigs.data.model.Message
import com.example.gigs.data.model.Profile
import com.example.gigs.data.remote.SupabaseClient
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Count
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.Calendar
import java.util.Objects.isNull
import javax.inject.Inject

class MessageRepository @Inject constructor(
    private val supabaseClient: SupabaseClient,
    private val authRepository: AuthRepository
) {
    suspend fun getConversations(): Flow<Result<List<Conversation>>> = flow {
        try {
            val userId = authRepository.getCurrentUserId() ?: throw Exception("User not authenticated")

            val conversations = supabaseClient.table("conversations")
                .select {
                    filter {
                        or {
                            eq("employer_id", userId)
                            eq("employee_id", userId)
                        }
                    }
                    order("last_message_at", Order.ASCENDING)
                }
                .decodeList<Conversation>()

            // Enhance conversations with additional data
            val enhancedConversations = conversations.map { conversation ->
                // Determine the other user ID
                val otherUserId = if (conversation.employerId == userId)
                    conversation.employeeId else conversation.employerId

                // Get other user's name
                val otherUser = getUserById(otherUserId)

                // Get last message
                val lastMessage = getLastMessage(conversation.id)

                // Get unread count
                val unreadCount = getUnreadCount(conversation.id, userId)

                conversation.copy(
                    otherUserName = otherUser?.fullName ?: "Unknown User",
                    lastMessage = lastMessage?.message ?: "",
                    unreadCount = unreadCount
                )
            }

            emit(Result.success(enhancedConversations))
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }

    private suspend fun getUserById(userId: String): Profile? {
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

    private suspend fun getLastMessage(conversationId: String): Message? {
        return try {
            supabaseClient.table("messages")
                .select {
                    filter {
                        eq("conversation_id", conversationId)
                    }
                    order("created_at", Order.ASCENDING)
                    limit(1)
                }
                .decodeSingleOrNull<Message>()
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun getUnreadCount(conversationId: String, userId: String): Int {
        return try {
            val result = supabaseClient
                .table("messages")
                .select {
                    count(Count.EXACT)  // Using Count.EXACT instead of a string
                    filter {
                        eq("conversation_id", conversationId)
                        eq("receiver_id", userId)
                        eq("is_read", false)
                    }
                }
                .decodeSingle<Map<String, Int>>()

            result["count"] ?: 0
        } catch (e: Exception) {
            0
        }
    }


    suspend fun getMessages(conversationId: String): Flow<Result<List<Message>>> = flow {
        try {
            val messages = supabaseClient.table("messages")
                .select {
                    filter {
                        eq("conversation_id", conversationId)
                    }
                    order("created_at", Order.DESCENDING)
                }
                .decodeList<Message>()

            emit(Result.success(messages))
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }

    suspend fun sendMessage(
        conversationId: String,
        receiverId: String,
        messageText: String
    ): Flow<Result<Message>> = flow {
        try {
            val senderId = authRepository.getCurrentUserId() ?: throw Exception("User not authenticated")

            val message = Message(
                conversationId = conversationId,
                senderId = senderId,
                receiverId = receiverId,
                message = messageText
            )

            val result = supabaseClient
                .table("messages")
                .insert(message)
                .decodeSingle<Message>()

// Update last_message_at in conversation
            supabaseClient
                .table("conversations")
                .update(
                    mapOf("last_message_at" to Calendar.getInstance().time)
                ) {
                    filter {
                        eq("id", conversationId)
                    }
                }
            emit(Result.success(result))

        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }

    suspend fun createConversation(
        jobId: String?,
        employerId: String,
        employeeId: String
    ): Flow<Result<String>> = flow {
        try {
            val userId = authRepository.getCurrentUserId() ?: throw Exception("User not authenticated")

            // Check if user is either the employer or employee
            if (userId != employerId && userId != employeeId) {
                throw Exception("Not authorized to create this conversation")
            }

            // Check if conversation already exists
            val existingConversation = if (jobId != null) {
                supabaseClient
                    .table("conversations")
                    .select {
                        filter {
                            eq("job_id", jobId)
                            eq("employer_id", employerId)
                            eq("employee_id", employeeId)
                        }
                    }
                    .decodeSingleOrNull<Conversation>()
            }
            else {
                supabaseClient
                    .table("conversations")
                    .select {
                        filter {
                            isNull("job_id")
                            eq("employer_id", employerId)
                            eq("employee_id", employeeId)
                        }
                    }
                    .decodeSingleOrNull<Conversation>()
            }

            if (existingConversation != null) {
                emit(Result.success(existingConversation.id))
                return@flow
            }

            // Create new conversation
            val conversation = Conversation(
                jobId = jobId,
                employerId = employerId,
                employeeId = employeeId
            )

            val result = supabaseClient.table("conversations")
                .insert(conversation)
                .decodeSingle<Conversation>()

            emit(Result.success(result.id))
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }

    suspend fun markMessagesAsRead(conversationId: String): Flow<Result<Boolean>> = flow {
        try {
            val userId = authRepository.getCurrentUserId() ?: throw Exception("User not authenticated")

            supabaseClient.table("messages")
                .update(
                    mapOf("is_read" to true)
                ) {
                    filter {
                        eq("conversation_id", conversationId)
                        eq("receiver_id", userId)
                        eq("is_read", false)
                    }
                }
            emit(Result.success(true))
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }
}