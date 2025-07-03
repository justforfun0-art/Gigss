package com.example.gigs.ui.screens.jobs

import android.util.Log
import com.example.gigs.viewmodel.EmployerApplicationsViewModel
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.WorkOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.gigs.data.model.ApplicationStatus
import com.example.gigs.data.model.ApplicationWithJob
import com.example.gigs.ui.components.GigWorkHeaderText
import com.example.gigs.ui.components.OtpDisplayDialog
import com.example.gigs.utils.DateUtils
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Screen to display applications for a specific job
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobApplicationsScreen(
    viewModel: EmployerApplicationsViewModel = hiltViewModel(),
    jobId: String,
    jobTitle: String,
    onBackPressed: () -> Unit,
    onViewApplicantProfile: (String) -> Unit
) {
    val applications by viewModel.recentApplications.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Load applications for this job
    LaunchedEffect(jobId) {
        viewModel.loadApplicationsForJob(jobId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Applications for $jobTitle") },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            } else if (applications.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        modifier = Modifier.size(80.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    GigWorkHeaderText(text = "No Applications Yet")

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "When candidates apply for this job, they'll appear here",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                        Text(
                            text = "${applications.size} Applications",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    items(applications) { application ->
                        ApplicationItem(
                            application = application,
                            viewModel=viewModel,
                            onViewProfile = { onViewApplicantProfile(application.employeeId) },
                            onUpdateStatus = { newStatus ->
                                scope.launch {
                                    viewModel.updateApplicationStatus(application.id, newStatus)
                                    snackbarHostState.showSnackbar("Status updated to $newStatus")
                                }
                            }
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

/**
 * Screen to display all applications across all jobs
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllApplicationsScreen(
    viewModel: EmployerApplicationsViewModel = hiltViewModel(),
    onBackPressed: () -> Unit,
    onViewApplicantProfile: (String) -> Unit,
    onViewJob: (String) -> Unit
) {
    val applications by viewModel.recentApplications.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Load all applications
    LaunchedEffect(Unit) {
        viewModel.loadRecentApplications(50) // Load up to 50 applications
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("All Applications") },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            } else if (applications.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        modifier = Modifier.size(80.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    GigWorkHeaderText(text = "No Applications Yet")

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Your job applications will appear here",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                        Text(
                            text = "${applications.size} Applications",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    items(applications) { application ->
                        ApplicationItemWithJob(
                            application = application,
                            viewModel=viewModel,
                            onViewProfile = { onViewApplicantProfile(application.employeeId) },
                            onViewJob = { onViewJob(application.jobId) },
                            onUpdateStatus = { newStatus ->
                                scope.launch {
                                    viewModel.updateApplicationStatus(application.id, newStatus)
                                    snackbarHostState.showSnackbar("Status updated to $newStatus")
                                }
                            }
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

// Update your ApplicationItem composable with OTP functionality

// Update your ApplicationItem composable with OTP functionality

@Composable
fun ApplicationItem(
    application: ApplicationWithJob,
    viewModel: EmployerApplicationsViewModel,
    onViewProfile: () -> Unit,
    onUpdateStatus: (String) -> Unit
) {
    var showStatusDialog by remember { mutableStateOf(false) }
    // 🚀 CRITICAL FIX: Use local state for OTP dialog instead of ViewModel state
    var showOtpDialog by remember { mutableStateOf(false) }

    // 🚀 FIX: Use application-specific OTP data
    val applicationOtps by viewModel.applicationOtps.collectAsState()
    val applicationWorkSessions by viewModel.applicationWorkSessions.collectAsState()
    val applicationLoadingStates by viewModel.applicationLoadingStates.collectAsState()
    val applicationErrors by viewModel.applicationOtpErrors.collectAsState()

    // Get data for this specific application
    val currentOtp = remember(applicationOtps, application.id) {
        applicationOtps[application.id]?.also {
            Log.d("ApplicationItem", "🔍 Current OTP for ${application.id}: $it")
        }
    }

    val currentWorkSession = remember(applicationWorkSessions, application.id) {
        applicationWorkSessions[application.id]?.also {
            Log.d("ApplicationItem", "🔍 Current work session for ${application.id}: ${it.status}")
        }
    }

    val isCurrentlyLoading = remember(applicationLoadingStates, application.id) {
        applicationLoadingStates[application.id] ?: false
    }

    val currentError = remember(applicationErrors, application.id) {
        applicationErrors[application.id]
    }

    // Load work session only when needed
    LaunchedEffect(application.id) {
        if (application.status == ApplicationStatus.SELECTED && currentWorkSession == null) {
            viewModel.getWorkSessionForApplication(application.id)
        }
    }

    // 🚀 CRITICAL FIX: Remove automatic dialog triggering based on OTP changes
    // LaunchedEffect(currentOtp) - REMOVED to prevent auto-popup

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Applicant info and status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = application.employeeId.take(1).uppercase(),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = "Applicant ${application.employeeId.takeLast(5)}",
                            style = MaterialTheme.typography.titleMedium
                        )

                        Text(
                            text = "Applied ${application.appliedAt?.let { DateUtils.formatDate(it) } ?: "Recently"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Status chip
                val (statusColor, statusText) = when (application.status) {
                    ApplicationStatus.APPLIED -> Pair(MaterialTheme.colorScheme.primary, "APPLIED")
                    ApplicationStatus.SELECTED -> Pair(MaterialTheme.colorScheme.secondary, "SELECTED")
                    ApplicationStatus.REJECTED -> Pair(MaterialTheme.colorScheme.error, "REJECTED")
                    ApplicationStatus.WORK_IN_PROGRESS -> Pair(Color(0xFF009688), "WORKING")
                    ApplicationStatus.COMPLETED -> Pair(Color(0xFF4CAF50), "COMPLETED")
                    else -> Pair(MaterialTheme.colorScheme.outline, application.status.toString())
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(statusColor.copy(alpha = 0.1f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = statusColor,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // First row: Profile, Job, Change Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                TextButton(
                    onClick = onViewProfile,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Profile", style = MaterialTheme.typography.bodySmall)
                }

                TextButton(
                    onClick = { /* Navigate to job details */ },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.WorkOutline,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Job", style = MaterialTheme.typography.bodySmall)
                }

                TextButton(
                    onClick = { showStatusDialog = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Update,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Change Status", style = MaterialTheme.typography.bodySmall)
                }
            }

            // Second row: Generate OTP (only for SELECTED status)
            if (application.status == ApplicationStatus.SELECTED) {
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (currentOtp != null) {
                        TextButton(
                            onClick = {
                                Log.d("ApplicationItem", "📱 View OTP clicked for ${application.id}")
                                showOtpDialog = true // 🚀 Use local state
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "View OTP",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    } else {
                        TextButton(
                            onClick = {
                                Log.d("ApplicationItem", "🚀 Generate OTP clicked for ${application.id}")
                                // 🚀 FIXED: Generate OTP without auto-showing dialog
                                viewModel.generateOtpSilently(application.id)
                            },
                            enabled = !isCurrentlyLoading,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isCurrentlyLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Generating...")
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Generate OTP",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            // Third row: OTP display when generated (inline display)
            if (application.status == ApplicationStatus.SELECTED && currentOtp != null && currentWorkSession != null) {
                Spacer(modifier = Modifier.height(12.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Work Session OTP",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = currentOtp,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 4.sp
                            )
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Timer,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "Expires in 30 minutes",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Show OTP error if any for this application
            currentError?.let { error ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }

    // Status update dialog (simplified with only SELECTED or REJECTED options)
    if (showStatusDialog) {
        Dialog(onDismissRequest = { showStatusDialog = false }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text(
                        text = "Update Application Status",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Choose the appropriate status for this application:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // SELECTED option
                    EmployerApplicationStatusOption(
                        status = "SELECTED",
                        displayText = "Selected",
                        description = "Candidate has been selected for work - OTP will be generated",
                        currentStatus = application.status.name,
                        isPrimary = true,
                        onSelect = {
                            onUpdateStatus("SELECTED")
                            showStatusDialog = false
                            // 🚀 FIXED: Generate OTP silently after selecting
                            viewModel.generateOtpSilently(application.id)
                        }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // REJECTED option
                    EmployerApplicationStatusOption(
                        status = "REJECTED",
                        displayText = "Rejected",
                        description = "Application not suitable for this position",
                        currentStatus = application.status.name,
                        isRejection = true,
                        onSelect = {
                            onUpdateStatus("REJECTED")
                            showStatusDialog = false
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    TextButton(
                        onClick = { showStatusDialog = false },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Cancel")
                    }
                }
            }
        }
    }

    // 🚀 FIXED: OTP Display Dialog - only shows when explicitly requested
    if (showOtpDialog && currentOtp != null) {
        OtpDisplayDialog(
            otp = currentOtp,
            employeeName = "Applicant ${application.employeeId.takeLast(5)}",
            jobTitle = application.job.title,
            expiryTime = currentWorkSession?.otpExpiry,
            onDismiss = {
                showOtpDialog = false // 🚀 Use local state
            },
            onRegenerateOtp = {
                showOtpDialog = false // 🚀 Use local state
                viewModel.generateOtpSilently(application.id)
            }
        )
    }
}
/**
 * Status option component for employer application updates
 */
@Composable
fun EmployerApplicationStatusOption(
    status: String,
    displayText: String,
    description: String,
    currentStatus: String,
    isPrimary: Boolean = false,
    isRejection: Boolean = false,
    onSelect: () -> Unit
) {
    val isSelected = status.equals(currentStatus, ignoreCase = true)

    val (statusColor, backgroundColor) = when {
        isRejection -> Pair(
            MaterialTheme.colorScheme.error,
            MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
        )
        isPrimary -> Pair(
            MaterialTheme.colorScheme.secondary,
            MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f)
        )
        else -> Pair(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
        )
    }

    Surface(
        onClick = onSelect,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        color = if (isSelected) backgroundColor else Color.Transparent,
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) statusColor else MaterialTheme.colorScheme.outline),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.surface
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = displayText,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) statusColor else MaterialTheme.colorScheme.onSurface
                    )

                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// 🚀 COMPLETE FIXED VERSION: Replace your ApplicationItemWithJob composable

// Replace your existing ApplicationItemWithJob with this updated version

@Composable
fun ApplicationItemWithJob(
    application: ApplicationWithJob,
    viewModel: EmployerApplicationsViewModel,
    onViewProfile: () -> Unit,
    onViewJob: () -> Unit,
    onUpdateStatus: (String) -> Unit
) {
    var showStatusDialog by remember { mutableStateOf(false) }
    // 🚀 CRITICAL FIX: Use local state for dialog control
    var showOtpDialog by remember { mutableStateOf(false) }

    // 🚀 FIX: Use application-specific OTP data
    val applicationOtps by viewModel.applicationOtps.collectAsState()
    val applicationWorkSessions by viewModel.applicationWorkSessions.collectAsState()
    val applicationLoadingStates by viewModel.applicationLoadingStates.collectAsState()
    val applicationErrors by viewModel.applicationOtpErrors.collectAsState()

    // Get data for this specific application
    val currentOtp = remember(applicationOtps, application.id) {
        applicationOtps[application.id]
    }
    val currentWorkSession = remember(applicationWorkSessions, application.id) {
        applicationWorkSessions[application.id]
    }
    val isCurrentlyLoading = remember(applicationLoadingStates, application.id) {
        applicationLoadingStates[application.id] ?: false
    }
    val currentError = remember(applicationErrors, application.id) {
        applicationErrors[application.id]
    }

    // Get work session when component loads
    LaunchedEffect(application.id) {
        if (application.status == ApplicationStatus.SELECTED && currentWorkSession == null) {
            viewModel.getWorkSessionForApplication(application.id)
        }
    }

    // 🚀 CRITICAL FIX: Remove automatic dialog triggering
    // LaunchedEffect(currentOtp) - REMOVED to prevent auto-popup

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Applicant info and date
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = application.employeeId.take(1).uppercase(),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = "Applicant ${application.employeeId.takeLast(5)}",
                            style = MaterialTheme.typography.titleMedium
                        )

                        Text(
                            text = "Applied ${application.appliedAt?.let { DateUtils.formatDate(it) } ?: "Recently"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Status chip with work-related statuses
                val (statusColor, statusText) = when (application.status.toString().uppercase(Locale.ROOT)) {
                    "APPLIED" -> Pair(MaterialTheme.colorScheme.primary, "APPLIED")
                    "REVIEWING", "UNDER_REVIEW" -> Pair(MaterialTheme.colorScheme.tertiary, "REVIEWING")
                    "SHORTLISTED" -> Pair(MaterialTheme.colorScheme.tertiary, "SHORTLISTED")
                    "INTERVIEW", "INTERVIEW_SCHEDULED" -> Pair(MaterialTheme.colorScheme.tertiary, "INTERVIEW")
                    "REJECTED", "DECLINED" -> Pair(MaterialTheme.colorScheme.error, "REJECTED")
                    "HIRED", "ACCEPTED", "SELECTED" -> Pair(MaterialTheme.colorScheme.secondary, "HIRED")
                    "WORK_IN_PROGRESS" -> Pair(Color(0xFF009688), "WORKING")
                    "COMPLETED" -> Pair(Color(0xFF4CAF50), "COMPLETED")
                    else -> Pair(MaterialTheme.colorScheme.outline, "PENDING")
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(statusColor.copy(alpha = 0.1f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = statusColor
                    )
                }
            }

            // Job title
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Job: ${application.jobTitle}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Location: ${application.jobDistrict}, ${application.jobState}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            // Actions row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = onViewProfile) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Profile", style = MaterialTheme.typography.bodySmall)
                }

                TextButton(onClick = onViewJob) {
                    Icon(
                        imageVector = Icons.Default.WorkOutline,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Job", style = MaterialTheme.typography.bodySmall)
                }

                // OTP or Update button based on status
                when (application.status) {
                    ApplicationStatus.SELECTED -> {
                        if (currentOtp != null) {
                            TextButton(onClick = {
                                showOtpDialog = true // 🚀 Use local state
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("OTP", style = MaterialTheme.typography.bodySmall)
                            }
                        } else {
                            TextButton(
                                onClick = {
                                    // 🚀 FIXED: Generate OTP without auto-showing dialog
                                    viewModel.generateOtpSilently(application.id)
                                },
                                enabled = !isCurrentlyLoading
                            ) {
                                if (isCurrentlyLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Gen OTP", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    ApplicationStatus.WORK_IN_PROGRESS -> {
                        TextButton(onClick = {}) {
                            Icon(
                                imageVector = Icons.Default.Timer,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = Color(0xFF009688)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Working", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    ApplicationStatus.COMPLETED -> {
                        TextButton(onClick = {}) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = Color(0xFF4CAF50)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Done", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    else -> {
                        TextButton(onClick = { showStatusDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Update", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            // Show OTP error if any
            currentError?.let { error ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }

    // Status update dialog
    if (showStatusDialog) {
        Dialog(onDismissRequest = { showStatusDialog = false }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text(
                        text = "Update Application Status",
                        style = MaterialTheme.typography.titleLarge
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    EmployerApplicationStatusOption(
                        status = "REVIEWING",
                        displayText = "Under Review",
                        description = "Application is being reviewed",
                        currentStatus = application.status.name,
                        onSelect = {
                            onUpdateStatus("REVIEWING")
                            showStatusDialog = false
                        }
                    )

                    EmployerApplicationStatusOption(
                        status = "SHORTLISTED",
                        displayText = "Shortlisted",
                        description = "Candidate looks promising",
                        currentStatus = application.status.name,
                        onSelect = {
                            onUpdateStatus("SHORTLISTED")
                            showStatusDialog = false
                        }
                    )

                    EmployerApplicationStatusOption(
                        status = "INTERVIEW_SCHEDULED",
                        displayText = "Interview Scheduled",
                        description = "Ready for interview",
                        currentStatus = application.status.name,
                        onSelect = {
                            onUpdateStatus("INTERVIEW_SCHEDULED")
                            showStatusDialog = false
                        }
                    )

                    EmployerApplicationStatusOption(
                        status = "HIRED",
                        displayText = "Hired / Selected",
                        description = "Candidate has been hired - OTP will be generated",
                        currentStatus = application.status.name,
                        isPrimary = true,
                        onSelect = {
                            onUpdateStatus("HIRED")
                            showStatusDialog = false
                            // 🚀 FIXED: After updating to HIRED, generate OTP silently
                            viewModel.generateOtpSilently(application.id)
                        }
                    )

                    EmployerApplicationStatusOption(
                        status = "REJECTED",
                        displayText = "Rejected",
                        description = "Application not suitable",
                        currentStatus = application.status.name,
                        isRejection = true,
                        onSelect = {
                            onUpdateStatus("REJECTED")
                            showStatusDialog = false
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    TextButton(
                        onClick = { showStatusDialog = false },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Cancel")
                    }
                }
            }
        }
    }

    // 🚀 FIXED: OTP Display Dialog - only shows when explicitly requested
    if (showOtpDialog && currentOtp != null) {
        OtpDisplayDialog(
            otp = currentOtp,
            employeeName = "Applicant ${application.employeeId.takeLast(5)}",
            jobTitle = application.job.title,
            expiryTime = currentWorkSession?.otpExpiry,
            onDismiss = {
                showOtpDialog = false // 🚀 Use local state
            },
            onRegenerateOtp = {
                showOtpDialog = false // 🚀 Use local state
                viewModel.generateOtpSilently(application.id)
            }
        )
    }
}