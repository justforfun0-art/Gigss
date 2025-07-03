package com.example.gigs.ui.components

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NotificationAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Work
import androidx.compose.material.icons.filled.WorkOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.example.gigs.data.util.ComposeOptimizationUtils
import com.example.gigs.data.util.PerformanceUtils
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.runtime.Stable
import com.example.gigs.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeoutOrNull

// Define swipe directions as an enum
enum class SwipeDirection {
    LEFT, CENTER, RIGHT
}

/**
 * OPTIMIZED Main component that displays swipeable job cards
 * - Fixed rejected jobs index handling
 * - Improved card centering and layout
 * - Enhanced performance and error handling
 * - Better visual styling and UX
 * - 🚀 NEW: Integrated reconsideration system
 */
@Composable
fun SwipeableJobCards(
    jobs: List<Job>,
    jobsWithEmployers: List<JobWithEmployer> = emptyList(),
    onJobAccepted: (Job) -> Unit,
    onJobRejected: (Job) -> Unit,
    onJobDetails: (String) -> Unit,
    modifier: Modifier = Modifier,
    isSwipeEnabled: Boolean = true,  // ADD THIS PARAMETER
    jobViewModel: JobViewModel = hiltViewModel(),
    processedJobsViewModel: ProcessedJobsViewModel = hiltViewModel(),
    jobHistoryViewModel: JobHistoryViewModel? = hiltViewModel(),
) {
    // 🚀 PERFORMANCE: Track recompositions only in debug
    if (BuildConfig.DEBUG) {
        ComposeOptimizationUtils.RecompositionCounter("SwipeableJobCards")
    }

    // 🚀 CRITICAL FIX: Stable job list to prevent cascading recompositions
    val stableJobs = remember(jobs.size, jobs.firstOrNull()?.id) {
        jobs.take(20) // Limit to prevent memory issues
    }

    // 🚀 OPTIMIZED: Efficient state collection with lifecycle awareness
    val sessionProcessedJobIds by processedJobsViewModel.sessionProcessedJobIds.collectAsState()
    val processedJobIds by processedJobsViewModel.processedJobIds.collectAsState()
    val isShowingRejectedJobs by processedJobsViewModel.isShowingRejectedJobs.collectAsState()
    val featuredJobs by jobViewModel.featuredJobs.collectAsState()

    // 🚀 NEW: Add reconsideration state tracking
    val reconsideredJobIds by processedJobsViewModel.reconsideredJobIds.collectAsState()

    // 🚀 OPTIMIZED: Derive UI state efficiently with reconsideration data
    val uiState by remember(sessionProcessedJobIds, processedJobIds, isShowingRejectedJobs, featuredJobs, reconsideredJobIds) {
        derivedStateOf {
            SwipeableJobsUiState(
                sessionProcessedJobIds = sessionProcessedJobIds,
                processedJobIds = processedJobIds,
                isShowingRejectedJobs = isShowingRejectedJobs,
                featuredJobs = featuredJobs,
                reconsideredJobIds = reconsideredJobIds // 🚀 NEW: Include reconsideration state
            )
        }
    }

    // 🚀 OPTIMIZED: Stable callbacks with error boundary
    val stableCallbacks = remember(onJobAccepted, onJobRejected, onJobDetails) {
        SwipeJobCallbacks(
            onAccepted = onJobAccepted,
            onRejected = onJobRejected,
            onDetails = onJobDetails,
            onError = { error -> Log.e("SwipeableJobCards", "Job action error: $error") }
        )
    }

    // 🚀 CRITICAL FIX: Better processing jobs management with automatic cleanup
    val processingJobs = remember { mutableSetOf<String>() }

    // 🚀 CRITICAL FIX: Track processed jobs in current session to prevent reappearing
    val sessionProcessedJobs = remember { mutableSetOf<String>() }

    // 🚀 ENHANCED: Shorter throttle interval for more responsive UI
    val throttledAction = ComposeOptimizationUtils.rememberThrottledAction(500L)

    // 🚀 ENHANCED: Filtering logic with reconsideration system integration
    val displayJobs by remember(stableJobs, uiState, sessionProcessedJobs.size, reconsideredJobIds.size) {
        derivedStateOf {
            PerformanceUtils.PerformanceMetrics.measureOperation("filter_jobs_v4", "ui") {
                if (stableJobs.isEmpty()) return@measureOperation emptyList<Job>()

                val filtered = if (uiState.isShowingRejectedJobs) {
                    // 🚀 ENHANCED: For rejected jobs mode, filter by session processed jobs AND reconsidered jobs
                    stableJobs
                        .asSequence()
                        .filter { job ->
                            !sessionProcessedJobs.contains(job.id) &&
                                    !reconsideredJobIds.contains(job.id) // 🚀 KEY ADDITION: Exclude reconsidered jobs
                        }
                        .distinctBy { it.id }
                        .take(20)
                        .toList()
                } else {
                    // 🚀 REGULAR: For regular mode, filter by session processed jobs only
                    stableJobs
                        .asSequence()
                        .filter { job -> !uiState.sessionProcessedJobIds.contains(job.id) }
                        .distinctBy { it.id }
                        .take(15)
                        .toList()
                }

                // 🚀 ENHANCED LOGGING: Include reconsideration stats
                Log.d("SwipeableJobCards", "🚀 FILTER: Mode=${if (uiState.isShowingRejectedJobs) "REJECTED" else "REGULAR"}, Input=${stableJobs.size}, Output=${filtered.size}, SessionProcessed=${sessionProcessedJobs.size}, Reconsidered=${reconsideredJobIds.size}")
                filtered
            }
        }
    }

    // 🚀 NEW: Helper function for getting eligible reconsideration count
    fun getEligibleReconsiderationCount(): Int {
        return jobViewModel.getEligibleReconsiderationCount()
    }

    // 🚀 OPTIONAL: Additional helper function for debugging reconsideration state
    fun logReconsiderationState() {
        if (BuildConfig.DEBUG) {
            Log.d("SwipeableJobCards", """
                🔍 RECONSIDERATION STATE:
                • Total Reconsidered Jobs: ${reconsideredJobIds.size}
                • Reconsidered IDs: ${reconsideredJobIds.take(5)}${if (reconsideredJobIds.size > 5) "..." else ""}
                • Session Processed: ${sessionProcessedJobs.size}
                • Available Jobs in Rejected Mode: ${if (uiState.isShowingRejectedJobs) displayJobs.size else "N/A"}
                • Eligible for Reconsideration: ${getEligibleReconsiderationCount()}
            """.trimIndent())
        }
    }

    // 🚀 OPTIMIZED: Employer map with stable reference
    val employerMap = remember(jobsWithEmployers) {
        jobsWithEmployers.associate { it.job.id to it.employerName }
    }

    // 🚀 SIMPLIFIED: Consolidated state management
    var currentIndex by rememberSaveable { mutableIntStateOf(0) }
    var selectedJob by remember { mutableStateOf<Job?>(null) }
    var showConfetti by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // 🚀 OPTIMIZED: Vibrator with lazy initialization
    val vibrator = remember {
        lazy {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
        }
    }

    // 🚀 COMPLETELY FIXED: Simplified job action handler with proper index management
    val handleJobAction = remember(jobViewModel, processedJobsViewModel, coroutineScope) {
        { job: Job, isAccept: Boolean ->
            // 🚀 CRITICAL: Immediate duplicate check
            if (processingJobs.contains(job.id)) {
                Log.w("SwipeableJobCards", "🚫 Job ${job.id} already processing, ignoring")
                return@remember
            }

            // 🚀 CRITICAL: Bounds check before processing
            if (currentIndex >= displayJobs.size) {
                Log.w("SwipeableJobCards", "Index $currentIndex out of bounds for ${displayJobs.size} jobs")
                return@remember
            }

            // Mark as processing immediately
            processingJobs.add(job.id)

            // 🚀 CRITICAL FIX: Add to session processed immediately to prevent job reappearing
            sessionProcessedJobs.add(job.id)

            throttledAction {
                // 🚀 CRITICAL: Use coroutine scope for suspend functions
                coroutineScope.launch {
                    try {
                        Log.d("SwipeableJobCards", "⚡ Processing ${if (isAccept) "ACCEPT" else "REJECT"} for job ${job.id}")

                        // 🚀 CRITICAL FIX: Always advance index in both modes
                        val isLastJob = currentIndex == displayJobs.size - 1

                        if (!isLastJob) {
                            currentIndex = currentIndex + 1
                            Log.d("SwipeableJobCards", "Advanced currentIndex to $currentIndex")
                        } else {
                            Log.d("SwipeableJobCards", "Reached last job, currentIndex remains at $currentIndex")
                        }

                        // 🚀 STEP 2: Process job action through ViewModel
                        if (isAccept) {
                            // Accept processing - call ViewModel method
                            showConfetti = true
                            stableCallbacks.onAccepted(job)

                            // Use ViewModel method for job application
                            jobViewModel.applyForJob(job.id)

                        } else {
                            // Reject processing - call ViewModel method
                            stableCallbacks.onRejected(job)

                            // Use ViewModel method for job rejection
                            jobViewModel.markJobAsNotInterested(job.id)
                        }

                        // 🚀 STEP 3: Background operations (non-blocking)
                        launch(Dispatchers.Default) {
                            try {
                                // Vibration
                                if (vibrator.value.hasVibrator()) {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                        vibrator.value.vibrate(
                                            VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE)
                                        )
                                    } else {
                                        @Suppress("DEPRECATION")
                                        vibrator.value.vibrate(50)
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e("SwipeableJobCards", "Vibration error: ${e.message}")
                            }
                        }

                        // History refresh (background)
                        launch(Dispatchers.IO) {
                            try {
                                jobHistoryViewModel?.refreshApplicationHistory()
                            } catch (e: Exception) {
                                Log.e("SwipeableJobCards", "History refresh error: ${e.message}")
                            }
                        }

                    } catch (e: Exception) {
                        Log.e("SwipeableJobCards", "❌ Job action failed: ${e.message}")
                        stableCallbacks.onError(e.message ?: "Unknown error")

                        // 🚀 FIX: Revert index and session processing on error
                        currentIndex = maxOf(0, currentIndex - 1)
                        sessionProcessedJobs.remove(job.id)
                        Log.d("SwipeableJobCards", "Reverted changes due to error")
                    } finally {
                        // 🚀 CRITICAL: Always remove from processing set
                        processingJobs.remove(job.id)
                    }
                }
            }
        }
    }

    // 🚀 FIX: Reset index when switching modes or when jobs change significantly
    LaunchedEffect(uiState.isShowingRejectedJobs, displayJobs.size) {
        // Only reset if we're out of bounds or switching modes
        if (currentIndex >= displayJobs.size && displayJobs.isNotEmpty()) {
            currentIndex = 0
            Log.d("SwipeableJobCards", "Reset index due to bounds check: displayJobs=${displayJobs.size}")
        }
    }

    // 🚀 FIX: Clear session processed jobs when entering rejected jobs mode
    LaunchedEffect(uiState.isShowingRejectedJobs) {
        if (uiState.isShowingRejectedJobs) {
            sessionProcessedJobs.clear()
            Log.d("SwipeableJobCards", "Cleared session processed jobs for rejected jobs mode")
        } else {
            sessionProcessedJobs.clear()
            Log.d("SwipeableJobCards", "Cleared session processed jobs for regular mode")
        }
    }

    // 🚀 NEW: Clear processing jobs when component is disposed
    DisposableEffect(Unit) {
        onDispose {
            processingJobs.clear()
            sessionProcessedJobs.clear()
        }
    }

    // 🚀 ENHANCED: Clear stale processing jobs periodically
    LaunchedEffect(Unit) {
        while (true) {
            delay(5000) // Every 5 seconds
            if (processingJobs.isNotEmpty()) {
                Log.d("SwipeableJobCards", "Clearing ${processingJobs.size} potentially stale processing jobs")
                processingJobs.clear()
            }
        }
    }

    // 🚀 ENHANCED: Better logging and state monitoring with reconsideration info
    LaunchedEffect(isShowingRejectedJobs, featuredJobs.size, currentIndex, reconsideredJobIds.size) {
        Log.d("SwipeableJobCards", "🚀 UI State - Mode: ${if (isShowingRejectedJobs) "REJECTED" else "REGULAR"}, Jobs: ${featuredJobs.size}, Display: ${displayJobs.size}, Index: $currentIndex, Reconsidered: ${reconsideredJobIds.size}")
        if (isShowingRejectedJobs) {
            Log.d("SwipeableJobCards", "Rejected jobs mode - Job IDs: ${featuredJobs.map { it.id.take(8) + "..." }}")
        }

        // 🚀 NEW: Log reconsideration state in debug builds
        if (BuildConfig.DEBUG) {
            logReconsiderationState()
        }
    }

    // Reset confetti animation
    LaunchedEffect(showConfetti) {
        if (showConfetti) {
            delay(1200) // Shorter duration
            showConfetti = false
        }
    }

    // 🚀 MAIN UI LAYOUT - FIXED CENTERING
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        // 🚀 ENHANCED: Better state rendering logic with rejected jobs session awareness
        when {
            displayJobs.isEmpty() -> {
                if (uiState.isShowingRejectedJobs && uiState.processedJobIds.isNotEmpty()) {
                    // User has finished reviewing rejected jobs
                    NoMoreJobsCard(
                        onResetClick = {
                            currentIndex = 0
                            sessionProcessedJobs.clear()
                        },
                        jobViewModel = jobViewModel,
                        processedJobsViewModel = processedJobsViewModel,
                        jobHistoryViewModel = jobHistoryViewModel,
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .align(Alignment.Center)
                    )
                } else if (uiState.processedJobIds.isNotEmpty()) {
                    // User has processed jobs but none are available to display
                    NoMoreJobsCard(
                        onResetClick = {
                            currentIndex = 0
                            sessionProcessedJobs.clear()
                        },
                        jobViewModel = jobViewModel,
                        processedJobsViewModel = processedJobsViewModel,
                        jobHistoryViewModel = jobHistoryViewModel,
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .align(Alignment.Center)
                    )
                } else {
                    // User has no processed jobs and no jobs available
                    EmptyJobsPlaceholder(
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .align(Alignment.Center)
                    )
                }
            }
            currentIndex >= displayJobs.size -> {
                // User has swiped through all available jobs
                NoMoreJobsCard(
                    onResetClick = {
                        currentIndex = 0
                        sessionProcessedJobs.clear()
                    },
                    jobViewModel = jobViewModel,
                    processedJobsViewModel = processedJobsViewModel,
                    jobHistoryViewModel = jobHistoryViewModel,
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .align(Alignment.Center)
                )
            }
            else -> {
                // 🚀 PERFORMANCE: Render cards efficiently
                JobCardsDisplay(
                    displayJobs = displayJobs,
                    currentIndex = currentIndex,
                    employerMap = employerMap,
                    onSwipeLeft = { job -> handleJobAction(job, false) },
                    onSwipeRight = { job -> handleJobAction(job, true) },
                    onCardClick = { job -> selectedJob = job },
                    showConfetti = showConfetti,
                    isShowingRejectedJobs = uiState.isShowingRejectedJobs,
                    isSwipeEnabled = isSwipeEnabled  // ADD THIS LINE
                )
            }
        }
    }

    // 🚀 OPTIMIZED: Dialog with better state management
    selectedJob?.let { job ->
        JobDetailsDialog(
            job = job,
            employerName = employerMap[job.id] ?: "Unknown Employer",
            onClose = { selectedJob = null },
            onApply = {
                handleJobAction(job, true)
                selectedJob = null
            },
            onReject = {
                handleJobAction(job, false)
                selectedJob = null
            }
        )
    }
}

