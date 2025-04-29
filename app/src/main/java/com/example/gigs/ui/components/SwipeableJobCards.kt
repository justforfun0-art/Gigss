package com.example.gigs.ui.components

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.example.gigs.R
import com.example.gigs.data.model.Job
import com.example.gigs.data.model.JobWithEmployer
import com.example.gigs.ui.theme.PrimaryBlue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.content.Context
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.platform.LocalContext
import androidx.wear.compose.material.ExperimentalWearMaterialApi
import androidx.wear.compose.material.FractionalThreshold
import androidx.wear.compose.material.rememberSwipeableState
import androidx.wear.compose.material.swipeable

// Define swipe directions as an enum
enum class SwipeDirection {
    LEFT, CENTER, RIGHT
}

/**
 * Main component that displays swipeable job cards
 */
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

    // Create a mutable copy of the jobs list for UI operations
    // This is crucial - we need a separate list that won't be affected by external filtering
    val displayJobs = remember(jobs) {
        jobs.toMutableStateList()
    }

    var currentIndex by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    var selectedJob by remember { mutableStateOf<Job?>(null) }
    var showAcceptConfetti by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Debug logs to help track what's happening
    LaunchedEffect(displayJobs.size, currentIndex) {
        Log.d("SwipeableJobCards", "Display jobs count: ${displayJobs.size}, Current index: $currentIndex")
    }

    // Setup vibration feedback
    val vibrator = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    fun triggerVibration() {
        if (vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(100)
            }
        }
    }

    // Function to handle swipe left (reject)
    fun handleSwipeLeft(job: Job) {
        Log.d("SwipeableJobCards", "Handling swipe left for job: ${job.id}")

        onJobRejected(job)
        triggerVibration()

        scope.launch {
            delay(300)

            if (currentIndex < displayJobs.size && displayJobs[currentIndex].id == job.id) {
                displayJobs.removeAt(currentIndex)

                // ✅ If index is now beyond list, reset it
                if (currentIndex >= displayJobs.size) {
                    currentIndex = 0
                }
            }
        }
    }


    // Function to handle swipe right (accept)
    fun handleSwipeRight(job: Job) {
        Log.d("SwipeableJobCards", "Handling swipe right for job: ${job.id}")

        showAcceptConfetti = true
        onJobAccepted(job)
        triggerVibration()

        scope.launch {
            delay(300)

            if (currentIndex < displayJobs.size && displayJobs[currentIndex].id == job.id) {
                displayJobs.removeAt(currentIndex)

                // ✅ If index is now beyond list, reset it
                if (currentIndex >= displayJobs.size) {
                    currentIndex = 0
                }
            }
        }
    }


    // Reset confetti animation after it plays
    LaunchedEffect(showAcceptConfetti) {
        if (showAcceptConfetti) {
            delay(1500)
            showAcceptConfetti = false
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if (displayJobs.isEmpty()) {
            // No more jobs to show
            NoMoreJobsCard(
                onResetClick = {
                    // Reset by recreating the display jobs list
                    displayJobs.clear()
                    displayJobs.addAll(jobs)
                    currentIndex = 0
                    Log.d("SwipeableJobCards", "Reset display jobs. New count: ${displayJobs.size}")
                },
                modifier = Modifier.fillMaxWidth(0.85f)
            )
        } else {
            // Show confetti animation if accepting a job
            if (showAcceptConfetti) {
                val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.confetti))
                val progress by animateLottieCompositionAsState(
                    composition = composition,
                    iterations = 1
                )

                LottieAnimation(
                    composition = composition,
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(30f) // Ensure confetti is on top
                )
            }

            // Get the top card (current index) and next card if available
            val topCardJob = if (currentIndex < displayJobs.size) displayJobs[currentIndex] else null
            val nextCardJob = if (currentIndex + 1 < displayJobs.size) displayJobs[currentIndex + 1] else null

            // Draw the next card (background) first if available
            nextCardJob?.let { nextJob ->
                val employerName = jobsWithEmployers.find { it.job.id == nextJob.id }?.employerName ?: "Unknown Employer"

                key(nextJob.id) {
                    StaticJobCard(
                        job = nextJob,
                        employerName = employerName,
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .offset(y = (-10).dp)
                            .scale(0.95f)
                            .zIndex(1f) // Background card
                    )
                }
            }

            // Draw the top card last (foreground)
            topCardJob?.let { topJob ->
                val employerName = jobsWithEmployers.find { it.job.id == topJob.id }?.employerName ?: "Unknown Employer"

                key(topJob.id) {
                    Log.d("SwipeableJobCards", "Rendering top card for job: ${topJob.id}")
                    SwipeableJobCard(
                        job = topJob,
                        employerName = employerName,
                        onSwipeLeft = { handleSwipeLeft(topJob) },
                        onSwipeRight = { handleSwipeRight(topJob) },
                        onCardClick = {
                            Log.d("SwipeableJobCards", "Card click detected for job: ${topJob.id}")
                            selectedJob = topJob
                        },
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .zIndex(10f) // Top card gets highest z-index
                    )
                }
            }

            // Action buttons
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .zIndex(20f), // Buttons should be on top of everything
                horizontalArrangement = Arrangement.spacedBy(48.dp)
            ) {
                // Only show buttons if there are jobs to interact with
                FloatingActionButton(
                    onClick = {
                        topCardJob?.let { job ->
                            Log.d("SwipeableJobCards", "Reject button clicked for job: ${job.id}")
                            handleSwipeLeft(job)
                        }
                    },
                    containerColor = Color.White,
                    contentColor = Color.Red,
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(Icons.Default.Close, "Reject", Modifier.size(30.dp))
                }

                FloatingActionButton(
                    onClick = {
                        topCardJob?.let { job ->
                            Log.d("SwipeableJobCards", "Accept button clicked for job: ${job.id}")
                            handleSwipeRight(job)
                        }
                    },
                    containerColor = Color.White,
                    contentColor = PrimaryBlue,
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(Icons.Default.Favorite, "Accept", Modifier.size(30.dp))
                }
            }
        }
    }

    // Full screen job details dialog
    AnimatedVisibility(
        visible = selectedJob != null,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut()
    ) {
        selectedJob?.let { job ->
            FullScreenJobDetailsDialog(
                job = job,
                employerName = jobsWithEmployers.find { it.job.id == job.id }?.employerName ?: "Unknown Employer",
                onClose = { selectedJob = null },
                onApply = {
                    // Update database
                    onJobAccepted(job)
                    showAcceptConfetti = true

                    scope.launch {
                        // Wait for confetti animation
                        delay(1500)
                        selectedJob = null

                        // Remove this job from the display list if it matches current index
                        if (currentIndex < displayJobs.size && displayJobs[currentIndex].id == job.id) {
                            displayJobs.removeAt(currentIndex)
                        } else {
                            // If it's not the current index job, find and remove it
                            val indexToRemove = displayJobs.indexOfFirst { it.id == job.id }
                            if (indexToRemove >= 0) {
                                displayJobs.removeAt(indexToRemove)
                                // Adjust currentIndex if necessary
                                if (indexToRemove < currentIndex) {
                                    currentIndex--
                                }
                            }
                        }
                    }
                },
                onReject = {
                    // Update database
                    onJobRejected(job)
                    selectedJob = null

                    // Remove this job from the display list if it matches current index
                    if (currentIndex < displayJobs.size && displayJobs[currentIndex].id == job.id) {
                        displayJobs.removeAt(currentIndex)
                    } else {
                        // If it's not the current index job, find and remove it
                        val indexToRemove = displayJobs.indexOfFirst { it.id == job.id }
                        if (indexToRemove >= 0) {
                            displayJobs.removeAt(indexToRemove)
                            // Adjust currentIndex if necessary
                            if (indexToRemove < currentIndex) {
                                currentIndex--
                            }
                        }
                    }
                },
                triggerVibration = { triggerVibration() }
            )
        }
    }
}

