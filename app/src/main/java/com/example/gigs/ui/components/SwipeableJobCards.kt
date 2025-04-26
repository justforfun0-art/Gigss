package com.example.gigs.ui.components

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gigs.data.model.Job
import com.example.gigs.data.model.JobWithEmployer
import com.example.gigs.ui.theme.PrimaryBlue
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.absoluteValue

@Composable
fun SwipeableJobCards(
    jobs: List<Job>,
    jobsWithEmployers: List<JobWithEmployer> = emptyList(),
    onJobAccepted: (Job) -> Unit,
    onJobRejected: (Job) -> Unit,
    onJobDetails: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (jobs.isEmpty()) {
        EmptyJobsPlaceholder(modifier)
        return
    }

    var currentIndex by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Show "No more jobs" when all cards are swiped
        if (currentIndex >= jobs.size) {
            NoMoreJobsCard(
                onResetClick = { currentIndex = 0 },
                modifier = Modifier.fillMaxWidth(0.85f)
            )
        } else {
            // Show a few cards stacked (the current one and the next ones)
            for (i in (currentIndex + 2 downTo currentIndex).reversed()) {
                if (i < jobs.size) {
                    val topCardIndex = currentIndex
                    val cardPosition = i - currentIndex
                    val job = jobs[i]

                    // Find the employer name for this job
                    val employerName = jobsWithEmployers.find { it.job.id == job.id }?.employerName ?: "Unknown Employer"

                    // Only make the top card swipeable
                    if (i == topCardIndex) {
                        SwipeableJobCard(
                            job = job,
                            employerName = employerName,
                            onSwipeLeft = {
                                onJobRejected(job)
                                currentIndex++
                            },
                            onSwipeRight = {
                                onJobAccepted(job)
                                currentIndex++
                            },
                            onJobDetails = { onJobDetails(job.id) },
                            onCardClick = { onJobDetails(job.id) }, // Added card click handler
                            modifier = Modifier
                                .fillMaxWidth(0.85f)
                                .offset(y = (-10 * cardPosition).dp)
                                .scale(1f - (0.05f * cardPosition))
                        )
                    } else {
                        // Non-swipeable background cards (just for visual stack effect)
                        StaticJobCard(
                            job = job,
                            employerName = employerName,
                            modifier = Modifier
                                .fillMaxWidth(0.85f)
                                .offset(y = (-10 * cardPosition).dp)
                                .scale(1f - (0.05f * cardPosition))
                        )
                    }
                }
            }

            // Action buttons below the cards
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(48.dp)
            ) {
                // Reject button
                FloatingActionButton(
                    onClick = {
                        if (currentIndex < jobs.size) {
                            scope.launch {
                                onJobRejected(jobs[currentIndex])
                                currentIndex++
                            }
                        }
                    },
                    containerColor = Color.White,
                    contentColor = Color.Red,
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Reject Job",
                        modifier = Modifier.size(30.dp)
                    )
                }

                // Accept button
                FloatingActionButton(
                    onClick = {
                        if (currentIndex < jobs.size) {
                            scope.launch {
                                onJobAccepted(jobs[currentIndex])
                                currentIndex++
                            }
                        }
                    },
                    containerColor = Color.White,
                    contentColor = PrimaryBlue,
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        Icons.Default.Favorite,
                        contentDescription = "Accept Job",
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun SwipeableJobCard(
    job: Job,
    employerName: String,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
    onJobDetails: () -> Unit,
    onCardClick: () -> Unit, // New parameter for card click
    modifier: Modifier = Modifier
) {
    val screenWidth = with(LocalDensity.current) {
        LocalConfiguration.current.screenWidthDp.dp.toPx()
    }

    // Value to track card position
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    // Track if the card is being swiped away
    var isSwiping by remember { mutableStateOf(false) }

    // Calculate rotation based on horizontal drag
    val rotation = (offsetX / screenWidth) * 15 // 15 degrees max rotation

    // Animation specs
    val animationSpec: AnimationSpec<Float> = tween(durationMillis = 300)

    // Animated values for smooth movement
    val animatedOffsetX by animateFloatAsState(
        targetValue = offsetX,
        animationSpec = animationSpec,
        finishedListener = {
            // If we've animated to a position far off screen, the swipe is complete
            if (abs(it) > screenWidth && !isSwiping) {
                if (it < 0) {
                    onSwipeLeft()
                } else {
                    onSwipeRight()
                }
            }
        }
    )

    val animatedOffsetY by animateFloatAsState(
        targetValue = offsetY,
        animationSpec = animationSpec
    )

    val animatedRotation by animateFloatAsState(
        targetValue = rotation,
        animationSpec = animationSpec
    )

    // Swipe threshold (20% of screen width - reduced from 30% to make it easier to trigger)
    val swipeThreshold = screenWidth * 0.2f

    // Determine which action overlay to show - threshold reduced for better feedback
    val showLeftSwipeOverlay = offsetX < -screenWidth * 0.05f
    val showRightSwipeOverlay = offsetX > screenWidth * 0.05f

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(400.dp)
            .graphicsLayer {
                translationX = animatedOffsetX
                translationY = animatedOffsetY
                rotationZ = animatedRotation
            }
            .clickable { onCardClick() } // Add clickable to the entire card
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = {
                        isSwiping = true
                    },
                    onDragEnd = {
                        isSwiping = false
                        if (offsetX < -swipeThreshold) {
                            // Swiped left far enough
                            offsetX = -screenWidth * 2
                            // onSwipeLeft will be called by the animation finished listener
                        } else if (offsetX > swipeThreshold) {
                            // Swiped right far enough
                            offsetX = screenWidth * 2
                            // onSwipeRight will be called by the animation finished listener
                        } else {
                            // Not swiped far enough, return to center
                            offsetX = 0f
                            offsetY = 0f
                        }
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        offsetX += dragAmount.x
                        offsetY += dragAmount.y
                    }
                )
            },
        elevation = CardDefaults.cardElevation(
            defaultElevation = 8.dp
        ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Job card content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Job title with more emphasis
                Text(
                    text = job.title.takeIf { it.isNotBlank() } ?: "Untitled Job",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Employer name with icon
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Work,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Posted by: $employerName",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Salary information with better styling
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = PrimaryBlue.copy(alpha = 0.1f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = job.salaryRange.takeIf { !it.isNullOrBlank() } ?: "Contact for salary details",
                        style = MaterialTheme.typography.titleMedium,
                        color = PrimaryBlue,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Only show location if district or state is available
                if (!job.district.isNullOrBlank() || !job.state.isNullOrBlank()) {
                    // Location with icon
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = Color.Gray,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = buildString {
                                if (!job.district.isNullOrBlank()) append(job.district)
                                if (!job.district.isNullOrBlank() && !job.state.isNullOrBlank()) append(", ")
                                if (!job.state.isNullOrBlank()) append(job.state)
                            }.ifBlank { "Location not specified" },
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Job description with better styling
                if (!job.description.isNullOrBlank()) {
                    Text(
                        text = "Description",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = job.description,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = "Tap card to see full job details",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // Bottom hint
                Text(
                    text = "Tap for more details",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }

            // Swipe action overlays with enhanced visibility
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .padding(32.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (showRightSwipeOverlay) {
                    Card(
                        modifier = Modifier
                            .rotate(-30f)
                            .scale(0.9f + (offsetX.absoluteValue / screenWidth) * 0.3f),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0x9900AA00)
                        )
                    ) {
                        Text(
                            text = "APPLY",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .matchParentSize()
                    .padding(32.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                if (showLeftSwipeOverlay) {
                    Card(
                        modifier = Modifier
                            .rotate(30f)
                            .scale(0.9f + (offsetX.absoluteValue / screenWidth) * 0.3f),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0x99FF0000)
                        )
                    ) {
                        Text(
                            text = "REJECT",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StaticJobCard(
    job: Job,
    employerName: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(400.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 4.dp
        ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        // Same content as SwipeableJobCard but without the swipe functionality
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                text = job.title.takeIf { it.isNotBlank() } ?: "Untitled Job",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Posted by: $employerName",
                style = MaterialTheme.typography.bodyMedium
            )

            // We don't need to show the complete details for background cards
        }
    }
}

@Composable
fun EmptyJobsPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .height(400.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "No Jobs Available",
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "We couldn't find any jobs matching your preferences. Please check back later or adjust your search criteria.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun NoMoreJobsCard(
    onResetClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(400.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "You've seen all jobs!",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Check back later for new opportunities",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(onClick = onResetClick) {
                Text("Look Again")
            }
        }
    }
}