// 🚀 ENHANCED: Stable data class for UI state with reconsideration support
@Stable
data class SwipeableJobsUiState(
    val sessionProcessedJobIds: Set<String> = emptySet(),
    val processedJobIds: Set<String> = emptySet(),
    val isShowingRejectedJobs: Boolean = false,
    val featuredJobs: List<Job> = emptyList(),
    val reconsideredJobIds: Set<String> = emptySet() // 🚀 NEW: Reconsideration state
)

// 🚀 PERFORMANCE: Stable callback container
@Stable
data class SwipeJobCallbacks(
    val onAccepted: (Job) -> Unit,
    val onRejected: (Job) -> Unit,
    val onDetails: (String) -> Unit,
    val onError: (String) -> Unit
)

// 🚀 OPTIMIZED: Separated card display logic with proper centering
@Composable
private fun JobCardsDisplay(
    displayJobs: List<Job>,
    currentIndex: Int,
    employerMap: Map<String, String>,
    onSwipeLeft: (Job) -> Unit,
    onSwipeRight: (Job) -> Unit,
    onCardClick: (Job) -> Unit,
    showConfetti: Boolean,
    isShowingRejectedJobs: Boolean,
    isSwipeEnabled: Boolean = true  // ADD THIS PARAMETER
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center // 🚀 FIX: Ensure main container centers content
    ) {
        if (!isSwipeEnabled && displayJobs.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(16.dp)
                    .zIndex(25f),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Complete active job to apply for new jobs",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
        // 🚀 OPTIMIZED: Confetti overlay
        if (showConfetti) {
            ConfettiAnimation(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(30f)
            )
        }

        // 🚀 PERFORMANCE: Render only visible cards with proper centering
        val topJob = displayJobs.getOrNull(currentIndex)
        val nextJob = displayJobs.getOrNull(currentIndex + 1)

        // 🚀 FIX: Cards container with proper centering
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp), // Add horizontal padding for better spacing
            contentAlignment = Alignment.Center // 🚀 FIX: Center the cards
        ) {
            // Background card
            nextJob?.let { job ->
                key(job.id) {
                    StaticJobCard(
                        job = job,
                        employerName = employerMap[job.id] ?: "Unknown Employer",
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .offset(y = (-10).dp)
                            .scale(0.95f)
                            .zIndex(1f)
                    )
                }
            }

            // Top card
            topJob?.let { job ->
                key(job.id) {
                    SwipeableJobCard(
                        job = job,
                        employerName = employerMap[job.id] ?: "Unknown Employer",
                        onSwipeLeft = { onSwipeLeft(job) },
                        onSwipeRight = { onSwipeRight(job) },
                        onCardClick = { onCardClick(job) },
                        isSwipeEnabled = isSwipeEnabled,  // ADD THIS LINE
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .zIndex(10f)
                    )
                }
            }
        }

        // 🚀 OPTIMIZED: Action buttons with proper positioning
        if (topJob != null && isSwipeEnabled) {
            ActionButtons(
                onReject = { onSwipeLeft(topJob) },
                onAccept = { onSwipeRight(topJob) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp) // 🚀 FIX: Increased bottom padding
                    .zIndex(20f)
            )
        }
    }
}

