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
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.WorkOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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

// Fixed ApplicationItem in JobApplicationsScreen.kt - Consistent Status Display

@Composable
fun ApplicationItem(
    application: ApplicationWithJob,
    viewModel: EmployerApplicationsViewModel,
    onViewProfile: () -> Unit,
    onUpdateStatus: (String) -> Unit
) {
    var showStatusDialog by remember { mutableStateOf(false) }
    var showOtpDialog by remember { mutableStateOf(false) }
    var showCompletionDialog by remember { mutableStateOf(false) }
    var completionOtpInput by remember { mutableStateOf("") }
    var isVerifyingCompletion by remember { mutableStateOf(false) }
    var completionError by remember { mutableStateOf<String?>(null) }

    // 🚀 FIX: Use application-specific OTP data
    val applicationOtps by viewModel.applicationOtps.collectAsState()
    val applicationWorkSessions by viewModel.applicationWorkSessions.collectAsState()
    val applicationLoadingStates by viewModel.applicationLoadingStates.collectAsState()
    val applicationErrors by viewModel.applicationOtpErrors.collectAsState()

    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

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

    // Load work session for all relevant statuses
    LaunchedEffect(application.id) {
        if (application.status in listOf(
                ApplicationStatus.SELECTED,
                ApplicationStatus.WORK_IN_PROGRESS,
                ApplicationStatus.COMPLETION_PENDING
            ) && currentWorkSession == null) {
            viewModel.getWorkSessionForApplication(application.id)
        }
    }

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

                // 🚀 FIXED: Status chip with consistent enum-based mapping
                val (statusColor, statusText) = when (application.status) {
                    ApplicationStatus.APPLIED -> Pair(MaterialTheme.colorScheme.primary, "APPLIED")
                    ApplicationStatus.SELECTED -> Pair(MaterialTheme.colorScheme.secondary, "SELECTED")
                    ApplicationStatus.ACCEPTED -> Pair(MaterialTheme.colorScheme.tertiary, "ACCEPTED")
                    ApplicationStatus.WORK_IN_PROGRESS -> Pair(Color(0xFF009688), "WORKING")
                    ApplicationStatus.COMPLETION_PENDING -> Pair(Color(0xFFFF9800), "VERIFY COMPLETION")
                    ApplicationStatus.COMPLETED -> Pair(Color(0xFF4CAF50), "COMPLETED")
                    ApplicationStatus.REJECTED -> Pair(MaterialTheme.colorScheme.error, "REJECTED")
                    ApplicationStatus.DECLINED -> Pair(MaterialTheme.colorScheme.error, "DECLINED")
                    ApplicationStatus.NOT_INTERESTED -> Pair(MaterialTheme.colorScheme.outline, "NOT INTERESTED")
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

            // 🚀 UPDATED: Status-based action buttons
            when (application.status) {
                ApplicationStatus.SELECTED -> {
                    // Existing SELECTED logic...
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        TextButton(
                            onClick = onViewProfile,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Profile", style = MaterialTheme.typography.bodySmall)
                        }

                        TextButton(
                            onClick = { /* Navigate to job details */ },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.WorkOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Job", style = MaterialTheme.typography.bodySmall)
                        }

                        TextButton(
                            onClick = { showStatusDialog = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Update, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Change Status", style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    // OTP Generation Section
                    Spacer(modifier = Modifier.height(8.dp))
                    if (currentOtp != null) {
                        TextButton(
                            onClick = { showOtpDialog = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.secondary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("View OTP", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        }
                    } else {
                        TextButton(
                            onClick = { viewModel.generateOtpSilently(application.id) },
                            enabled = !isCurrentlyLoading,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isCurrentlyLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Generating...")
                            } else {
                                Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Generate OTP", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            }
                        }
                    }

                    // OTP Display when available
                    if (currentOtp != null && currentWorkSession != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Work Session OTP", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
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
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Timer, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Expires in 30 minutes", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }

                ApplicationStatus.WORK_IN_PROGRESS -> {
                    // 🚀 FIXED: Show work in progress status
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        TextButton(
                            onClick = onViewProfile,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Profile", style = MaterialTheme.typography.bodySmall)
                        }

                        TextButton(
                            onClick = { /* Navigate to job details */ },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.WorkOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Job", style = MaterialTheme.typography.bodySmall)
                        }

                        TextButton(
                            onClick = {},
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Timer, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFF009688))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Working", style = MaterialTheme.typography.bodySmall, color = Color(0xFF009688))
                        }
                    }

                    // Show work progress info
                    currentWorkSession?.let { session ->
                        Spacer(modifier = Modifier.height(12.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF009688).copy(alpha = 0.1f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "Work in Progress",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF009688)
                                )
                                session.workStartTime?.let { startTime ->
                                    Text(
                                        text = "Started: ${DateUtils.formatDate(startTime)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = "Employee is currently working on this job",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                ApplicationStatus.COMPLETION_PENDING -> {
                    // 🚀 COMPLETION VERIFICATION UI
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        TextButton(
                            onClick = onViewProfile,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Profile", style = MaterialTheme.typography.bodySmall)
                        }

                        TextButton(
                            onClick = { /* Navigate to job details */ },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.WorkOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Job", style = MaterialTheme.typography.bodySmall)
                        }

                        TextButton(
                            onClick = {
                                showCompletionDialog = true
                                completionOtpInput = ""
                                completionError = null
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !isVerifyingCompletion
                        ) {
                            if (isVerifyingCompletion) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.VerifiedUser, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFFFF9800))
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Verify", style = MaterialTheme.typography.bodySmall, color = Color(0xFFFF9800))
                        }
                    }

                    // Show completion pending info
                    Spacer(modifier = Modifier.height(12.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFF9800).copy(alpha = 0.1f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.HourglassTop, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFFFF9800))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Work Completion Pending Verification",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFF9800)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Employee has completed work and provided a completion code. Verify the work quality and enter the completion code to finalize payment.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            // Show work session details if available
                            currentWorkSession?.let { session ->
                                Spacer(modifier = Modifier.height(8.dp))
                                session.workDurationMinutes?.let { duration ->
                                    Text(
                                        text = "Work Duration: ${session.formattedWorkDuration}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                session.calculatedWages?.let { wages ->
                                    Text(
                                        text = "Estimated Payment: ₹${String.format("%.2f", wages)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }

                ApplicationStatus.COMPLETED -> {
                    // Show completed status
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        TextButton(
                            onClick = onViewProfile,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Profile", style = MaterialTheme.typography.bodySmall)
                        }

                        TextButton(
                            onClick = { /* Navigate to job details */ },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.WorkOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Job", style = MaterialTheme.typography.bodySmall)
                        }

                        TextButton(
                            onClick = {},
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFF4CAF50))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Completed", style = MaterialTheme.typography.bodySmall, color = Color(0xFF4CAF50))
                        }
                    }

                    // Show completion summary
                    currentWorkSession?.let { session ->
                        Spacer(modifier = Modifier.height(12.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF4CAF50).copy(alpha = 0.1f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "Work Completed Successfully",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF4CAF50)
                                )
                                session.workDurationMinutes?.let { duration ->
                                    Text(
                                        text = "Duration: ${session.formattedWorkDuration}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                session.totalWagesCalculated?.let { wages ->
                                    Text(
                                        text = "Total Payment: ₹$wages",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }

                else -> {
                    // Default action buttons for other statuses
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        TextButton(
                            onClick = onViewProfile,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Profile", style = MaterialTheme.typography.bodySmall)
                        }

                        TextButton(
                            onClick = { /* Navigate to job details */ },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.WorkOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Job", style = MaterialTheme.typography.bodySmall)
                        }

                        TextButton(
                            onClick = { showStatusDialog = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Update, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Update", style = MaterialTheme.typography.bodySmall)
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

            // Show completion verification error
            completionError?.let { error ->
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(16.dp))
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

    // OTP Display Dialog - only shows when explicitly requested
    if (showOtpDialog && currentOtp != null) {
        OtpDisplayDialog(
            otp = currentOtp,
            employeeName = "Applicant ${application.employeeId.takeLast(5)}",
            jobTitle = application.job.title,
            expiryTime = currentWorkSession?.otpExpiry,
            onDismiss = { showOtpDialog = false },
            onRegenerateOtp = {
                showOtpDialog = false
                viewModel.generateOtpSilently(application.id)
            }
        )
    }

    // 🚀 NEW: Work Completion Verification Dialog
    if (showCompletionDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!isVerifyingCompletion) {
                    showCompletionDialog = false
                    completionOtpInput = ""
                    completionError = null
                }
            },
            title = { Text("Verify Work Completion") },
            text = {
                Column {
                    Text(
                        text = "Employee ${application.employeeId.takeLast(5)} has completed work for: ${application.job.title}",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = completionOtpInput,
                        onValueChange = { input ->
                            if (input.length <= 6 && input.all { it.isDigit() }) {
                                completionOtpInput = input
                                completionError = null
                            }
                        },
                        label = { Text("Enter 6-digit completion code") },
                        placeholder = { Text("123456") },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        isError = completionError != null,
                        enabled = !isVerifyingCompletion
                    )

                    completionError?.let { error ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Text(
                            text = "Ask the employee for the 6-digit completion code they received after finishing work.",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(8.dp)
                        )
                    }

                    // Show work details if available
                    currentWorkSession?.let { session ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(
                                    text = "Work Summary:",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                session.workDurationMinutes?.let { duration ->
                                    Text(
                                        text = "Duration: ${session.formattedWorkDuration}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                session.calculatedWages?.let { wages ->
                                    Text(
                                        text = "Estimated Payment: ₹${String.format("%.2f", wages)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (completionOtpInput.length == 6) {
                            isVerifyingCompletion = true
                            completionError = null

                            coroutineScope.launch {
                                try {
                                    val result = viewModel.verifyWorkCompletionOtp(application.id, completionOtpInput)

                                    if (result.isSuccess) {
                                        val message = result.getOrNull() ?: "Work completed successfully!"
                                        snackbarHostState.showSnackbar(message)
                                        showCompletionDialog = false
                                        // Refresh the applications list
                                    } else {
                                        completionError = result.exceptionOrNull()?.message ?: "Verification failed"
                                    }
                                } catch (e: Exception) {
                                    completionError = "Error: ${e.message}"
                                } finally {
                                    isVerifyingCompletion = false
                                }
                            }
                        }
                    },
                    enabled = completionOtpInput.length == 6 && !isVerifyingCompletion
                ) {
                    if (isVerifyingCompletion) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Verifying...")
                    } else {
                        Text("Verify & Complete")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        if (!isVerifyingCompletion) {
                            showCompletionDialog = false
                            completionOtpInput = ""
                            completionError = null
                        }
                    },
                    enabled = !isVerifyingCompletion
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // Add SnackbarHost for completion messages
    SnackbarHost(hostState = snackbarHostState)
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
// Fixed ApplicationItemWithJob in JobApplicationsScreen.kt - Consistent Status Display

@Composable
fun ApplicationItemWithJob(
    application: ApplicationWithJob,
    viewModel: EmployerApplicationsViewModel,
    onViewProfile: () -> Unit,
    onViewJob: () -> Unit,
    onUpdateStatus: (String) -> Unit
) {
    var showStatusDialog by remember { mutableStateOf(false) }
    var showOtpDialog by remember { mutableStateOf(false) }

    // Use application-specific OTP data
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

                // 🚀 FIXED: Status chip with consistent enum-based mapping instead of string-based
                val (statusColor, statusText) = when (application.status) {
                    ApplicationStatus.APPLIED -> Pair(MaterialTheme.colorScheme.primary, "APPLIED")
                    ApplicationStatus.SELECTED -> Pair(MaterialTheme.colorScheme.secondary, "SELECTED")
                    ApplicationStatus.ACCEPTED -> Pair(MaterialTheme.colorScheme.tertiary, "ACCEPTED")
                    ApplicationStatus.WORK_IN_PROGRESS -> Pair(Color(0xFF009688), "WORKING")
                    ApplicationStatus.COMPLETION_PENDING -> Pair(Color(0xFFFF9800), "VERIFY COMPLETION")
                    ApplicationStatus.COMPLETED -> Pair(Color(0xFF4CAF50), "COMPLETED")
                    ApplicationStatus.REJECTED -> Pair(MaterialTheme.colorScheme.error, "REJECTED")
                    ApplicationStatus.DECLINED -> Pair(MaterialTheme.colorScheme.error, "DECLINED")
                    ApplicationStatus.NOT_INTERESTED -> Pair(MaterialTheme.colorScheme.outline, "NOT INTERESTED")
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
                    Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Profile", style = MaterialTheme.typography.bodySmall)
                }

                TextButton(onClick = onViewJob) {
                    Icon(Icons.Default.WorkOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Job", style = MaterialTheme.typography.bodySmall)
                }

                // 🚀 FIXED: Status-specific action buttons
                when (application.status) {
                    ApplicationStatus.SELECTED -> {
                        if (currentOtp != null) {
                            TextButton(onClick = { showOtpDialog = true }) {
                                Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("OTP", style = MaterialTheme.typography.bodySmall)
                            }
                        } else {
                            TextButton(
                                onClick = { viewModel.generateOtpSilently(application.id) },
                                enabled = !isCurrentlyLoading
                            ) {
                                if (isCurrentlyLoading) {
                                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Gen OTP", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    ApplicationStatus.WORK_IN_PROGRESS -> {
                        TextButton(onClick = {}) {
                            Icon(Icons.Default.Timer, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFF009688))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Working", style = MaterialTheme.typography.bodySmall, color = Color(0xFF009688))
                        }
                    }
                    ApplicationStatus.COMPLETION_PENDING -> {
                        TextButton(onClick = {}) {
                            Icon(Icons.Default.HourglassTop, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFFFF9800))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Verify", style = MaterialTheme.typography.bodySmall, color = Color(0xFFFF9800))
                        }
                    }
                    ApplicationStatus.COMPLETED -> {
                        TextButton(onClick = {}) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFF4CAF50))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Done", style = MaterialTheme.typography.bodySmall, color = Color(0xFF4CAF50))
                        }
                    }
                    else -> {
                        TextButton(onClick = { showStatusDialog = true }) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
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

    // OTP Display Dialog - only shows when explicitly requested
    if (showOtpDialog && currentOtp != null) {
        OtpDisplayDialog(
            otp = currentOtp,
            employeeName = "Applicant ${application.employeeId.takeLast(5)}",
            jobTitle = application.job.title,
            expiryTime = currentWorkSession?.otpExpiry,
            onDismiss = { showOtpDialog = false },
            onRegenerateOtp = {
                showOtpDialog = false
                viewModel.generateOtpSilently(application.id)
            }
        )
    }
}