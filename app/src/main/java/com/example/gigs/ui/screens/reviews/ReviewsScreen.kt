package com.example.gigs.ui.screens.reviews

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarHalf
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.gigs.data.model.Review
import com.example.gigs.ui.components.GigWorkPrimaryButton
import com.example.gigs.utils.DateUtils.formatDate
import com.example.gigs.viewmodel.ReviewState
import com.example.gigs.viewmodel.ReviewViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewsScreen(
    viewModel: ReviewViewModel = hiltViewModel(),
    isMyReviews: Boolean = false,
    jobId: String? = null,
    onBackPressed: () -> Unit
) {
    val reviews by viewModel.reviews.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    // Load appropriate reviews
    LaunchedEffect(isMyReviews, jobId) {
        if (jobId != null) {
            viewModel.loadJobReviews(jobId)
        } else {
            viewModel.loadUserReviews(!isMyReviews)  // If isMyReviews=true, load reviews about me
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when {
                            jobId != null -> "Job Reviews"
                            isMyReviews -> "My Reviews"
                            else -> "Reviews About Me"
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            } else if (reviews.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = when {
                            jobId != null -> "No reviews for this job yet"
                            isMyReviews -> "You haven't written any reviews yet"
                            else -> "You haven't received any reviews yet"
                        },
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = when {
                            jobId != null -> "Be the first to leave a review"
                            isMyReviews -> "Rate your experience with employers or employees"
                            else -> "Complete jobs to receive reviews"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                ) {
                    item {
                        Spacer(modifier = Modifier.height(16.dp))

                        // Display average rating
                        val averageRating = reviews.map { it.rating }.average()

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = String.format("%.1f", averageRating),
                                    style = MaterialTheme.typography.headlineLarge
                                )

                                Spacer(modifier = Modifier.width(8.dp))

                                RatingBar(
                                    rating = averageRating.toFloat(),
                                    modifier = Modifier.height(24.dp)
                                )
                            }

                            Text(
                                text = "Based on ${reviews.size} reviews",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Divider()
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    items(reviews.size) { index ->
                        val review = reviews[index]
                        ReviewItem(review = review)

                        if (index < reviews.lastIndex) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Divider()
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ReviewItem(
    review: Review
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = review.reviewerName,
                style = MaterialTheme.typography.titleMedium
            )

            if (review.createdAt != null) {
                Text(
                    text = formatDate(review.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        RatingBar(
            rating = review.rating.toFloat(),
            modifier = Modifier.height(20.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (!review.jobTitle.isNullOrBlank()) {
            Text(
                text = "For: ${review.jobTitle}",
                style = MaterialTheme.typography.bodyMedium,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))
        }

        if (!review.comment.isNullOrBlank()) {
            Text(
                text = review.comment,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateReviewScreen(
    viewModel: ReviewViewModel = hiltViewModel(),
    jobId: String,
    revieweeId: String,
    revieweeName: String,
    onReviewSubmitted: () -> Unit,
    onBackPressed: () -> Unit
) {

    var rating by remember { mutableStateOf(0) }
    var comment by remember { mutableStateOf("") }

    val reviewState by viewModel.reviewState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(reviewState) {
        when (reviewState) {
            is ReviewState.Success -> {
                onReviewSubmitted()
            }
            is ReviewState.Error -> {
                snackbarHostState.showSnackbar(
                    (reviewState as ReviewState.Error).message
                )
            }
            else -> {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Review $revieweeName") },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Rate your experience",
                style = MaterialTheme.typography.titleLarge
            )

            Spacer(modifier = Modifier.height(24.dp))

            RatingSelector(
                currentRating = rating,
                onRatingChanged = { rating = it }
            )

            Spacer(modifier = Modifier.height(32.dp))

            OutlinedTextField(
                value = comment,
                onValueChange = { comment = it },
                label = { Text("Write your review (optional)") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
                maxLines = 7
            )

            Spacer(modifier = Modifier.height(32.dp))

            GigWorkPrimaryButton(
                text = "Submit Review",
                onClick = {
                    viewModel.createReview(jobId, revieweeId, rating, comment.takeIf { it.isNotBlank() })
                },
                enabled = rating > 0 && reviewState !is ReviewState.Loading,
                isLoading = reviewState is ReviewState.Loading
            )
        }
    }
}

@Composable
fun RatingSelector(
    currentRating: Int,
    onRatingChanged: (Int) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 1..5) {
            IconButton(
                onClick = { onRatingChanged(i) },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = if (i <= currentRating) Icons.Filled.Star else Icons.Outlined.Star,
                    contentDescription = "Rate $i",
                    tint = if (i <= currentRating) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(36.dp)
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    Text(
        text = when (currentRating) {
            0 -> "Tap to rate"
            1 -> "Poor"
            2 -> "Below average"
            3 -> "Average"
            4 -> "Good"
            5 -> "Excellent"
            else -> ""
        },
        style = MaterialTheme.typography.titleMedium,
        color = if (currentRating > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
fun RatingBar(
    rating: Float,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
    ) {
        for (i in 1..5) {
            val starIcon = when {
                i <= rating.toInt() -> Icons.Filled.Star
                i - rating > 0 && i - rating < 1 -> Icons.Filled.StarHalf
                else -> Icons.Outlined.Star
            }

            Icon(
                imageVector = starIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}