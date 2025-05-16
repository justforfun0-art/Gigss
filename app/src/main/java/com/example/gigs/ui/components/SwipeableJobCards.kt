package com.example.gigs.ui.components

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import androidx.wear.compose.material.ExperimentalWearMaterialApi
import androidx.wear.compose.material.FractionalThreshold
import androidx.wear.compose.material.rememberSwipeableState
import androidx.wear.compose.material.swipeable
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.example.gigs.R
import com.example.gigs.data.model.Job
import com.example.gigs.data.model.JobWithEmployer
import com.example.gigs.ui.theme.PrimaryBlue
import com.example.gigs.viewmodel.JobViewModel
import com.example.gigs.viewmodel.ProcessedJobsViewModel
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.gigs.viewmodel.JobHistoryViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

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
    modifier: Modifier = Modifier,
    jobViewModel: JobViewModel = hiltViewModel(),
    processedJobsViewModel: ProcessedJobsViewModel = hiltViewModel(),
    jobHistoryViewModel: JobHistoryViewModel? = hiltViewModel(),
) {
    // ===== STATE VARIABLES (all at top level) =====
    val sessionProcessedJobIds by processedJobsViewModel.sessionProcessedJobIds.collectAsState()
    val processedJobIds by processedJobsViewModel.processedJobIds.collectAsState()
    val hasProcessedJobs = processedJobIds.isNotEmpty()
    val isShowingRejectedJobs by processedJobsViewModel.isShowingRejectedJobs.collectAsState()

    // Create a mutable state list of unique jobs by ID, filtered to remove already processed jobs
    val displayJobsList = remember(jobs, sessionProcessedJobIds) {
        // Filter out any jobs that have already been processed in this session
        val uniqueJobs = jobs
            .filter { job -> !sessionProcessedJobIds.contains(job.id) }
            .distinctBy { it.id }

        Log.d("SwipeableJobCards", "Initializing with ${uniqueJobs.size} unique jobs from ${jobs.size} total jobs")
        Log.d("SwipeableJobCards", "Excluding ${sessionProcessedJobIds.size} already processed jobs")

        mutableStateListOf<Job>().apply { addAll(uniqueJobs) }
    }

    // Current index as a mutable state
    var currentIndex by remember { mutableIntStateOf(0) }
    var selectedJob by remember { mutableStateOf<Job?>(null) }
    var showAcceptConfetti by remember { mutableStateOf(false) }

    // Track jobs to be removed from display list
    var jobToProcess by remember { mutableStateOf<Job?>(null) }
    var jobProcessAction by remember { mutableStateOf("") } // "accept" or "reject"

    // Track whether to show confetti in dialog
    var showDialogConfetti by remember { mutableStateOf(false) }
    var shouldCloseDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current

    // ===== HELPER FUNCTIONS =====
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

        if (displayJobsList.isEmpty() || currentIndex >= displayJobsList.size) {
            Log.e("SwipeableJobCards", "Invalid state: empty list or index out of bounds")
            return
        }

        // Check if job is already processed in this session
        if (sessionProcessedJobIds.contains(job.id)) {
            Log.d("SwipeableJobCards", "Job ${job.id} already processed in this session, skipping rejection")
            return
        }

        // IMMEDIATELY remove from display list
        val index = displayJobsList.indexOf(job)
        if (index >= 0) {
            Log.d("SwipeableJobCards", "Immediately removing job ${job.id} at index $index from display list")
            displayJobsList.removeAt(index)

            // Adjust current index if needed
            if (index <= currentIndex && currentIndex > 0) {
                currentIndex--
            }

            // Make sure currentIndex stays valid
            if (currentIndex >= displayJobsList.size && displayJobsList.isNotEmpty()) {
                currentIndex = displayJobsList.size - 1
            }
        } else {
            Log.e("SwipeableJobCards", "Could not find job ${job.id} in display list to remove")
        }

        // Mark job as rejected in the repository
        processedJobsViewModel.markJobAsRejected(job.id)

        // Notify the backend
        jobViewModel.markJobAsNotInterested(job.id)

        // UI feedback
        onJobRejected(job)
        triggerVibration()

        // Set job for additional processing by LaunchedEffect if needed
        jobToProcess = job
        jobProcessAction = "reject"

        // Refresh job history
        jobHistoryViewModel?.refreshApplicationHistory()
    }

    // Function to handle swipe right (accept)
    fun handleSwipeRight(job: Job) {
        Log.d("SwipeableJobCards", "Handling swipe right for job: ${job.id}")

        if (displayJobsList.isEmpty() || currentIndex >= displayJobsList.size) {
            Log.e("SwipeableJobCards", "Invalid state: empty list or index out of bounds")
            return
        }

        // Check if job is already processed in this session
        if (processedJobsViewModel.isJobProcessedInCurrentSession(job.id)) {
            Log.d("SwipeableJobCards", "Job ${job.id} already processed in this session, skipping acceptance")
            return
        }

        // IMMEDIATELY remove from display list
        val index = displayJobsList.indexOf(job)
        if (index >= 0) {
            Log.d("SwipeableJobCards", "Immediately removing job ${job.id} at index $index from display list")
            displayJobsList.removeAt(index)

            // Adjust current index if needed
            if (index <= currentIndex && currentIndex > 0) {
                currentIndex--
            }

            // Make sure currentIndex stays valid
            if (currentIndex >= displayJobsList.size && displayJobsList.isNotEmpty()) {
                currentIndex = displayJobsList.size - 1
            }
        } else {
            Log.e("SwipeableJobCards", "Could not find job ${job.id} in display list to remove")
        }

        // Mark job as applied in the repository
        processedJobsViewModel.markJobAsApplied(job.id)

        // Also add to session processed jobs to ensure it doesn't show up again this session
        // Also add to session processed jobs to ensure it doesn't show up again this session
        processedJobsViewModel.addToSessionProcessedJobs(job.id)
        // Call the backend API
        jobViewModel.applyForJob(job.id)

        // UI feedback
        showAcceptConfetti = true
        onJobAccepted(job)
        triggerVibration()

        // Set job for additional processing by LaunchedEffect if needed
        jobToProcess = job
        jobProcessAction = "accept"

        // Refresh job history
        jobHistoryViewModel?.refreshApplicationHistory()

        // Debug logs to help track what's happening
        Log.d("SwipeableJobCards", "After swipe right - Display jobs count: ${displayJobsList.size}")
        Log.d("SwipeableJobCards", "After swipe right - Current index: $currentIndex")
        if (currentIndex < displayJobsList.size) {
            Log.d("SwipeableJobCards", "After swipe right - Next job ID: ${displayJobsList[currentIndex].id}")
        } else {
            Log.d("SwipeableJobCards", "After swipe right - No more jobs to display")
        }
        Log.d("SwipeableJobCards", "After swipe right - Session processed job count: ${processedJobsViewModel.sessionProcessedJobIds.value.size}")
    }

    // ===== SIDE EFFECTS =====
    LaunchedEffect(jobViewModel.featuredJobs.collectAsState().value) {
        val featuredJobs = jobViewModel.featuredJobs.value
        if (featuredJobs.isNotEmpty()) {
            Log.d("SwipeableJobCards", "Jobs updated from ViewModel. Refreshing display list with ${featuredJobs.size} jobs")
            displayJobsList.clear()

            val filteredJobs = featuredJobs.filter { job ->
                !sessionProcessedJobIds.contains(job.id)
            }.distinctBy { it.id }

            displayJobsList.addAll(filteredJobs)

            if (displayJobsList.isNotEmpty()) {
                currentIndex = 0
            }
        }
    }

    // Debug logs to help track what's happening
    LaunchedEffect(displayJobsList.size, currentIndex) {
        Log.d("SwipeableJobCards", "Display jobs count: ${displayJobsList.size}, Current index: $currentIndex")
        Log.d("SwipeableJobCards", "Processed job IDs: $processedJobIds")
    }

    // Effect to preserve state during navigation
    DisposableEffect(Unit) {
        // This runs on component creation
        Log.d("SwipeableJobCards", "Component initialized with ${displayJobsList.size} jobs")
        Log.d("SwipeableJobCards", "Starting with ${processedJobIds.size} processed jobs")

        onDispose {
            // This code runs when the composable is removed from composition (navigation away)
            Log.d("SwipeableJobCards", "Component is being disposed but preserving state of ${processedJobIds.size} processed jobs")
        }
    }

    // Effect to handle job processing after swipe events
    LaunchedEffect(jobToProcess, jobProcessAction) {
        val job = jobToProcess ?: return@LaunchedEffect

        // Wait a bit to allow animation and API calls to complete
        delay(300)

        try {
            // We don't need to remove the job from the display list here anymore
            // since we now do it immediately in the handleSwipe methods

            // However, we can still use this to handle any additional processing
            // or to recover from any state inconsistencies
            val index = displayJobsList.indexOfFirst { it.id == job.id }
            if (index >= 0) {
                Log.d("SwipeableJobCards", "Job ${job.id} is still in display list after ${jobProcessAction}. Removing it now.")
                displayJobsList.removeAt(index)

                // Adjust the current index if needed
                if (index <= currentIndex && currentIndex > 0) {
                    currentIndex--
                }

                // Make sure currentIndex is valid
                if (currentIndex >= displayJobsList.size && displayJobsList.isNotEmpty()) {
                    currentIndex = displayJobsList.size - 1
                }
            }
        } catch (e: Exception) {
            Log.e("SwipeableJobCards", "Error in post-processing for job ${job.id}: ${e.message}")
        }

        // Clear the job to process to prevent further processing
        jobToProcess = null
        jobProcessAction = ""
    }

    // Effect to handle dialog closing after confetti animation
    LaunchedEffect(showDialogConfetti, shouldCloseDialog) {
        if (showDialogConfetti && shouldCloseDialog) {
            delay(1500) // Wait for confetti animation
            selectedJob = null
            shouldCloseDialog = false
            showDialogConfetti = false
        }
    }

    // Reset confetti animation after it plays
    LaunchedEffect(showAcceptConfetti) {
        if (showAcceptConfetti) {
            delay(1500) // Match confetti duration
            showAcceptConfetti = false
        }
    }

    // ===== MAIN UI LAYOUT =====
    // SINGLE BOX FOR ALL UI CONTENT
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Top banner indicator for rejected jobs mode
        AnimatedVisibility(
            visible = isShowingRejectedJobs,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFFFA000))
                    .padding(vertical = 6.dp)
                    .align(Alignment.TopCenter)
                    .zIndex(20f)
            ) {
                Text(
                    text = "Reconsidering previously rejected jobs",
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // CONDITIONAL CONTENT BASED ON STATE
        when {
            // No jobs at all - show empty placeholder
            jobs.isEmpty() && !hasProcessedJobs -> {
                EmptyJobsPlaceholder(modifier = Modifier.fillMaxWidth(0.85f))
            }

            // No more jobs to display - show the "look again" card
            displayJobsList.isEmpty() || currentIndex >= displayJobsList.size -> {
                NoMoreJobsCard(
                    onResetClick = {
                        // Reset the display list with ALL jobs
                        displayJobsList.clear()
                        Log.d("SwipeableJobCards", "Total jobs available to show: ${jobs.size}")

                        // Filter out session processed jobs when adding
                        val filteredJobs = jobs.filter { job ->
                            !sessionProcessedJobIds.contains(job.id)
                        }.distinctBy { it.id }

                        displayJobsList.addAll(filteredJobs)

                        Log.d("SwipeableJobCards", "Reset display jobs. New count: ${displayJobsList.size} jobs")

                        if (displayJobsList.isNotEmpty()) {
                            currentIndex = 0
                        }
                    },
                    jobViewModel = jobViewModel,
                    processedJobsViewModel = processedJobsViewModel,
                    modifier = Modifier.fillMaxWidth(0.85f)
                )
            }

            // Have jobs to display - show cards and buttons
            else -> {
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

                // Ensure current index is valid
                val validCurrentIndex = currentIndex.coerceIn(0, displayJobsList.size - 1)
                if (validCurrentIndex != currentIndex) {
                    currentIndex = validCurrentIndex
                }

                // Get the top card (current index) and next card if available
                val topCardJob = if (currentIndex < displayJobsList.size) displayJobsList[currentIndex] else null
                val nextCardJob = if (currentIndex + 1 < displayJobsList.size) displayJobsList[currentIndex + 1] else null

                // Check if the topCardJob is already processed (should not happen with our new filtering)
                LaunchedEffect(topCardJob) {
                    if (topCardJob != null &&
                        sessionProcessedJobIds.contains(topCardJob.id) &&
                        !isShowingRejectedJobs)
                    {
                        Log.e("SwipeableJobCards", "Top card job ${topCardJob.id} is already processed! This should not happen.")
                        // Remove this job and update the index
                        val index = displayJobsList.indexOf(topCardJob)
                        if (index >= 0) {
                            displayJobsList.removeAt(index)
                            if (displayJobsList.isEmpty()) {
                                return@LaunchedEffect
                            }
                            if (currentIndex >= displayJobsList.size) {
                                currentIndex = displayJobsList.size - 1
                            }
                        }
                    }
                }

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
                    if (topCardJob != null) {
                        FloatingActionButton(
                            onClick = {
                                topCardJob.let { job ->
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
                                topCardJob.let { job ->
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
        }
    }

    // Full screen job details dialog (outside the Box)
    AnimatedVisibility(
        visible = selectedJob != null,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut()
    ) {
        selectedJob?.let { job ->
            FullScreenJobDetailsDialog(
                job = job,
                employerName = jobsWithEmployers.find { it.job.id == job.id }?.employerName ?: "Unknown Employer",
                onClose = {
                    selectedJob = null
                },
                onApply = {
                    // Mark job as applied in the repository
                    processedJobsViewModel.markJobAsApplied(job.id)

                    // Apply for the job using the ViewModel
                    jobViewModel.applyForJob(job.id)

                    // Show confetti and queue dialog closing
                    showDialogConfetti = true
                    shouldCloseDialog = true

                    // Update UI
                    showAcceptConfetti = true
                    onJobAccepted(job)

                    // Set job to be processed by the LaunchedEffect
                    jobToProcess = job
                    jobProcessAction = "accept"
                },
                onReject = {
                    // Mark job as rejected in the repository
                    processedJobsViewModel.markJobAsRejected(job.id)

                    // Mark the job as not interested using the ViewModel
                    jobViewModel.markJobAsNotInterested(job.id)

                    // Update UI
                    onJobRejected(job)
                    selectedJob = null

                    // Set job to be processed by the LaunchedEffect
                    jobToProcess = job
                    jobProcessAction = "reject"
                },
                triggerVibration = { triggerVibration() }
            )
        }
    }
}

@Composable
fun NoMoreJobsCard(
    onResetClick: () -> Unit,
    modifier: Modifier = Modifier,
    jobViewModel: JobViewModel = hiltViewModel(),
    processedJobsViewModel: ProcessedJobsViewModel = hiltViewModel(),
    jobHistoryViewModel: JobHistoryViewModel? = hiltViewModel()
) {
    val isShowingRejectedJobs by processedJobsViewModel.isShowingRejectedJobs.collectAsState()

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (isShowingRejectedJobs)
                    "No more rejected jobs to reconsider"
                else
                    "You've seen all available jobs",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (isShowingRejectedJobs)
                    "You've reviewed all your previously rejected jobs"
                else
                    "Check back later for new job opportunities",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (isShowingRejectedJobs) {
                // SHOWING REJECTED JOBS - Show button to go back to regular job search
                Button(
                    onClick = {
                        Log.d("SwipeableJobCards", "Back to Job Search clicked.")

                        // Set the showing rejected jobs flag to false in the repository
                        processedJobsViewModel.setShowingRejectedJobs(false)

                        // Get new unprocessed jobs
                        val district = jobViewModel.employeeProfile.value?.district ?: ""
                        jobViewModel.getJobsByDistrict(district)

                        // Reset the card display
                        onResetClick()

                        // Refresh job history
                        jobHistoryViewModel?.refreshApplicationHistory()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryBlue
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Back to Job Search")
                }
            } else {
                // NORMAL MODE - Only show reconsider rejected jobs option
                Button(
                    onClick = {
                        Log.d("SwipeableJobCards", "Reconsider Rejected Jobs clicked.")

                        // Set the showing rejected jobs flag to true in the repository
                        // This will automatically clear session processed jobs in the repository
                        processedJobsViewModel.setShowingRejectedJobs(true)

                        // Get rejected jobs
                        val district = jobViewModel.employeeProfile.value?.district ?: ""
                        jobViewModel.getOnlyRejectedJobs(district)

                        // Reset the card display
                        onResetClick()

                        // Refresh job history
                        jobHistoryViewModel?.refreshApplicationHistory()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryBlue
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Reconsider Rejected Jobs")
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
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "We couldn't find any jobs matching your location. Please check back later or try a different location.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
            }
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
    // Create anchors for left, center, and right positions
    val screenWidth = with(LocalDensity.current) {
        LocalConfiguration.current.screenWidthDp.dp.toPx()
    }

    // Define our swipe state with CENTER as initial value
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
                // Reset to center to prevent double-triggers
                swipeableState.snapTo(SwipeDirection.CENTER)
            }
            SwipeDirection.RIGHT -> {
                Log.d("SwipeableJobCard", "RIGHT swipe completed for job: ${job.id}")
                onSwipeRight()
                // Reset to center to prevent double-triggers
                swipeableState.snapTo(SwipeDirection.CENTER)
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