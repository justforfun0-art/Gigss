package com.example.gigs.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gigs.data.model.Conversation
import com.example.gigs.data.model.Message
import com.example.gigs.data.repository.AuthRepository
import com.example.gigs.data.repository.MessageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ConversationsViewModel @Inject constructor(
    private val messageRepository: MessageRepository
) : ViewModel() {
    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
    val conversations: StateFlow<List<Conversation>> = _conversations

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    fun loadConversations() {
        viewModelScope.launch {
            _isLoading.value = true
            messageRepository.getConversations().collect { result ->
                _isLoading.value = false
                if (result.isSuccess) {
                    _conversations.value = result.getOrNull() ?: emptyList()
                }
            }
        }
    }
}

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val messageRepository: MessageRepository,
    private val authRepository: AuthRepository
) : ViewModel() {
    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _currentUserId = MutableStateFlow<String?>(null)
    val currentUserId: StateFlow<String?> = _currentUserId

    init {
        viewModelScope.launch {
            _currentUserId.value = authRepository.getCurrentUserId()
        }
    }

    fun loadMessages(conversationId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            messageRepository.getMessages(conversationId).collect { result ->
                _isLoading.value = false
                if (result.isSuccess) {
                    _messages.value = result.getOrNull() ?: emptyList()
                }
            }

            // Mark messages as read
            messageRepository.markMessagesAsRead(conversationId).collect {}
        }
    }

    fun sendMessage(conversationId: String, receiverId: String, messageText: String) {
        if (messageText.isBlank()) return

        viewModelScope.launch {
            messageRepository.sendMessage(conversationId, receiverId, messageText).collect { result ->
                if (result.isSuccess) {
                    // Optimistically add message to the list
                    val newMessage = result.getOrNull()
                    if (newMessage != null) {
                        _messages.value = listOf(newMessage) + _messages.value
                    }

                    // Reload messages to sync with server
                    loadMessages(conversationId)
                }
            }
        }
    }

    // Create a new function in ChatViewModel.kt
    fun createNewConversation(jobId: String, employerId: String, employeeId: String, onSuccess: (String) -> Unit) {
        viewModelScope.launch {
            messageRepository.createConversation(jobId, employerId, employeeId).collect { result ->
                if (result.isSuccess) {
                    val conversationId = result.getOrNull()
                    if (conversationId != null) {
                        onSuccess(conversationId)
                    }
                }
            }
        }
    }

    suspend fun createConversation(jobId: String?, employerId: String, employeeId: String): Flow<Result<String>> {
        return messageRepository.createConversation(jobId, employerId, employeeId)
    }
}