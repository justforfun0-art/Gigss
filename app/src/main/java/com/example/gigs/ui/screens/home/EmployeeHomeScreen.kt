package com.example.gigs.ui.screens.home

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.gigs.data.model.ApplicationStatus
import com.example.gigs.data.model.ApplicationWithJob
import com.example.gigs.data.model.EmployeeProfile
import com.example.gigs.data.model.Job
import com.example.gigs.data.model.JobAlert
import com.example.gigs.data.model.JobWithEmployer
import com.example.gigs.data.model.WorkPreference
import com.example.gigs.data.repository.ApplicationRepository
import com.example.gigs.data.repository.JobRepository
import com.example.gigs.ui.components.*
import com.example.gigs.ui.screens.dashboard.JobItem
import com.example.gigs.viewmodel.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
// Fixed EmployeeHomeScreen.kt - Main function with proper Accept Job handling

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmployeeHomeScreen(
    authViewModel: AuthViewModel,
    jobRepository: JobRepository,
    onSignOut: () -> Unit,
    onNavigateToDashboard: () -> Unit,
    onNavigateToJobListing: (String) -> Unit,
    onNavigateToMessages: () -> Unit = {},
    onNavigateToNotifications: () -> Unit = {},
    onNavigateToJobHistory: () -> Unit = {},
    onNavigateToJobDetails: (String) -> Unit = {}
) {
    var selectedTab by remember { mutableStateOf(0) }
    val profileViewModel: ProfileViewModel = hiltViewModel()
    val jobViewModel: JobViewModel = hiltViewModel()
    val processedJobsViewModel: ProcessedJobsViewModel = hiltViewModel()
    val jobHistoryViewModel: JobHistoryViewModel = hiltViewModel()

    // Get ApplicationRepository through JobViewModel
    val applicationRepository = jobViewModel.applicationRepository

    val employeeProfile by profileViewModel.employeeProfile.collectAsState()

    // Track multiple selected jobs instead of single active job
    var selectedJobs by remember { mutableStateOf<List<ApplicationWithJob>>(emptyList()) }
    var isLoadingSelectedJobs by remember { mutableStateOf(false) }
    var selectedJobError by remember { mutableStateOf<String?>(null) }
    var currentSelectedJobIndex by remember { mutableStateOf(0) }

    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Load selected jobs instead of single active job
    LaunchedEffect(Unit) {
        profileViewModel.getEmployeeProfile()

        // Load selected jobs immediately
        try {
            isLoadingSelectedJobs = true
            Log.d("EmployeeHomeScreen", "🚀 Initial loading - refreshing application history")

            jobHistoryViewModel.refreshApplicationHistory()
            delay(1000) // Give more time for data to load

            val selectedJobsList = jobHistoryViewModel.getSelectedJobs()
            selectedJobs = selectedJobsList

            Log.d("EmployeeHomeScreen", "🚀 Found ${selectedJobsList.size} selected jobs")

        } catch (e: Exception) {
            selectedJobError = "Failed to load selected jobs: ${e.message}"
            Log.e("EmployeeHomeScreen", "❌ Error loading selected jobs: ${e.message}")
        } finally {
            isLoadingSelectedJobs = false
        }
    }

    // Refresh selected jobs when switching to home tab
    LaunchedEffect(selectedTab) {
        if (selectedTab == 0) { // Home tab
            Log.d("EmployeeHomeScreen", "🚀 Home tab selected, checking for selected job updates")
            try {
                jobHistoryViewModel.refreshApplicationHistory()
                delay(500)
                val refreshedSelectedJobs = jobHistoryViewModel.getSelectedJobs()
                if (refreshedSelectedJobs != selectedJobs) {
                    selectedJobs = refreshedSelectedJobs
                    Log.d("EmployeeHomeScreen", "🚀 Selected jobs refreshed: ${refreshedSelectedJobs.size} jobs")
                }
            } catch (e: Exception) {
                Log.e("EmployeeHomeScreen", "❌ Error refreshing selected jobs on tab switch: ${e.message}")
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("GigWork") },
                actions = {
                    IconButton(onClick = onNavigateToJobHistory) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = "Job History"
                        )
                    }

                    IconButton(onClick = {
                        authViewModel.signOut()
                        onSignOut()
                    }) {
                        Icon(
                            imageVector = Icons.Default.ExitToApp,
                            contentDescription = "Sign Out"
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Home,
                            contentDescription = "Home"
                        )
                    },
                    label = { Text("Home") }
                )

                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = {
                        selectedTab = 1
                        employeeProfile?.district?.let { district ->
                            onNavigateToJobListing(district)
                        }
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Find Jobs"
                        )
                    },
                    label = { Text("Find Jobs") }
                )

                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = {
                        selectedTab = 2
                        onNavigateToDashboard()
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Profile"
                        )
                    },
                    label = { Text("Profile") }
                )

                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = {
                        selectedTab = 3
                        onNavigateToJobHistory()
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = "Job History"
                        )
                    },
                    label = { Text("History") }
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Show selected jobs section at the top (always visible)
            if (selectedJobs.isNotEmpty()) {
                SelectedJobsSection(
                    selectedJobs = selectedJobs,
                    currentIndex = currentSelectedJobIndex,
                    onPrevious = {
                        if (currentSelectedJobIndex > 0) {
                            currentSelectedJobIndex--
                        }
                    },
                    onNext = {
                        if (currentSelectedJobIndex < selectedJobs.size - 1) {
                            currentSelectedJobIndex++
                        }
                    },
                    // 🚀 FIXED: Accept Job implementation using SELECTED status
                    onAcceptJob = { jobIndex ->
                        val selectedJob = selectedJobs[jobIndex]
                        coroutineScope.launch {
                            try {
                                isLoadingSelectedJobs = true
                                selectedJobError = null

                                Log.d("EmployeeHomeScreen", "🚀 Accepting job: ${selectedJob.id}")

                                // Check for date conflicts
                                val hasConflict = checkJobDateConflict(selectedJobs, selectedJob)
                                if (hasConflict) {
                                    snackbarHostState.showSnackbar(
                                        "Cannot accept job: You have another job on the same date/time",
                                        duration = SnackbarDuration.Long
                                    )
                                    return@launch
                                }

                                // 🚀 FIXED: Accept the job (update status to ACCEPTED, not HIRED)
                                applicationRepository.updateEmployeeApplicationStatus(
                                    selectedJob.jobId,
                                    "ACCEPTED"  // Changed from HIRED to ACCEPTED
                                ).collect { updateResult ->
                                    if (updateResult.isSuccess) {
                                        snackbarHostState.showSnackbar("Job accepted successfully!")

                                        // Refresh selected jobs
                                        jobHistoryViewModel.refreshApplicationHistory()
                                        delay(500)
                                        selectedJobs = jobHistoryViewModel.getSelectedJobs()

                                        Log.d("EmployeeHomeScreen", "✅ Job accepted and status updated to ACCEPTED")
                                    } else {
                                        val errorMsg = updateResult.exceptionOrNull()?.message ?: "Failed to accept job"
                                        selectedJobError = errorMsg
                                        snackbarHostState.showSnackbar(
                                            "Failed to accept job: $errorMsg",
                                            duration = SnackbarDuration.Long
                                        )
                                        Log.e("EmployeeHomeScreen", "❌ Job acceptance failed: $errorMsg")
                                    }
                                }
                            } catch (e: Exception) {
                                val errorMsg = e.message ?: "Unknown error occurred"
                                selectedJobError = errorMsg
                                snackbarHostState.showSnackbar(
                                    "Error accepting job: $errorMsg",
                                    duration = SnackbarDuration.Long
                                )
                                Log.e("EmployeeHomeScreen", "❌ Exception accepting job: $errorMsg", e)
                            } finally {
                                isLoadingSelectedJobs = false
                            }
                        }
                    },
                    onStartWork = { jobIndex, otp ->
                        val selectedJob = selectedJobs[jobIndex]
                        coroutineScope.launch {
                            isLoadingSelectedJobs = true
                            selectedJobError = null

                            try {
                                Log.d("EmployeeHomeScreen", "🚀 Starting work with OTP: '$otp' for job: ${selectedJob.id}")

                                val result = applicationRepository.startWorkWithOtp(selectedJob.id, otp)

                                Log.d("EmployeeHomeScreen", "🚀 OTP verification result: ${result.isSuccess}")

                                if (result.isSuccess) {
                                    snackbarHostState.showSnackbar("Work started successfully!")

                                    // Wait and refresh to get updated status
                                    delay(1000)
                                    jobHistoryViewModel.refreshApplicationHistory()
                                    delay(500)

                                    selectedJobs = jobHistoryViewModel.getSelectedJobs()

                                    Log.d("EmployeeHomeScreen", "🚀 Selected jobs updated after work start")

                                } else {
                                    val errorMessage = result.exceptionOrNull()?.message ?: "Invalid OTP or verification failed"
                                    Log.e("EmployeeHomeScreen", "❌ OTP verification failed: $errorMessage")

                                    selectedJobError = errorMessage
                                    snackbarHostState.showSnackbar(
                                        message = errorMessage,
                                        duration = SnackbarDuration.Long
                                    )
                                }
                            } catch (e: Exception) {
                                val errorMessage = e.message ?: "Failed to start work"
                                Log.e("EmployeeHomeScreen", "❌ Exception during OTP verification: $errorMessage", e)

                                selectedJobError = errorMessage
                                snackbarHostState.showSnackbar(
                                    message = "Error: $errorMessage",
                                    duration = SnackbarDuration.Long
                                )
                            } finally {
                                isLoadingSelectedJobs = false
                            }
                        }
                    },
                    onCompleteWork = { jobIndex ->
                        val selectedJob = selectedJobs[jobIndex]
                        coroutineScope.launch {
                            isLoadingSelectedJobs = true
                            selectedJobError = null

                            try {
                                Log.d("EmployeeHomeScreen", "🚀 Completing work for job: ${selectedJob.id}")

                                val result = applicationRepository.completeWork(selectedJob.id)

                                if (result.isSuccess) {
                                    snackbarHostState.showSnackbar("Work completed successfully!")

                                    // Refresh and update selected jobs
                                    jobHistoryViewModel.refreshApplicationHistory()
                                    selectedJobs = jobHistoryViewModel.getSelectedJobs()

                                    Log.d("EmployeeHomeScreen", "🚀 Work completed, selected jobs updated")

                                } else {
                                    val errorMessage = result.exceptionOrNull()?.message ?: "Failed to complete work"
                                    Log.e("EmployeeHomeScreen", "❌ Work completion failed: $errorMessage")

                                    selectedJobError = errorMessage
                                    snackbarHostState.showSnackbar(
                                        message = errorMessage,
                                        duration = SnackbarDuration.Long
                                    )
                                }
                            } catch (e: Exception) {
                                val errorMessage = e.message ?: "Failed to complete work"
                                Log.e("EmployeeHomeScreen", "❌ Exception during work completion: $errorMessage", e)

                                selectedJobError = errorMessage
                                snackbarHostState.showSnackbar(
                                    message = "Error: $errorMessage",
                                    duration = SnackbarDuration.Long
                                )
                            } finally {
                                isLoadingSelectedJobs = false
                            }
                        }
                    },
                    isLoading = isLoadingSelectedJobs,
                    errorMessage = selectedJobError,
                    modifier = Modifier.padding(16.dp)
                )

                Divider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    thickness = 1.dp
                )
            }

            // Calculate if user has active work (WORK_IN_PROGRESS only)
            val hasActiveWork = selectedJobs.any { it.status == ApplicationStatus.WORK_IN_PROGRESS }

            // Rest of the content based on selected tab
            when (selectedTab) {
                0 -> EnhancedEmployeeHomeTab(
                    modifier = Modifier.weight(1f),
                    jobViewModel = jobViewModel,
                    processedJobsViewModel = processedJobsViewModel,
                    jobHistoryViewModel = jobHistoryViewModel,
                    jobRepository = jobRepository,
                    onJobDetails = onNavigateToJobDetails,
                    onViewJobHistory = onNavigateToJobHistory,
                    onNavigateToJobListing = onNavigateToJobListing,
                    onNavigateToDashboard = onNavigateToDashboard,
                    hasActiveWork = hasActiveWork,
                    hasSelectedJobs = selectedJobs.isNotEmpty()
                )
                1 -> EmployeeJobsTab(
                    modifier = Modifier.weight(1f),
                    district = employeeProfile?.district ?: "",
                    onJobSelected = onNavigateToJobDetails
                )
                2 -> EmployeeProfileTab(
                    modifier = Modifier.weight(1f),
                    profile = employeeProfile
                )
                3 -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                    LaunchedEffect(Unit) {
                        onNavigateToJobHistory()
                    }
                }
            }
        }
    }
}