// 🚀 OPTIMIZED: Confetti animation
@Composable
private fun ConfettiAnimation(modifier: Modifier = Modifier) {
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.confetti))
    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations = 1
    )

    LottieAnimation(
        composition = composition,
        progress = { progress },
        modifier = modifier
    )
}

// 🚀 OPTIMIZED: Action buttons
@Composable
private fun ActionButtons(
    onReject: () -> Unit,
    onAccept: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(48.dp)
    ) {
        FloatingActionButton(
            onClick = onReject,
            containerColor = Color.White,
            contentColor = Color.Red,
            modifier = Modifier.size(56.dp)
        ) {
            Icon(Icons.Default.Close, "Reject", Modifier.size(30.dp))
        }

        FloatingActionButton(
            onClick = onAccept,
            containerColor = Color.White,
            contentColor = PrimaryBlue,
            modifier = Modifier.size(56.dp)
        ) {
            Icon(Icons.Default.Favorite, "Accept", Modifier.size(30.dp))
        }
    }
}

// 🚀 OPTIMIZED: Dialog component
@Composable
private fun JobDetailsDialog(
    job: Job,
    employerName: String,
    onClose: () -> Unit,
    onApply: () -> Unit,
    onReject: () -> Unit
) {
    AnimatedVisibility(
        visible = true,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut()
    ) {
        FullScreenJobDetailsDialog(
            job = job,
            employerName = employerName,
            onClose = onClose,
            onApply = onApply,
            onReject = onReject,
            triggerVibration = { /* Handled in parent */ }
        )
    }
}

