package com.example.gigs.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gigs.data.model.Notification
import com.example.gigs.data.repository.NotificationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NotificationViewModel @Inject constructor(
    private val notificationRepository: NotificationRepository
) : ViewModel() {
    private val _notifications = MutableStateFlow<List<Notification>>(emptyList())
    val notifications: StateFlow<List<Notification>> = _notifications

    private val _unreadCount = MutableStateFlow(0)
    val unreadCount: StateFlow<Int> = _unreadCount

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    fun loadNotifications() {
        viewModelScope.launch {
            _isLoading.value = true
            notificationRepository.getNotifications().collect { result ->
                _isLoading.value = false
                if (result.isSuccess) {
                    _notifications.value = result.getOrNull() ?: emptyList()

                    // Update unread count
                    _unreadCount.value = _notifications.value.count { !it.isRead }
                }
            }
        }
    }

    fun loadUnreadCount() {
        viewModelScope.launch {
            notificationRepository.getUnreadCount().collect { result ->
                if (result.isSuccess) {
                    _unreadCount.value = result.getOrNull() ?: 0
                }
            }
        }
    }

    fun markAsRead(notificationId: String) {
        viewModelScope.launch {
            notificationRepository.markAsRead(notificationId).collect { result ->
                if (result.isSuccess && result.getOrNull() == true) {
                    // Update local state optimistically
                    _notifications.value = _notifications.value.map {
                        if (it.id == notificationId) it.copy(isRead = true) else it
                    }

                    // Update unread count
                    _unreadCount.value = _notifications.value.count { !it.isRead }
                }
            }
        }
    }

    fun markAllAsRead() {
        viewModelScope.launch {
            notificationRepository.markAllAsRead().collect { result ->
                if (result.isSuccess && result.getOrNull() == true) {
                    // Update local state optimistically
                    _notifications.value = _notifications.value.map {
                        it.copy(isRead = true)
                    }

                    // Update unread count
                    _unreadCount.value = 0
                }
            }
        }
    }
}