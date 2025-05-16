package com.example.gigs.ui.screens.jobs

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.WorkHistory
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.gigs.data.model.ApplicationWithJob
import com.example.gigs.ui.theme.PrimaryBlue
import com.example.gigs.utils.DateUtils
import com.example.gigs.viewmodel.JobHistoryViewModel
import dagger.hilt.android.HiltAndroidApp
import androidx.hilt.navigation.compose.hiltViewModel


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobHistoryScreen(
    viewModel: JobHistoryViewModel = hiltViewModel(),
    onJobSelected: (String) -> Unit,
    onApplicationSelected: (String) -> Unit, // Added parameter for application navigation
    onBackPressed: () -> Unit
) {
    val allApplications by viewModel.allApplications.collectAsState()
    val activeApplications by viewModel.activeApplications.collectAsState()
    val completedApplications by viewModel.completedApplications.collectAsState()
    val rejectedApplications by viewModel.rejectedApplications.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("All", "Active", "Completed", "Rejected")

    LaunchedEffect(Unit) {
        viewModel.loadApplicationsHistory()
        viewModel.refreshApplicationHistory()

    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Job History") },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            TabRow(selectedTabIndex = selectedTabIndex) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(title) }
                    )
                }
            }

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                val currentList = when (selectedTabIndex) {
                    0 -> allApplications
                    1 -> activeApplications
                    2 -> completedApplications
                    3 -> rejectedApplications
                    else -> allApplications
                }

                if (currentList.isEmpty()) {
                    EmptyHistoryMessage(tabName = tabs[selectedTabIndex])
                } else {
                    JobHistoryList(
                        applications = currentList,
                        onJobSelected = onJobSelected,
                        onApplicationSelected = onApplicationSelected // Pass the callback
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyHistoryMessage(tabName: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.WorkHistory,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "No $tabName Jobs",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = when (tabName) {
                    "All" -> "You haven't applied to any jobs yet"
                    "Active" -> "You don't have any active job applications"
                    "Completed" -> "You haven't completed any jobs yet"
                    "Rejected" -> "You don't have any rejected applications"
                    else -> "No job history to show"
                },
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobHistoryList(
    applications: List<ApplicationWithJob>,
    onJobSelected: (String) -> Unit,
    onApplicationSelected: (String) -> Unit // Added parameter
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        items(applications) { application ->
            JobHistoryItem(
                application = application,
                onClick = { onJobSelected(application.jobId) },
                onViewApplicationDetails = { onApplicationSelected(application.id) } // Pass application ID
            )

            Divider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
        }

        // Add bottom padding
        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobHistoryItem(
    application: ApplicationWithJob,
    onClick: () -> Unit,
    onViewApplicationDetails: () -> Unit // Added parameter
) {
    Card(
        onClick = onClick, // Keep the card click for job details
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Job title and application date
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = application.job.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                application.appliedAt?.let { dateString ->
                    Text(
                        text = "Applied: ${DateUtils.formatDate(dateString)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Location
            Text(
                text = application.job.location,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Salary
            Text(
                text = application.job.salaryRange ?: "Salary not specified",
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Status chip
                val (statusColor, statusIcon) = when (application.status.toString().uppercase()) {
                    "APPLIED" -> Pair(PrimaryBlue, null)
                    "SHORTLISTED" -> Pair(MaterialTheme.colorScheme.tertiary, null)
                    "HIRED" -> Pair(MaterialTheme.colorScheme.secondary, Icons.Default.Check)
                    "REJECTED" -> Pair(MaterialTheme.colorScheme.error, Icons.Default.Close)
                    else -> Pair(MaterialTheme.colorScheme.outlineVariant, null)
                }

                FilterChip(
                    selected = true,
                    onClick = { /* No action needed */ },
                    label = {
                        Text(
                            text = application.status.toString().uppercase(),
                            style = MaterialTheme.typography.labelMedium
                        )
                    },
                    leadingIcon = {
                        statusIcon?.let {
                            Icon(
                                imageVector = it,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    },
                    colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                        containerColor = statusColor.copy(alpha = 0.1f),
                        labelColor = statusColor,
                        iconColor = statusColor
                    )
                )

                // Add View Application Details button
                TextButton(
                    onClick = onViewApplicationDetails
                ) {
                    Text(
                        text = "Application Details",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = "View application details",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Show job description preview
            if (application.job.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = application.job.description,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }
        }
    }
}