// Helper function to check job date conflicts (simplified implementation)
private fun checkJobDateConflict(
    selectedJobs: List<ApplicationWithJob>,
    newJob: ApplicationWithJob
): Boolean {
    // This is a simplified version - implement proper date/time conflict checking
    // based on your job scheduling requirements
    val acceptedJobs = selectedJobs.filter {
        it.status in listOf(ApplicationStatus.WORK_IN_PROGRESS)
    }

    // For now, return false (no conflicts) - implement your date comparison logic here
    return false
}
@Composable
fun SelectedJobsSection(
    selectedJobs: List<ApplicationWithJob>,
    currentIndex: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onAcceptJob: (Int) -> Unit,
    onStartWork: (Int, String) -> Unit,
    onCompleteWork: (Int) -> Unit,
    isLoading: Boolean,
    errorMessage: String?,
    modifier: Modifier = Modifier
) {
    if (selectedJobs.isEmpty()) return

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header with navigation
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Selected Jobs",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )

                if (selectedJobs.size > 1) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onPrevious,
                            enabled = currentIndex > 0
                        ) {
                            Icon(
                                Icons.Default.KeyboardArrowLeft,
                                contentDescription = "Previous",
                                tint = if (currentIndex > 0)
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                else
                                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f)
                            )
                        }

                        Text(
                            text = "${currentIndex + 1}/${selectedJobs.size}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )

                        IconButton(
                            onClick = onNext,
                            enabled = currentIndex < selectedJobs.size - 1
                        ) {
                            Icon(
                                Icons.Default.KeyboardArrowRight,
                                contentDescription = "Next",
                                tint = if (currentIndex < selectedJobs.size - 1)
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                else
                                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Current job card
            if (currentIndex < selectedJobs.size) {
                val currentJob = selectedJobs[currentIndex]

                SelectedJobCard(
                    job = currentJob,
                    onAcceptJob = { onAcceptJob(currentIndex) },
                    onStartWork = { otp -> onStartWork(currentIndex, otp) },
                    onCompleteWork = { onCompleteWork(currentIndex) },
                    isLoading = isLoading,
                    errorMessage = errorMessage
                )
            }
        }
    }
}

