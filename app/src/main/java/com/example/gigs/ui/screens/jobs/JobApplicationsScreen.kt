package com.example.gigs.ui.screens.jobs

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
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.gigs.data.model.ApplicationWithJob
import com.example.gigs.ui.components.GigWorkHeaderText
import com.example.gigs.utils.DateUtils
import com.example.gigs.viewmodel.EmployerApplicationsViewModel
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

@Composable
fun ApplicationItem(
    application: ApplicationWithJob,
    onViewProfile: () -> Unit,
    onUpdateStatus: (String) -> Unit
) {
    var showStatusDialog by remember { mutableStateOf(false) }

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

                // Status chip
                val (statusColor, statusText) = when (application.status.toString().uppercase(Locale.ROOT)) {
                    "APPLIED" -> Pair(MaterialTheme.colorScheme.primary, "APPLIED")
                    "SHORTLISTED" -> Pair(MaterialTheme.colorScheme.tertiary, "SHORTLISTED")
                    "REJECTED" -> Pair(MaterialTheme.colorScheme.error, "REJECTED")
                    "HIRED" -> Pair(MaterialTheme.colorScheme.secondary, "HIRED")
                    else -> Pair(
                        MaterialTheme.colorScheme.outline,
                        application.status?.toString()?.uppercase(Locale.ROOT) ?: "UNKNOWN"
                    )
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

            Spacer(modifier = Modifier.height(16.dp))

            // Actions row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = onViewProfile) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    Text("View Profile")
                }

                TextButton(onClick = { showStatusDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    Text("Update Status")
                }
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

                    ApplicationStatusOption(
                        status = "APPLIED",
                        currentStatus = application.status.name, // Changed to .name
                        onSelect = {
                            onUpdateStatus("APPLIED")
                            showStatusDialog = false
                        }
                    )

                    ApplicationStatusOption(
                        status = "SHORTLISTED",
                        currentStatus = application.status.name,
                        onSelect = {
                            onUpdateStatus("SHORTLISTED")
                            showStatusDialog = false
                        }
                    )

                    ApplicationStatusOption(
                        status = "HIRED",
                        currentStatus = application.status.name,
                        onSelect = {
                            onUpdateStatus("HIRED")
                            showStatusDialog = false
                        }
                    )

                    ApplicationStatusOption(
                        status = "REJECTED",
                        currentStatus = application.status.name,
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
}

@Composable
fun ApplicationItemWithJob(
    application: ApplicationWithJob,
    onViewProfile: () -> Unit,
    onViewJob: () -> Unit,
    onUpdateStatus: (String) -> Unit
) {
    var showStatusDialog by remember { mutableStateOf(false) }

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

                // Status chip
                val (statusColor, statusText) = when (application.status.toString().uppercase(Locale.ROOT)) {
                    "APPLIED" -> Pair(MaterialTheme.colorScheme.primary, "APPLIED")
                    "SHORTLISTED" -> Pair(MaterialTheme.colorScheme.tertiary, "SHORTLISTED")
                    "REJECTED" -> Pair(MaterialTheme.colorScheme.error, "REJECTED")
                    "HIRED" -> Pair(MaterialTheme.colorScheme.secondary, "HIRED")
                    else -> Pair(
                        MaterialTheme.colorScheme.outline,
                        application.status?.toString()?.uppercase(Locale.ROOT) ?: "UNKNOWN"
                    )
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

                    Text("View Profile")
                }

                TextButton(onClick = onViewJob) {
                    Icon(
                        imageVector = Icons.Default.WorkOutline,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    Text("View Job")
                }

                TextButton(onClick = { showStatusDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    Text("Update")
                }
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

                    ApplicationStatusOption(
                        status = "APPLIED",
                        currentStatus = application.status.name,
                        onSelect = {
                            onUpdateStatus("APPLIED")
                            showStatusDialog = false
                        }
                    )

                    ApplicationStatusOption(
                        status = "SHORTLISTED",
                        currentStatus = application.status.name,
                        onSelect = {
                            onUpdateStatus("SHORTLISTED")
                            showStatusDialog = false
                        }
                    )

                    ApplicationStatusOption(
                        status = "HIRED",
                        currentStatus = application.status.name,
                        onSelect = {
                            onUpdateStatus("HIRED")
                            showStatusDialog = false
                        }
                    )

                    ApplicationStatusOption(
                        status = "REJECTED",
                        currentStatus = application.status.name,
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
}

@Composable
fun ApplicationStatusOption(
    status: String,
    currentStatus: String,
    onSelect: () -> Unit
) {
    val isSelected = status.equals(currentStatus, ignoreCase = true)

    val (statusColor, backgroundColor) = when (status) {
        "APPLIED" -> Pair(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
        "SHORTLISTED" -> Pair(MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.1f))
        "REJECTED" -> Pair(MaterialTheme.colorScheme.error, MaterialTheme.colorScheme.error.copy(alpha = 0.1f))
        "HIRED" -> Pair(MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f))
        else -> Pair(MaterialTheme.colorScheme.outline, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
    }

    Surface(
        onClick = onSelect,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        color = if (isSelected) backgroundColor else Color.Transparent,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) statusColor else Color.Transparent)
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = status,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isSelected) statusColor else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}