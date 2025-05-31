package com.example.gigs.ui.screens.jobs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.gigs.data.model.Job
import com.example.gigs.navigation.Screen
import com.example.gigs.ui.components.GigWorkPrimaryButton
import com.example.gigs.viewmodel.ChatViewModel
import com.example.gigs.viewmodel.JobViewModel
import java.text.NumberFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobDetailsScreen(
    jobViewModel: JobViewModel,
    jobId: String,
    navController: NavController,
    onBackPressed: () -> Unit,
    onApply: () -> Unit,
    onMessageEmployer: (employerId: String, employerName: String) -> Unit
) {
    val job by jobViewModel.selectedJob.collectAsState()
    val isLoading by jobViewModel.isLoading.collectAsState()
    val hasApplied by jobViewModel.hasApplied.collectAsState()
    val employerProfile by jobViewModel.employerProfile.collectAsState()
    val applicationStatus by jobViewModel.applicationUIState.collectAsState()

    // State to show error message
    var showErrorDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    // Load job details when screen opens
    LaunchedEffect(jobId) {
        jobViewModel.getJobDetails(jobId)
        jobViewModel.checkIfApplied(jobId)
        // This should also load employer profile
    }

    // Observe application status changes
    LaunchedEffect(applicationStatus) {
        when (applicationStatus) {
            is JobViewModel.ApplicationUIState.ERROR -> {
                errorMessage = (applicationStatus as JobViewModel.ApplicationUIState.ERROR).message
                showErrorDialog = true
            }
            is JobViewModel.ApplicationUIState.SUCCESS -> {
                // Success was handled by setting hasApplied = true
            }
            else -> {}
        }
    }

    // Error dialog
    if (showErrorDialog) {
        AlertDialog(
            onDismissRequest = { showErrorDialog = false },
            title = { Text("Application Error") },
            text = { Text(errorMessage) },
            confirmButton = {
                TextButton(onClick = { showErrorDialog = false }) {
                    Text("OK")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(job?.title ?: "Job Details") },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { /* Add to favorites */ }) {
                        Icon(Icons.Default.Favorite, contentDescription = "Favorite")
                    }
                    IconButton(onClick = { /* Share job */ }) {
                        Icon(Icons.Default.Share, contentDescription = "Share")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (job == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text("Job not found")
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Job title and basic info
                Text(
                    text = job!!.title,
                    style = MaterialTheme.typography.headlineMedium
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Business,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = employerProfile?.companyName ?: "Company",
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = job!!.location,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.AttachMoney,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = job!!.salaryRange ?: "Not specified",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = job!!.jobType.toString()
                            .replace("_", " ")
                            .lowercase()
                            .replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.bodyLarge
                    )
                }

                Divider(modifier = Modifier.padding(vertical = 16.dp))

                // Job description
                Text(
                    text = "Job Description",
                    style = MaterialTheme.typography.titleLarge
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = job!!.description,
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Skills required
                if (job!!.skillsRequired.isNotEmpty()) {
                    Text(
                        text = "Skills Required",
                        style = MaterialTheme.typography.titleLarge
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    job!!.skillsRequired.forEach { skill ->
                        Row(
                            verticalAlignment = Alignment.Top,
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(end = 8.dp, top = 2.dp)
                            )

                            Text(
                                text = skill,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Application deadline if available
                job!!.applicationDeadline?.let { deadline ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Text(
                            text = "Application Deadline: $deadline",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }

                // About the employer
                if (employerProfile != null) {
                    Text(
                        text = "About the Employer",
                        style = MaterialTheme.typography.titleLarge
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = employerProfile!!.description ?: "No description available",
                        style = MaterialTheme.typography.bodyMedium
                    )


                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Action buttons
                if (hasApplied) {
                    // Already applied - show status
                    Button(
                        onClick = { /* View application */ },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Text("Already Applied")
                    }
                } else {
                    // Apply button
                    GigWorkPrimaryButton(
                        text = "Apply for this Job",
                        onClick = {
                            job?.id?.let { jobId ->
                                jobViewModel.applyForJob(jobId)
                                // onApply called only when the application succeeds
                                // we'll use the applicationStatus for that
                                if (applicationStatus is JobViewModel.ApplicationUIState.SUCCESS) {
                                    onApply()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        isLoading = isLoading
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                  MessageEmployerButton(
                    jobId = jobId,
                    employerId = job?.employerId ?: "",
                    viewModel = hiltViewModel(),
                    navController = navController
                )
                Spacer(modifier = Modifier.height(32.dp))


                /*
                                // Message employer button
                                OutlinedButton(
                                    onClick = {
                                        job?.employerId?.let { employerId ->
                                            onMessageEmployer(
                                                employerId,
                                                employerProfile?.companyName ?: "Employer"
                                            )
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Email,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )

                                    Spacer(modifier = Modifier.width(8.dp))

                                    Text("Message Employer")
                                }

                                Spacer(modifier = Modifier.height(32.dp))

                                 */
            }
        }



        }
}

// Add this to JobDetailsScreen.kt
@Composable
fun MessageEmployerButton(
    jobId: String,
    employerId: String,
    viewModel: ChatViewModel = hiltViewModel(),
    navController: NavController
) {
    val currentUserId = viewModel.currentUserId.collectAsState().value ?: return

    OutlinedButton(
        onClick = {
            viewModel.createNewConversation(
                jobId = jobId,
                employerId = employerId,
                employeeId = currentUserId
            ) { conversationId ->
                // Navigate to the conversation
                navController.navigate(
                    Screen.Chat.createRoute(
                    conversationId = conversationId,
                    otherUserName = "Employer", // You might want to fetch the real name
                    receiverId = employerId
                ))
            }
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = Icons.Default.Message,
            contentDescription = null,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text("Message Employer")
    }
}