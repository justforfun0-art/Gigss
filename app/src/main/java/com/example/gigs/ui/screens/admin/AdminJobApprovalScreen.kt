package com.example.gigs.ui.screens.admin


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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.gigs.data.model.Job
import com.example.gigs.data.model.JobStatus
import com.example.gigs.viewmodel.AdminJobViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminJobApprovalScreen(
    viewModel: AdminJobViewModel = hiltViewModel(),
    onBackPressed: () -> Unit,
    onNavigateToJobDetails: (String) -> Unit
) {
    val pendingJobs by viewModel.pendingJobs.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val actionState by viewModel.actionState.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    var selectedJob by remember { mutableStateOf<Job?>(null) }
    var showApproveDialog by remember { mutableStateOf(false) }
    var showRejectDialog by remember { mutableStateOf(false) }
    var rejectionReason by remember { mutableStateOf("") }
    var showJobDetailDialog by remember { mutableStateOf(false) }

    // Handle action results
    LaunchedEffect(actionState) {
        when (actionState) {
            is AdminJobViewModel.ActionState.Success -> {
                val message = when ((actionState as AdminJobViewModel.ActionState.Success).action) {
                    "approve" -> "Job approved successfully"
                    "reject" -> "Job rejected successfully"
                    else -> "Action completed successfully"
                }
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(message)
                }
                viewModel.loadPendingJobs()
                viewModel.resetActionState()
            }
            is AdminJobViewModel.ActionState.Error -> {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(
                        (actionState as AdminJobViewModel.ActionState.Error).message
                    )
                }
                viewModel.resetActionState()
            }
            else -> {}
        }
    }

    // Load pending jobs on screen launch
    LaunchedEffect(Unit) {
        viewModel.loadPendingJobs()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Job Approval Dashboard") },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadPendingJobs() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
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
            if (isLoading && pendingJobs.isEmpty()) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            } else if (pendingJobs.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "No pending jobs to approve",
                        style = MaterialTheme.typography.headlineSmall
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "All jobs have been reviewed",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    item {
                        Text(
                            text = "Jobs Pending Approval",
                            style = MaterialTheme.typography.titleLarge
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    items(pendingJobs) { job ->
                        PendingJobCard(
                            job = job,
                            onApprove = {
                                selectedJob = job
                                showApproveDialog = true
                            },
                            onReject = {
                                selectedJob = job
                                showRejectDialog = true
                            },
                            onViewDetails = {
                                selectedJob = job
                                showJobDetailDialog = true
                            }
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }

    // Approve confirmation dialog
    if (showApproveDialog && selectedJob != null) {
        AlertDialog(
            onDismissRequest = { showApproveDialog = false },
            title = { Text("Approve Job") },
            text = {
                Text("Are you sure you want to approve this job posting? " +
                        "Once approved, it will be visible to all employees.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        selectedJob?.let { job ->
                            viewModel.approveJob(job.id)
                        }
                        showApproveDialog = false
                    }
                ) {
                    Text("Approve")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showApproveDialog = false }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // Reject dialog with reason input
    if (showRejectDialog && selectedJob != null) {
        Dialog(onDismissRequest = { showRejectDialog = false }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Text(
                        text = "Reject Job Posting",
                        style = MaterialTheme.typography.titleLarge
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Please provide a reason for rejection:",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = rejectionReason,
                        onValueChange = { rejectionReason = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("e.g., Incomplete information, inappropriate content") },
                        maxLines = 5
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = {
                                showRejectDialog = false
                                rejectionReason = ""
                            }
                        ) {
                            Text("Cancel")
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = {
                                selectedJob?.let { job ->
                                    viewModel.rejectJob(job.id, rejectionReason)
                                }
                                showRejectDialog = false
                                rejectionReason = ""
                            },
                            enabled = rejectionReason.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("Reject")
                        }
                    }
                }
            }
        }
    }

    // Job detail dialog
    if (showJobDetailDialog && selectedJob != null) {
        Dialog(onDismissRequest = { showJobDetailDialog = false }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .fillMaxWidth()
                ) {
                    Text(
                        text = selectedJob!!.title,
                        style = MaterialTheme.typography.headlineSmall
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Posted by: Employer ID ${selectedJob!!.employerId}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Divider(modifier = Modifier.padding(vertical = 8.dp))

                    JobDetailItem("Location", "${selectedJob!!.district}, ${selectedJob!!.state}")
                    JobDetailItem("Salary", selectedJob!!.salaryRange ?: "Not specified")
                    JobDetailItem("Work Type", selectedJob!!.workType.toString().replace("_", " "))
                    JobDetailItem("Remote", if (selectedJob!!.isRemote) "Yes" else "No")
                    JobDetailItem("Duration", selectedJob!!.workDuration ?: "Not specified")

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Description",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = selectedJob!!.description,
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    if (selectedJob!!.skillsRequired.isNotEmpty()) {
                        Text(
                            text = "Required Skills",
                            style = MaterialTheme.typography.titleMedium
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = selectedJob!!.skillsRequired.joinToString(", "),
                            style = MaterialTheme.typography.bodyMedium
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(
                            onClick = { showJobDetailDialog = false }
                        ) {
                            Text("Close")
                        }

                        Row {
                            Button(
                                onClick = {
                                    selectedJob?.let { job ->
                                        viewModel.rejectJob(job.id, "Job rejected by admin.")
                                    }
                                    showJobDetailDialog = false
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Text("Reject")
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Button(
                                onClick = {
                                    selectedJob?.let { job ->
                                        viewModel.approveJob(job.id)
                                    }
                                    showJobDetailDialog = false
                                }
                            ) {
                                Text("Approve")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PendingJobCard(
    job: Job,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onViewDetails: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = job.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                var expanded by remember { mutableStateOf(false) }

                Box {
                    IconButton(onClick = { expanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More options")
                    }

                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("View Details") },
                            onClick = {
                                onViewDetails()
                                expanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "${job.district}, ${job.state}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = job.salaryRange ?: "Not specified",
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = job.description,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(4.dp)
                    )
                    .padding(8.dp)
            ) {
                Text(
                    text = "Pending Approval",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Button(
                    onClick = onReject,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    Text("Reject")
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = onApprove,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    Text("Approve")
                }
            }
        }
    }
}

@Composable
fun JobDetailItem(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}