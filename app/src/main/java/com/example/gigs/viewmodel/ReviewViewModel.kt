package com.example.gigs.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gigs.data.model.Review
import com.example.gigs.data.repository.AuthRepository
import com.example.gigs.data.repository.ReviewRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ReviewViewModel @Inject constructor(
    private val reviewRepository: ReviewRepository,
    private val authRepository: AuthRepository
) : ViewModel() {
    private val _reviews = MutableStateFlow<List<Review>>(emptyList())
    val reviews: StateFlow<List<Review>> = _reviews

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _reviewState = MutableStateFlow<ReviewState>(ReviewState.Initial)
    val reviewState: StateFlow<ReviewState> = _reviewState

    fun loadUserReviews(isReviewer: Boolean) {
        viewModelScope.launch {
            _isLoading.value = true
            val userId = authRepository.getCurrentUserId()

            if (userId != null) {
                reviewRepository.getReviewsByUser(userId, isReviewer).collect { result ->
                    _isLoading.value = false
                    if (result.isSuccess) {
                        _reviews.value = result.getOrNull() ?: emptyList()
                    }
                }
            } else {
                _isLoading.value = false
            }
        }
    }

    fun loadJobReviews(jobId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            reviewRepository.getReviewsByJob(jobId).collect { result ->
                _isLoading.value = false
                if (result.isSuccess) {
                    _reviews.value = result.getOrNull() ?: emptyList()
                }
            }
        }
    }

    fun createReview(jobId: String, revieweeId: String, rating: Int, comment: String?) {
        viewModelScope.launch {
            _reviewState.value = ReviewState.Loading
            reviewRepository.createReview(jobId, revieweeId, rating, comment).collect { result ->
                _reviewState.value = if (result.isSuccess) {
                    ReviewState.Success(result.getOrNull()!!)
                } else {
                    ReviewState.Error(result.exceptionOrNull()?.message ?: "Failed to create review")
                }
            }
        }
    }

    fun updateReview(reviewId: String, rating: Int, comment: String?) {
        viewModelScope.launch {
            _reviewState.value = ReviewState.Loading
            reviewRepository.updateReview(reviewId, rating, comment).collect { result ->
                _reviewState.value = if (result.isSuccess) {
                    ReviewState.Success(result.getOrNull()!!)
                } else {
                    ReviewState.Error(result.exceptionOrNull()?.message ?: "Failed to update review")
                }
            }
        }
    }
}

sealed class ReviewState {
    object Initial : ReviewState()
    object Loading : ReviewState()
    data class Success(val review: Review) : ReviewState()
    data class Error(val message: String) : ReviewState()
}