// 🚀 NEW: Empty rejected jobs card
@Composable
private fun EmptyRejectedJobsCard(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.padding(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "No jobs to reconsider",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "You haven't marked any jobs as not interested yet",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun SwipeableJobCardsWithMonitoring(
    jobs: List<Job>,
    jobsWithEmployers: List<JobWithEmployer> = emptyList(),
    onJobAccepted: (Job) -> Unit,
    onJobRejected: (Job) -> Unit,
    onJobDetails: (String) -> Unit,
    modifier: Modifier = Modifier,
    isSwipeEnabled: Boolean = true,  // ADD THIS PARAMETER
    jobViewModel: JobViewModel = hiltViewModel(),
    processedJobsViewModel: ProcessedJobsViewModel = hiltViewModel(),
    jobHistoryViewModel: JobHistoryViewModel? = hiltViewModel(),
) {
    // Monitor overall component performance
    val startTime = remember { PerformanceUtils.PerformanceMetrics.startTiming("swipeable_cards_render", "ui") }

    SwipeableJobCards(
        jobs = jobs,
        jobsWithEmployers = jobsWithEmployers,
        onJobAccepted = onJobAccepted,
        onJobRejected = onJobRejected,
        onJobDetails = onJobDetails,
        modifier = modifier,
        isSwipeEnabled = isSwipeEnabled,  // ADD THIS LINE
        jobViewModel = jobViewModel,
        processedJobsViewModel = processedJobsViewModel,
        jobHistoryViewModel = jobHistoryViewModel
    )

    DisposableEffect(Unit) {
        onDispose {
            PerformanceUtils.PerformanceMetrics.endTiming("swipeable_cards_render", startTime)
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun RefreshableSwipeableJobCards(
    jobs: List<Job>,
    jobsWithEmployers: List<JobWithEmployer> = emptyList(),
    onJobAccepted: (Job) -> Unit,
    onJobRejected: (Job) -> Unit,
    onJobDetails: (String) -> Unit,
    onNavigateToApplications: () -> Unit = {},
    onNavigateToLocationSearch: () -> Unit = {},
    onShowJobAlertDialog: () -> Unit = {},
    modifier: Modifier = Modifier,
    isSwipeEnabled: Boolean = true,  // ADD THIS PARAMETER
    jobViewModel: JobViewModel = hiltViewModel(),
    processedJobsViewModel: ProcessedJobsViewModel = hiltViewModel(),
    jobHistoryViewModel: JobHistoryViewModel = hiltViewModel(),
) {
    // State for refresh
    var isRefreshing by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    // Collect states from ViewModels
    val employeeProfile by jobViewModel.employeeProfile.collectAsState()
    val isShowingRejectedJobs by processedJobsViewModel.isShowingRejectedJobs.collectAsState()
    val appliedJobIds by processedJobsViewModel.appliedJobIds.collectAsState()

    // Pull refresh state
    val pullRefreshState = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh = {
            coroutineScope.launch {
                isRefreshing = true

                // Get current district
                val district = employeeProfile?.district ?: ""

                // Refresh based on current mode
                if (isShowingRejectedJobs) {
                    // If showing rejected jobs, refresh rejected jobs
                    jobViewModel.getOnlyRejectedJobs(district)
                } else {
                    // Otherwise refresh regular jobs
                    jobViewModel.getLocalizedFeaturedJobs(district, limit = 10)
                }

                // Give time for refresh to complete
                delay(1000)
                isRefreshing = false
            }
        }
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .pullRefresh(pullRefreshState)
    ) {
        if (jobs.isEmpty() && !isRefreshing) {
            // Empty state
            EmptyJobsState(
                isShowingRejectedJobs = isShowingRejectedJobs,
                onNavigateToApplications = onNavigateToApplications,
                onNavigateToLocationSearch = onNavigateToLocationSearch
            )
        } else {
            // The main swipeable cards component
            SwipeableJobCards(
                jobs = jobs,
                jobsWithEmployers = jobsWithEmployers,
                onJobAccepted = onJobAccepted,
                onJobRejected = onJobRejected,
                onJobDetails = onJobDetails,
                modifier = Modifier.fillMaxSize(),
                isSwipeEnabled = isSwipeEnabled  // ADD THIS LINE
            )

            // Quick action buttons overlay (top right)
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Location search button
                if (onNavigateToLocationSearch != {}) {
                    FilledTonalIconButton(
                        onClick = onNavigateToLocationSearch,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = "Search other locations",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Job alerts button
                if (onShowJobAlertDialog != {}) {
                    FilledTonalIconButton(
                        onClick = onShowJobAlertDialog,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationAdd,
                            contentDescription = "Create job alert",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Applied jobs count badge (top left)
            if (appliedJobIds.isNotEmpty()) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(16.dp),
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "${appliedJobIds.size} Applied",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        // Pull refresh indicator
        PullRefreshIndicator(
            refreshing = isRefreshing,
            state = pullRefreshState,
            modifier = Modifier.align(Alignment.TopCenter),
            backgroundColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun EmptyJobsState(
    isShowingRejectedJobs: Boolean,
    onNavigateToApplications: () -> Unit,
    onNavigateToLocationSearch: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = if (isShowingRejectedJobs) {
                Icons.Default.CheckCircle
            } else {
                Icons.Default.WorkOff
            },
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = if (isShowingRejectedJobs) {
                "All caught up!"
            } else {
                "No new jobs available"
            },
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = if (isShowingRejectedJobs) {
                "You've reviewed all jobs you previously marked as not interested"
            } else {
                "Pull down to refresh or check back later"
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Action buttons
        OutlinedButton(
            onClick = onNavigateToLocationSearch,
            modifier = Modifier.fillMaxWidth(0.8f)
        ) {
            Icon(
                Icons.Default.LocationOn,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Search Other Areas")
        }

        Spacer(modifier = Modifier.height(12.dp))

        TextButton(onClick = onNavigateToApplications) {
            Text("View Your Applications")
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
    val isShowingRejectedJobs by processedJobsViewModel.isShowingRejectedJobs.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
            .heightIn(min = 220.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 🚀 ENHANCED: Different icons and messages based on mode
            Icon(
                imageVector = if (isShowingRejectedJobs) {
                    Icons.Default.CheckCircle
                } else {
                    Icons.Default.Refresh
                },
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = if (isShowingRejectedJobs) {
                    Color.Green
                } else {
                    MaterialTheme.colorScheme.primary
                }
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = if (isShowingRejectedJobs)
                    "✅ All not interested jobs reviewed!"
                else
                    "You've seen all available jobs",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = if (isShowingRejectedJobs)
                    "You've finished reconsidering all jobs you previously marked as not interested"
                else
                    "Check back later for new job opportunities",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (isShowingRejectedJobs) {
                // 🚀 ENHANCED: Return to regular job search with session cleanup
                Button(
                    onClick = {
                        Log.d("NoMoreJobsCard", "Returning to regular job search after reviewing not interested jobs")

                        coroutineScope.launch {
                            try {
                                // End the rejected jobs session properly
                                jobViewModel.endRejectedJobsSession()
                                processedJobsViewModel.setShowingRejectedJobs(false)

                                // Get fresh jobs for regular browsing
                                val district = jobViewModel.employeeProfile.value?.district ?: ""
                                if (district.isNotBlank()) {
                                    jobViewModel.getLocalizedFeaturedJobs(district, 10)
                                } else {
                                    jobViewModel.getFeaturedJobs(10)
                                }

                                onResetClick()
                                jobHistoryViewModel?.refreshApplicationHistory()

                                Log.d("NoMoreJobsCard", "Successfully returned to regular job search")
                            } catch (e: Exception) {
                                Log.e("NoMoreJobsCard", "Error returning to job search: ${e.message}")
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Green
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Back to Job Search")
                }
            } else {
                // 🚀 ENHANCED: Show not interested jobs option with better messaging
                val notInterestedCount = processedJobsViewModel.rejectedJobIds.collectAsState().value.size

                Button(
                    onClick = {
                        Log.d("NoMoreJobsCard", "Starting not interested jobs review session")

                        coroutineScope.launch {
                            try {
                                // Start rejected jobs session properly
                                jobViewModel.startRejectedJobsSession()
                                processedJobsViewModel.setShowingRejectedJobs(true)

                                // Get not interested jobs
                                val district = jobViewModel.employeeProfile.value?.district ?: ""
                                jobViewModel.getOnlyRejectedJobs(district)

                                onResetClick()
                                jobHistoryViewModel?.refreshApplicationHistory()

                                Log.d("NoMoreJobsCard", "Successfully started not interested jobs session")
                            } catch (e: Exception) {
                                Log.e("NoMoreJobsCard", "Error starting not interested jobs session: ${e.message}")
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = notInterestedCount > 0
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (notInterestedCount > 0) {
                            "Reconsider $notInterestedCount Not Interested Jobs"
                        } else {
                            "No Jobs to Reconsider"
                        }
                    )
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
                .padding(16.dp),
            verticalArrangement = Arrangement.Top, // 🚀 FIX: Proper vertical arrangement
            horizontalAlignment = Alignment.Start // 🚀 FIX: Proper horizontal alignment
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

            Spacer(modifier = Modifier.height(12.dp))

            // Salary information
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

            // We don't need to show the complete details for background cards
        }
    }
}

@Composable
fun EmptyJobsPlaceholder(modifier: Modifier = Modifier) {
    // 🚀 FIX: Removed the Box wrapper that was causing centering issues
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(400.dp), // 🚀 FIX: Match the height of other job cards
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp), // 🚀 FIX: Increased padding for better spacing
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // 🚀 FIX: Add an icon for better visual appeal
            Icon(
                imageVector = Icons.Default.WorkOff,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "No Jobs Available",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "We couldn't find any jobs matching your location. Please check back later or try a different location.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 🚀 FIX: Add a small action hint
            Text(
                text = "Pull down to refresh",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                textAlign = TextAlign.Center
            )
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

// 🚀 CRITICAL FIXES for SwipeableJobCard - Navigation and Processing Issues

@OptIn(ExperimentalWearMaterialApi::class)
@Composable
fun SwipeableJobCard(
    job: Job,
    employerName: String,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
    onCardClick: () -> Unit,
    modifier: Modifier = Modifier,
    isSwipeEnabled: Boolean = true  // ADD THIS PARAMETER
) {
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
    val rotation = if (isSwipeEnabled) {
        (swipeableState.offset.value / screenWidth) * 15f
    } else {
        0f
    }
    // Determine when to show swipe overlays
    val showLeftSwipeOverlay = swipeableState.offset.value < -screenWidth * 0.05f
    val showRightSwipeOverlay = swipeableState.offset.value > screenWidth * 0.05f

    // 🚀 CRITICAL FIX: Better swipe completion handling with immediate reset
    LaunchedEffect(swipeableState.currentValue,isSwipeEnabled) {
        if (!isSwipeEnabled) return@LaunchedEffect  // Don't handle swipes if disabled
        when (swipeableState.currentValue) {
            SwipeDirection.LEFT -> {
                Log.d("SwipeableJobCard", "LEFT swipe completed for job: ${job.id}")

                // 🚀 FIX: Trigger action and immediately reset to prevent stuck state
                onSwipeLeft()

                // 🚀 CRITICAL: Reset to center immediately to prevent multiple triggers
                try {
                    swipeableState.snapTo(SwipeDirection.CENTER)
                    Log.d("SwipeableJobCard", "Reset position to CENTER for job: ${job.id}")
                } catch (e: Exception) {
                    Log.e("SwipeableJobCard", "Error resetting swipe state: ${e.message}")
                }
            }
            SwipeDirection.RIGHT -> {
                Log.d("SwipeableJobCard", "RIGHT swipe completed for job: ${job.id}")

                // 🚀 FIX: Trigger action and immediately reset to prevent stuck state
                onSwipeRight()

                // 🚀 CRITICAL: Reset to center immediately to prevent multiple triggers
                try {
                    swipeableState.snapTo(SwipeDirection.CENTER)
                    Log.d("SwipeableJobCard", "Reset position to CENTER for job: ${job.id}")
                } catch (e: Exception) {
                    Log.e("SwipeableJobCard", "Error resetting swipe state: ${e.message}")
                }
            }
            else -> { /* Do nothing if we're in the center */ }
        }
    }

    // 🚀 ENHANCED: Better job change handling with immediate state reset
    LaunchedEffect(job.id) {
        Log.d("SwipeableJobCard", "New job card: ${job.id}, resetting position")
        try {
            // 🚀 CRITICAL: Always reset to center when job changes
            swipeableState.snapTo(SwipeDirection.CENTER)
            Log.d("SwipeableJobCard", "Successfully reset position for new job: ${job.id}")
        } catch (e: Exception) {
            Log.e("SwipeableJobCard", "Error resetting position for new job ${job.id}: ${e.message}")
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(400.dp)
            .offset { IntOffset(swipeableState.offset.value.roundToInt(), 0) }
            .rotate(rotation)
            .then(
                if (isSwipeEnabled) {
                    Modifier.swipeable(
                        state = swipeableState,
                        anchors = anchors,
                        thresholds = { _, _ -> FractionalThreshold(0.3f) },
                        orientation = Orientation.Horizontal,
                        resistance = null
                    )
                } else {
                    Modifier  // No swipeable behavior when disabled
                }
            ),
        elevation = CardDefaults.cardElevation(8.dp),
        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface),
        onClick = {
            // Only allow clicks when swipe is enabled AND card is centered
            if (isSwipeEnabled && abs(swipeableState.offset.value) < screenWidth * 0.1f) {
                Log.d("SwipeableJobCard", "Card clicked: ${job.id}")
                onCardClick()
            } else if (!isSwipeEnabled) {
                // Show a message when clicking disabled card
                Log.d("SwipeableJobCard", "Card click disabled due to active job")
                // You could trigger a snackbar here if you pass the callback
            }
        }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Job card content with proper padding and alignment
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.Top,
                horizontalAlignment = Alignment.Start
            ) {
                // Job title with more emphasis
                Text(
                    text = job.title.takeIf { it.isNotBlank() } ?: "Untitled Job",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Employer name with icon
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Work,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = employerName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

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
                        modifier = Modifier.padding(12.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

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
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = buildString {
                                if (!job.district.isNullOrBlank()) append(job.district)
                                if (!job.district.isNullOrBlank() && !job.state.isNullOrBlank()) append(", ")
                                if (!job.state.isNullOrBlank()) append(job.state)
                            }.ifBlank { "Location not specified" },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Job description with better styling
                if (!job.description.isNullOrBlank()) {
                    Text(
                        text = "Description",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = job.description,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 20.sp
                    )
                } else {
                    Text(
                        text = "Tap card to see full job details",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // Bottom hint with better styling
                Surface(
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = "Tap for details • Swipe to apply/reject",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }


            if (!isSwipeEnabled) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Color.Black.copy(alpha = 0.2f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                        ),
                        elevation = CardDefaults.cardElevation(4.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Work,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Complete your active job first",
                                style = MaterialTheme.typography.titleMedium,  // ← Larger text
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Tap to view details",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            // Swipe action overlays with enhanced visibility and better positioning
            if (showRightSwipeOverlay && isSwipeEnabled) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Card(
                        modifier = Modifier
                            .rotate(-20f)
                            .scale(0.9f + (abs(swipeableState.offset.value) / screenWidth) * 0.2f),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xCC00AA00)
                        ),
                        elevation = CardDefaults.cardElevation(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "APPLY",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                    }
                }
            }

            if (showLeftSwipeOverlay && isSwipeEnabled) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Card(
                        modifier = Modifier
                            .rotate(20f)
                            .scale(0.9f + (abs(swipeableState.offset.value) / screenWidth) * 0.2f),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xCCFF4444)
                        ),
                        elevation = CardDefaults.cardElevation(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "NOT INTERESTED",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DebugNavigationOverlay(
    displayJobs: List<Job>,
    currentIndex: Int,
    isShowingRejectedJobs: Boolean,
    sessionProcessedJobs: Set<String>
) {
    if (BuildConfig.DEBUG) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.TopStart
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color.Black.copy(alpha = 0.7f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(8.dp)
                ) {
                    Text(
                        text = "DEBUG MODE",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Mode: ${if (isShowingRejectedJobs) "REJECTED" else "REGULAR"}",
                        color = Color.White,
                        fontSize = 8.sp
                    )
                    Text(
                        text = "Jobs: ${displayJobs.size}, Index: $currentIndex",
                        color = Color.White,
                        fontSize = 8.sp
                    )
                    Text(
                        text = "Session Processed: ${sessionProcessedJobs.size}",
                        color = Color.White,
                        fontSize = 8.sp
                    )
                    if (displayJobs.isNotEmpty() && currentIndex < displayJobs.size) {
                        Text(
                            text = "Current: ${displayJobs[currentIndex].id.take(8)}...",
                            color = Color.Yellow,
                            fontSize = 8.sp
                        )
                    }
                }
            }
        }
    }
}