@OptIn(ExperimentalWearMaterialApi::class)
@Composable
fun SwipeableJobCard(
    job: Job,
    employerName: String,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
    onCardClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()

    // Create anchors for left, center, and right positions
    val screenWidth = with(LocalDensity.current) {
        LocalConfiguration.current.screenWidthDp.dp.toPx()
    }

    // Define our swipe state with CENTER as initial value
    val swipeableState = rememberSwipeableState(initialValue = SwipeDirection.CENTER)

    // Map swipe directions to anchor points
    val anchors = mapOf(
        -screenWidth to SwipeDirection.LEFT,  // Swipe left (reject)
        0f to SwipeDirection.CENTER,          // Initial position
        screenWidth to SwipeDirection.RIGHT   // Swipe right (accept)
    )

    // Calculate rotation based on offset
    val rotation = (swipeableState.offset.value / screenWidth) * 15f

    // Determine when to show swipe overlays
    val showLeftSwipeOverlay = swipeableState.offset.value < -screenWidth * 0.05f
    val showRightSwipeOverlay = swipeableState.offset.value > screenWidth * 0.05f

    // Handle swipe completion
    LaunchedEffect(swipeableState.currentValue) {
        when (swipeableState.currentValue) {
            SwipeDirection.LEFT -> {
                Log.d("SwipeableJobCard", "LEFT swipe completed for job: ${job.id}")
                onSwipeLeft()
            }
            SwipeDirection.RIGHT -> {
                Log.d("SwipeableJobCard", "RIGHT swipe completed for job: ${job.id}")
                onSwipeRight()
            }
            else -> { /* Do nothing if we're in the center */ }
        }
    }

    // Reset card position when job changes
    LaunchedEffect(job.id) {
        Log.d("SwipeableJobCard", "New job card: ${job.id}, resetting position")
        if (swipeableState.currentValue != SwipeDirection.CENTER) {
            swipeableState.snapTo(SwipeDirection.CENTER)
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(400.dp)
            .offset { IntOffset(swipeableState.offset.value.roundToInt(), 0) }
            .rotate(rotation)
            .swipeable(
                state = swipeableState,
                anchors = anchors,
                thresholds = { _, _ -> FractionalThreshold(0.3f) }, // Require 30% of the way to swap
                orientation = Orientation.Horizontal,
                resistance = null // No resistance for smooth swiping
            )
            .padding(4.dp), // Add padding to prevent cutting off during rotation
        elevation = CardDefaults.cardElevation(8.dp),
        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface),
        onClick = {
            // Only trigger click if we're close to center position
            if (abs(swipeableState.offset.value) < screenWidth * 0.1f) {
                Log.d("SwipeableJobCard", "Card clicked: ${job.id}")
                onCardClick()
            }
        }
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
                            .scale(0.9f + (abs(swipeableState.offset.value) / screenWidth) * 0.3f),
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
                            .scale(0.9f + (abs(swipeableState.offset.value) / screenWidth) * 0.3f),
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
fun FullScreenJobDetailsDialog(
    job: Job,
    employerName: String,
    onClose: () -> Unit,
    onApply: () -> Unit,
    onReject: () -> Unit,
    triggerVibration: () -> Unit
) {
    var showConfetti by remember { mutableStateOf(false) }

    LaunchedEffect(showConfetti) {
        if (showConfetti) {
            delay(2000) // Match confetti duration
            showConfetti = false
        }
    }

    Dialog(onDismissRequest = onClose) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Confetti animation overlay
                if (showConfetti) {
                    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.confetti))
                    val progress by animateLottieCompositionAsState(
                        composition = composition,
                        iterations = 1
                    )

                    LottieAnimation(
                        composition = composition,
                        progress = { progress },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    // Close button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        IconButton(onClick = onClose) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Close",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Job title
                    Text(
                        text = job.title,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Employer info
                    Text(
                        text = "Posted by: $employerName",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Salary info
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
                            modifier = Modifier.padding(12.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Location info if available
                    if (!job.district.isNullOrBlank() || !job.state.isNullOrBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = Color.Gray,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = buildString {
                                    if (!job.district.isNullOrBlank()) append(job.district)
                                    if (!job.district.isNullOrBlank() && !job.state.isNullOrBlank()) append(", ")
                                    if (!job.state.isNullOrBlank()) append(job.state)
                                }.ifBlank { "Location not specified" },
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // Job description
                    Text(
                        text = "Description",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = job.description ?: "No description available",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Additional job details could go here

                    Spacer(modifier = Modifier.weight(1f))

                    // Action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Button(
                            onClick = {
                                triggerVibration()
                                onReject()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.Red.copy(alpha = 0.8f)
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Reject")
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Button(
                            onClick = {
                                showConfetti = true
                                triggerVibration()
                                onApply()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PrimaryBlue
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Apply")
                        }
                    }
                }
            }
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
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "We couldn't find any jobs matching your preferences. Please check back later or adjust your search criteria.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
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
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Check back later for new opportunities",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(onClick = onResetClick) {
                Text("Look Again")
            }
        }
    }
}