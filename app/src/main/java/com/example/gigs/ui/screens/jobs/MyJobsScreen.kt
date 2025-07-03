package com.example.gigs.ui.screens.jobs

import android.net.Uri
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.gigs.data.model.Job
import com.example.gigs.data.model.JobStatus
import com.example.gigs.navigation.Screen
import com.example.gigs.viewmodel.JobViewModel

// 🚀 Enums for job filtering
enum class JobsFilter {
    ALL_JOBS,           // Show all jobs (including rejected)
    ACTIVE_ONLY         // Show only approved/active jobs
}

// 🚀 FIXED: MyJobsScreen with proper job loading
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyJobsScreen(
    navController: NavController,
    filter: String = "ALL_JOBS",
    title: String = "My Jobs",
    viewModel: JobViewModel = hiltViewModel()
) {
    val jobsFilter = try {
        JobsFilter.valueOf(filter)
    } catch (e: Exception) {
        JobsFilter.ALL_JOBS
    }

    val jobs by viewModel.jobs.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    // 🚀 DEBUG: Add logging to see what's happening
    LaunchedEffect(jobs.size) {
        Log.d("MyJobsScreen", "🔍 Jobs loaded: ${jobs.size} jobs with filter: $jobsFilter")
        jobs.forEach { job ->
            Log.d("MyJobsScreen", "  - Job: ${job.title}, Status: ${job.status}")
        }
    }

    LaunchedEffect(jobsFilter) {
        Log.d("MyJobsScreen", "🚀 Loading jobs with filter: $jobsFilter")

        // 🚀 FIXED: Load jobs based on filter with proper method calls
        when (jobsFilter) {
            JobsFilter.ALL_JOBS -> {
                // 🚀 Load all jobs posted by current employer
                viewModel.getMyPostedJobs() // Make sure this method exists in JobViewModel
            }
            JobsFilter.ACTIVE_ONLY -> {
                // 🚀 Load only active/approved jobs
                viewModel.getMyActiveJobs() // Make sure this method exists in JobViewModel
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = Uri.decode(title),
                        style = MaterialTheme.typography.headlineSmall
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (isLoading) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Loading your jobs...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (jobs.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Work,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        modifier = Modifier.size(80.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = when (jobsFilter) {
                            JobsFilter.ALL_JOBS -> "No Jobs Posted"
                            JobsFilter.ACTIVE_ONLY -> "No Active Jobs"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = when (jobsFilter) {
                            JobsFilter.ALL_JOBS -> "You haven't posted any jobs yet. Tap the + button to create your first job posting."
                            JobsFilter.ACTIVE_ONLY -> "You don't have any active/approved jobs at the moment."
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    if (jobsFilter == JobsFilter.ALL_JOBS) {
                        Button(
                            onClick = {
                                navController.navigate(Screen.CreateJob.route)
                            }
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Create Job")
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                ) {
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${jobs.size} Jobs Found",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )

                            // 🚀 Add filter indicator
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Text(
                                    text = when (jobsFilter) {
                                        JobsFilter.ALL_JOBS -> "All Jobs"
                                        JobsFilter.ACTIVE_ONLY -> "Active Only"
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    items(jobs) { job ->
                        MyJobCard(
                            job = job,
                            onClick = {
                                navController.navigate(Screen.EmployerJobDetails.createRoute(job.id))
                            }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyJobCard(
    job: Job,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = job.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "${job.district}, ${job.state}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = job.salaryRange ?: "Salary not specified",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // Status indicator
                val (statusColor, statusText) = when (job.status) {
                    JobStatus.APPROVED -> Pair(Color.Green, "APPROVED")
                    JobStatus.PENDING_APPROVAL -> Pair(Color.Blue, "PENDING")
                    JobStatus.REJECTED -> Pair(Color.Red, "REJECTED")
                    JobStatus.CLOSED -> Pair(Color.Gray, "CLOSED")
                    else -> Pair(Color.Gray, "UNKNOWN")
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

            if (job.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = job.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}