// 🚀 UPDATED: Individual Selected Job Card Component with ACCEPTED status
// 🚀 UPDATED EmployeeHomeScreen.kt - Only the completion workflow part

@Composable
fun SelectedJobCard(
    job: ApplicationWithJob,
    onAcceptJob: () -> Unit,
    onStartWork: (String) -> Unit,
    onCompleteWork: () -> Unit,
    isLoading: Boolean,
    errorMessage: String?,
    modifier: Modifier = Modifier
) {
    var showOtpDialog by remember { mutableStateOf(false) }
    var otpInput by remember { mutableStateOf("") }

    // 🚀 NEW: Completion workflow state
    var showCompletionDialog by remember { mutableStateOf(false) }
    var completionOtp by remember { mutableStateOf("") }
    var completionError by remember { mutableStateOf<String?>(null) }
    var isCompletionLoading by remember { mutableStateOf(false) }

    // Get application repository for completion workflow
    val jobHistoryViewModel: JobHistoryViewModel = hiltViewModel()
    val applicationRepository = jobHistoryViewModel.applicationRepository
    val coroutineScope = rememberCoroutineScope()

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Job details (keep existing code)
            Text(
                text = job.job.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = job.job.location,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            job.job.salaryRange?.let { salary ->
                Text(
                    text = salary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Status-based UI
            when (job.status) {
                ApplicationStatus.SELECTED -> {
                    // Keep existing SELECTED logic
                    Column {
                        Text(
                            text = "Ready to Accept",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "You've been selected for this job",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = onAcceptJob,
                            enabled = !isLoading,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary
                            )
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onSecondary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Accepting...")
                            } else {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Accept Job")
                            }
                        }
                    }
                }

                ApplicationStatus.ACCEPTED -> {
                    // Keep existing ACCEPTED logic
                    Column {
                        Text(
                            text = "Job Accepted",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Get the OTP from your employer to start work",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = { showOtpDialog = true },
                            enabled = !isLoading,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Enter OTP to Start Work")
                        }
                    }
                }

                ApplicationStatus.WORK_IN_PROGRESS -> {
                    // 🚀 UPDATED: New completion workflow
                    Column {
                        Text(
                            text = "Work in Progress",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.tertiary,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Complete your work when finished",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = {
                                Log.d("SelectedJobCard", "🚀 Complete Work button clicked")
                                // 🚀 NEW: Updated completion workflow
                                coroutineScope.launch {
                                    try {
                                        isCompletionLoading = true
                                        completionError = null

                                        Log.d("SelectedJobCard", "🚀 Initiating completion for job: ${job.id}")

                                        // 🚀 NEW: Use the new initiate completion method
                                        val result = applicationRepository.initiateWorkCompletion(job.id)

                                        if (result.isSuccess) {
                                            Log.d("SelectedJobCard", "✅ Work completion initiated successfully")

                                            // Get the completion OTP
                                            val otpResult = applicationRepository.getCompletionOtp(job.id)
                                            if (otpResult.isSuccess) {
                                                completionOtp = otpResult.getOrNull() ?: ""
                                                showCompletionDialog = true
                                                Log.d("SelectedJobCard", "🔐 Completion OTP retrieved: $completionOtp")
                                            } else {
                                                completionError = "Failed to get completion OTP: ${otpResult.exceptionOrNull()?.message}"
                                                Log.e("SelectedJobCard", "❌ Failed to get completion OTP")
                                            }
                                        } else {
                                            val errorMessage = result.exceptionOrNull()?.message ?: "Failed to complete work"
                                            completionError = errorMessage
                                            Log.e("SelectedJobCard", "❌ Work completion failed: $errorMessage")
                                        }
                                    } catch (e: Exception) {
                                        val errorMessage = e.message ?: "Unknown error occurred"
                                        completionError = errorMessage
                                        Log.e("SelectedJobCard", "❌ Exception during completion: $errorMessage", e)
                                    } finally {
                                        isCompletionLoading = false
                                    }
                                }
                            },
                            enabled = !isLoading && !isCompletionLoading,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.tertiary
                            )
                        ) {
                            if (isCompletionLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onTertiary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Completing...")
                            } else {
                                Icon(Icons.Default.Check, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Complete Work")
                            }
                        }
                    }
                }

                else -> {
                    // Fallback for any other status
                    Text(
                        text = "Status: ${job.status}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Error message display
            errorMessage?.let { error ->
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            // 🚀 NEW: Show completion error if any
            completionError?.let { error ->
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }
    }

    // OTP Dialog for starting work (keep existing)
    if (showOtpDialog) {
        AlertDialog(
            onDismissRequest = {
                showOtpDialog = false
                otpInput = ""
            },
            title = { Text("Enter Work OTP") },
            text = {
                Column {
                    Text(
                        text = "Please enter the 6-digit OTP provided by your employer to start work.",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = otpInput,
                        onValueChange = {
                            if (it.length <= 6 && it.all { char -> char.isDigit() }) {
                                otpInput = it
                            }
                        },
                        label = { Text("6-digit OTP") },
                        placeholder = { Text("123456") },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        isError = errorMessage?.contains("OTP", ignoreCase = true) == true
                    )

                    if (errorMessage?.contains("OTP", ignoreCase = true) == true ||
                        errorMessage?.contains("expired", ignoreCase = true) == true) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = errorMessage,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (otpInput.length == 6) {
                            Log.d("SelectedJobCard", "🚀 Submitting OTP: '$otpInput'")
                            onStartWork(otpInput)
                            showOtpDialog = false
                            otpInput = ""
                        }
                    },
                    enabled = otpInput.length == 6 && !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text("Verify & Start")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showOtpDialog = false
                        otpInput = ""
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // 🚀 NEW: Completion OTP Dialog
    if (showCompletionDialog && completionOtp.isNotEmpty()) {
        CompletionOtpDialog(
            otp = completionOtp,
            jobTitle = job.job.title,
            workDuration = job.job.workDuration ?: "Duration calculated automatically",
            estimatedWages = "Payment will be calculated by employer",
            onDismiss = {
                showCompletionDialog = false
                completionOtp = ""
                completionError = null
            },
            onConfirmCompletion = {
                showCompletionDialog = false
                completionOtp = ""
                completionError = null
                // Completion is already done, this is just acknowledgment
                Log.d("SelectedJobCard", "✅ Employee confirmed work completion")
            }
        )
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnhancedEmployeeHomeTab(
    modifier: Modifier = Modifier,
    jobViewModel: JobViewModel,
    processedJobsViewModel: ProcessedJobsViewModel,
    jobHistoryViewModel: JobHistoryViewModel,
    jobRepository: JobRepository,
    onJobDetails: (String) -> Unit,
    onViewJobHistory: () -> Unit,
    onNavigateToJobListing: (String) -> Unit,
    onNavigateToDashboard: () -> Unit,
    hasActiveWork: Boolean = false,
    hasSelectedJobs: Boolean = false
) {
    val TAG = "EnhancedEmployeeHomeTab"

    // State collections
    val jobs by jobViewModel.jobs.collectAsStateWithLifecycle()
    val featuredJobs by jobViewModel.featuredJobs.collectAsStateWithLifecycle()
    val isLoading by jobViewModel.isLoading.collectAsStateWithLifecycle()
    val employeeProfile by jobViewModel.employeeProfile.collectAsStateWithLifecycle()
    val isShowingRejectedJobs by processedJobsViewModel.isShowingRejectedJobs.collectAsStateWithLifecycle()
    val processedJobIds by processedJobsViewModel.processedJobIds.collectAsStateWithLifecycle()
    val appliedJobIds by processedJobsViewModel.appliedJobIds.collectAsStateWithLifecycle()
    val rejectedJobIds by processedJobsViewModel.rejectedJobIds.collectAsStateWithLifecycle()

    // Local state
    var showJobAlertDialog by remember { mutableStateOf(false) }
    var showLocationSearchDialog by remember { mutableStateOf(false) }
    var showNewJobsSnackbar by remember { mutableStateOf(false) }
    var newJobsCount by remember { mutableStateOf(0) }
    var hasInitialized by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Create JobWithEmployer list
    val jobsWithEmployers = remember(featuredJobs) {
        featuredJobs.map { job ->
            val employerName = job.employerId.takeIf { it.isNotEmpty() }?.let { id ->
                "Employer ${id.takeLast(4)}"
            } ?: "Unknown Employer"
            JobWithEmployer(job, employerName)
        }
    }

    // Lifecycle management
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    Log.d(TAG, "Screen resumed - checking for updates")
                    coroutineScope.launch {
                        processedJobsViewModel.restoreSessionState()
                        val district = employeeProfile?.district ?: ""
                        if (district.isNotBlank()) {
                            jobViewModel.checkForNewJobs(district).collect { count ->
                                if (count > 0 && hasInitialized) {
                                    newJobsCount = count
                                    showNewJobsSnackbar = true
                                }
                            }
                        }
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Initial data loading
    LaunchedEffect(Unit) {
        jobViewModel.getEmployeeProfile()
    }

    LaunchedEffect(employeeProfile, isShowingRejectedJobs) {
        if (employeeProfile != null) {
            val employeeDistrict = employeeProfile?.district ?: ""
            if (employeeDistrict.isNotEmpty()) {
                if (isShowingRejectedJobs) {
                    Log.d(TAG, "Loading rejected jobs for district: $employeeDistrict")
                    jobViewModel.getOnlyRejectedJobs(employeeDistrict)
                } else {
                    Log.d(TAG, "Loading regular jobs for district: $employeeDistrict")
                    jobViewModel.getLocalizedFeaturedJobs(employeeDistrict, 10)
                }
                hasInitialized = true
            } else {
                jobViewModel.getFeaturedJobs(10)
            }
        } else {
            jobViewModel.getFeaturedJobs(10)
        }
    }

    // Show snackbar for new jobs
    LaunchedEffect(showNewJobsSnackbar) {
        if (showNewJobsSnackbar) {
            val result = snackbarHostState.showSnackbar(
                message = "$newJobsCount new job${if (newJobsCount > 1) "s" else ""} available!",
                actionLabel = "Refresh",
                duration = SnackbarDuration.Long
            )

            if (result == SnackbarResult.ActionPerformed) {
                val district = employeeProfile?.district ?: ""
                jobViewModel.refreshJobsForDistrict(district)
            }

            showNewJobsSnackbar = false
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (isShowingRejectedJobs) "Reconsidering Jobs" else "Job Opportunities",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        employeeProfile?.district?.let { district ->
                            Text(
                                text = "in $district",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showJobAlertDialog = true }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = "Job Alerts"
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                isLoading && featuredJobs.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Loading jobs...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                employeeProfile?.district == null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocationOff,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Location Not Set",
                                style = MaterialTheme.typography.headlineSmall,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Please complete your profile to see jobs in your area",
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(
                                onClick = onNavigateToDashboard
                            ) {
                                Icon(Icons.Default.Person, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Complete Profile")
                            }
                        }
                    }
                }

                else -> {
                    // 🚀 FIXED: Use LazyColumn for proper scrolling with swipeable cards
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Top spacing
                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        // Show active work message if user has active work
                        if (hasActiveWork && !isShowingRejectedJobs) {
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Info,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                        Text(
                                            text = "You have work in progress. Complete it before applying for new jobs.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                }
                            }
                        }

                        // Job History Button
                        item {
                            Button(
                                onClick = onViewJobHistory,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.History,
                                        contentDescription = "History",
                                        modifier = Modifier.padding(end = 8.dp)
                                    )
                                    Text("View Your Job History")
                                    if (appliedJobIds.isNotEmpty()) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Badge {
                                            Text(
                                                text = appliedJobIds.size.toString(),
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Swipe instruction text
                        item {
                            Text(
                                text = when {
                                    hasActiveWork -> "Complete your active work to apply for new jobs"
                                    hasSelectedJobs -> "Accept selected jobs above, then swipe to find more opportunities"
                                    else -> "Swipe right to apply, left to reject"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                                color = if (hasActiveWork)
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                else
                                    MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // 🚀 FIXED: Swipeable Job Cards in LazyColumn item with proper height
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(500.dp) // Fixed height for swipeable cards
                            ) {
                                SwipeableJobCards(
                                    jobs = featuredJobs,
                                    jobsWithEmployers = jobsWithEmployers,
                                    onJobAccepted = { job ->
                                        if (!hasActiveWork) {
                                            coroutineScope.launch {
                                                jobViewModel.applyForJob(job.id)
                                                snackbarHostState.showSnackbar(
                                                    message = "Applied for: ${job.title}"
                                                )
                                            }
                                        } else {
                                            coroutineScope.launch {
                                                snackbarHostState.showSnackbar(
                                                    message = "Complete your active work first",
                                                    duration = SnackbarDuration.Long
                                                )
                                            }
                                        }
                                    },
                                    onJobRejected = { job ->
                                        if (!hasActiveWork) {
                                            coroutineScope.launch {
                                                jobViewModel.markJobAsNotInterested(job.id)
                                                snackbarHostState.showSnackbar(
                                                    message = "Job rejected: ${job.title}"
                                                )
                                            }
                                        } else {
                                            coroutineScope.launch {
                                                snackbarHostState.showSnackbar(
                                                    message = "Complete your active work first",
                                                    duration = SnackbarDuration.Long
                                                )
                                            }
                                        }
                                    },
                                    onJobDetails = onJobDetails,
                                    modifier = Modifier.fillMaxSize(),
                                    isSwipeEnabled = !hasActiveWork
                                )
                            }
                        }

                        // Bottom spacing for FAB
                        item {
                            Spacer(modifier = Modifier.height(80.dp))
                        }
                    }
                }
            }

            // Floating action button - only show if not working and not in rejected jobs mode
            if (!isShowingRejectedJobs && featuredJobs.isNotEmpty() && !hasActiveWork) {
                ExtendedFloatingActionButton(
                    onClick = {
                        employeeProfile?.district?.let { district ->
                            onNavigateToJobListing(district)
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Icon(
                        imageVector = Icons.Default.List,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("View All Jobs")
                }
            }
        }
    }

    // Job Alert Dialog
    if (showJobAlertDialog) {
        JobAlertDialog(
            currentDistrict = employeeProfile?.district ?: "",
            onDismiss = { showJobAlertDialog = false },
            onCreateAlert = { alert ->
                coroutineScope.launch {
                    jobRepository.createJobAlert(alert).collect { result ->
                        if (result.isSuccess) {
                            snackbarHostState.showSnackbar(
                                message = "Job alert created successfully!",
                                duration = SnackbarDuration.Short
                            )
                            showJobAlertDialog = false
                        } else {
                            snackbarHostState.showSnackbar(
                                message = "Failed to create job alert",
                                duration = SnackbarDuration.Short
                            )
                        }
                    }
                }
            }
        )
    }

    // Location Search Dialog
    if (showLocationSearchDialog) {
        LocationSearchDialog(
            currentDistrict = employeeProfile?.district ?: "",
            onDismiss = { showLocationSearchDialog = false },
            onLocationSelected = { newDistrict ->
                showLocationSearchDialog = false
                onNavigateToJobListing(newDistrict)
            }
        )
    }
}

@Composable
fun OtpInputDialog(
    show: Boolean,
    otp: String,
    onOtpChange: (String) -> Unit,
    error: String?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!show) return

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enter OTP from Employer") },
        text = {
            Column {
                OutlinedTextField(
                    value = otp,
                    onValueChange = onOtpChange,
                    label = { Text("OTP") },
                    singleLine = true,
                    isError = error != null
                )
                if (error != null) {
                    Text(error, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = { Button(onClick = onConfirm) { Text("Verify OTP") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// Keep the existing EmployeeJobsTab and EmployeeProfileTab components unchanged
@Composable
fun EmployeeJobsTab(
    modifier: Modifier = Modifier,
    district: String,
    onJobSelected: (String) -> Unit
) {
    val jobViewModel: JobViewModel = hiltViewModel()
    val jobs by jobViewModel.jobs.collectAsState()
    val isLoading by jobViewModel.isLoading.collectAsState()

    LaunchedEffect(district) {
        if (district.isNotEmpty()) {
            jobViewModel.getJobsByDistrict(district)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        GigWorkHeaderText(text = "Jobs in $district")

        Spacer(modifier = Modifier.height(16.dp))

        if (isLoading) {
            CircularProgressIndicator()
        } else if (jobs.isEmpty()) {
            Text(
                text = "No jobs available in your area at the moment.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
        } else {
            LazyColumn {
                items(jobs) { job ->
                    JobItem(
                        job = job,
                        onClick = { onJobSelected(job.id) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
fun EmployeeProfileTab(
    modifier: Modifier = Modifier,
    profile: EmployeeProfile?
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        GigWorkHeaderText(text = "Your Profile")

        Spacer(modifier = Modifier.height(24.dp))

        if (profile == null) {
            CircularProgressIndicator()
        } else {
            // Profile photo
            if (profile.profilePhotoUrl != null) {
                AsyncImage(
                    model = profile.profilePhotoUrl,
                    contentDescription = "Profile Photo",
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(60.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = profile.name,
                style = MaterialTheme.typography.headlineSmall
            )

            profile.email?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            ProfileSection(
                title = "Personal Details",
                content = {
                    ProfileRow("Gender", profile.gender.toString())
                    ProfileRow("Location", "${profile.district}, ${profile.state}")
                    ProfileRow("Computer Knowledge", if (profile.hasComputerKnowledge) "Yes" else "No")
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            ProfileSection(
                title = "Work Preferences",
                content = {
                    ProfileRow("Work Types", profile.workPreferences.joinToString(", ") {
                        it.toString().replace("_", " ")
                    })
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { /* Navigate to edit profile */ },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Edit Profile")
            }
        }
    }
}

@Composable
fun ProfileSection(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                content()
            }
        }
    }
}

@Composable
fun ProfileRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

/**
 * Simple location search dialog
 */
@Composable
fun LocationSearchDialog(
    currentDistrict: String,
    onDismiss: () -> Unit,
    onLocationSelected: (String) -> Unit
) {
    val nearbyDistricts = remember {
        listOf(
            "Hisar", "Rohtak", "Karnal", "Panipat",
            "Sonipat", "Kaithal", "Kurukshetra", "Fatehabad",
            "Bhiwani", "Jhajjar", "Rewari", "Mahendragarh"
        ).filter { it != currentDistrict }.sorted()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Search Nearby Areas")
        },
        text = {
            Column {
                Text(
                    "Select a nearby district to search for jobs:",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    nearbyDistricts.chunked(2).forEach { row ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            row.forEach { district ->
                                SuggestionChip(
                                    onClick = { onLocationSelected(district) },
                                    label = { Text(district) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (row.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}