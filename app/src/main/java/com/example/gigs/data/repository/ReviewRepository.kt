package com.example.gigs.data.repository

import com.example.gigs.data.model.Job
import com.example.gigs.data.model.Review
import com.example.gigs.data.remote.SupabaseClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order

class ReviewRepository @Inject constructor(
    private val supabaseClient: SupabaseClient,
    private val authRepository: AuthRepository,
    private val profileRepository: ProfileRepository
) {
    suspend fun createReview(
        jobId: String,
        revieweeId: String,
        rating: Int,
        comment: String?
    ): Flow<Result<Review>> = flow {
        try {
            val reviewerId = authRepository.getCurrentUserId() ?: throw Exception("User not authenticated")

            if (reviewerId == revieweeId) {
                throw Exception("You cannot review yourself")
            }

            if (rating < 1 || rating > 5) {
                throw Exception("Rating must be between 1 and 5")
            }

            val review = Review(
                jobId = jobId,
                reviewerId = reviewerId,
                revieweeId = revieweeId,
                rating = rating,
                comment = comment
            )

            val result = supabaseClient.table("reviews")
                .insert(review)
                .decodeSingle<Review>()

            emit(Result.success(result))
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }

    suspend fun updateReview(
        reviewId: String,
        rating: Int,
        comment: String?
    ): Flow<Result<Review>> = flow {
        try {
            val reviewerId = authRepository.getCurrentUserId() ?: throw Exception("User not authenticated")

            // Check if the review exists and belongs to the user
            val existingReview = supabaseClient.table("reviews")
                .select {
                    filter {
                        eq("id", reviewId)
                        eq("reviewer_id", reviewerId)
                    }
                }

                .decodeSingleOrNull<Review>()

            if (existingReview == null) {
                throw Exception("Review not found or you don't have permission to edit it")
            }

            if (rating < 1 || rating > 5) {
                throw Exception("Rating must be between 1 and 5")
            }

            val result = supabaseClient
                .table("reviews")
                .update(
                    mapOf(
                        "rating" to rating,
                        "comment" to comment
                    )
                ) {
                    filter {
                        eq("id", reviewId)
                    }
                }
                .decodeSingle<Review>()

            emit(Result.success(result))
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }

    suspend fun getReviewsByUser(userId: String, isReviewer: Boolean): Flow<Result<List<Review>>> = flow {
        try {
            val field = if (isReviewer) "reviewer_id" else "reviewee_id"

            val reviews = supabaseClient
                .table("reviews")
                .select {
                    filter {
                        eq(field, userId)
                    }
                    order("created_at", Order.ASCENDING)
                }
                .decodeList<Review>()

            // Enhance reviews with additional data
            val enhancedReviews = reviews.map { review ->
                // Get reviewer name
                val reviewer = profileRepository.getProfileByUserId(review.reviewerId)

                // Get reviewee name
                val reviewee = profileRepository.getProfileByUserId(review.revieweeId)

                // Get job title
                val job = getJobById(review.jobId)

                review.copy(
                    reviewerName = reviewer?.fullName ?: "Unknown User",
                    revieweeName = reviewee?.fullName ?: "Unknown User",
                    jobTitle = job?.title ?: "Unknown Job"
                )
            }

            emit(Result.success(enhancedReviews))
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }

    suspend fun getReviewsByJob(jobId: String): Flow<Result<List<Review>>> = flow {
        try {
            val reviews = supabaseClient
                .table("reviews")
                .select {
                    filter {
                        eq("job_id", jobId)
                    }
                    order("created_at", Order.ASCENDING)
                }
                .decodeList<Review>()
            // Enhance reviews with additional data
            val enhancedReviews = reviews.map { review ->
                // Get reviewer name
                val reviewer = profileRepository.getProfileByUserId(review.reviewerId)

                // Get reviewee name
                val reviewee = profileRepository.getProfileByUserId(review.revieweeId)

                review.copy(
                    reviewerName = reviewer?.fullName ?: "Unknown User",
                    revieweeName = reviewee?.fullName ?: "Unknown User",
                    jobTitle = "This Job"
                )
            }

            emit(Result.success(enhancedReviews))
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }

    private suspend fun getJobById(jobId: String): Job? {
        return try {
            supabaseClient.table("jobs")
                .select {
                    filter {
                        eq("id", jobId)
                    }
                }
                .decodeSingleOrNull<Job>()
        } catch (e: Exception) {
            null
        }
    }
}