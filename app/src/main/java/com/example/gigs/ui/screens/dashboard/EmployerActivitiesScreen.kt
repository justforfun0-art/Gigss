package com.example.gigs.ui.screens.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.gigs.data.model.Activity
import com.example.gigs.utils.DateUtils.formatTimeAgo
import com.example.gigs.viewmodel.EmployerDashboardViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmployerActivitiesScreen(
    viewModel: EmployerDashboardViewModel = hiltViewModel(),
    onBackPressed: () -> Unit
) {
    val recentActivities by viewModel.recentActivities.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    // Load activities when screen appears
    LaunchedEffect(Unit) {
        viewModel.loadDashboardData()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recent Activities") },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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
        } else if (recentActivities.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "No Recent Activities",
                        style = MaterialTheme.typography.titleLarge
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Activities like job applications, status updates, and reviews will appear here",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                item {
                    // Header
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Timeline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(24.dp)
                            )

                            Spacer(modifier = Modifier.width(12.dp))

                            Column {
                                Text(
                                    text = "Activity Timeline",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )

                                Text(
                                    text = "${recentActivities.size} recent activities",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }

                items(recentActivities) { activity ->
                    ActivityDetailItem(activity = activity)
                }
            }
        }
    }
}

@Composable
fun ActivityDetailItem(
    activity: Activity
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Activity icon
                val (icon, iconColor) = when (activity.activityType) {
                    "application" -> Pair(Icons.Default.Description, MaterialTheme.colorScheme.primary)
                    "status_update" -> Pair(Icons.Default.Update, MaterialTheme.colorScheme.secondary)
                    "job_posting" -> Pair(Icons.Default.Work, MaterialTheme.colorScheme.tertiary)
                    "review" -> Pair(Icons.Default.RateReview, MaterialTheme.colorScheme.error)
                    "message" -> Pair(Icons.Default.Message, MaterialTheme.colorScheme.outline)
                    "interview" -> Pair(Icons.Default.Event, MaterialTheme.colorScheme.primary)
                    "hire" -> Pair(Icons.Default.CheckCircle, MaterialTheme.colorScheme.secondary)
                    else -> Pair(Icons.Default.Notifications, MaterialTheme.colorScheme.onSurface)
                }

                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(28.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    // Activity description
                    Text(
                        text = buildAnnotatedString {
                            append(activity.userName)
                            append(" ${activity.action}")
                            if (activity.targetUserName.isNotEmpty()) {
                                append(" ${activity.targetUserName}")
                            }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Activity title/subject
                    Text(
                        text = activity.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Activity type badge
                    val badgeText = when (activity.activityType) {
                        "application" -> "New Application"
                        "status_update" -> "Status Update"
                        "job_posting" -> "Job Posted"
                        "review" -> "Review Received"
                        "message" -> "Message"
                        "interview" -> "Interview Scheduled"
                        "hire" -> "Candidate Hired"
                        else -> activity.activityType.replace("_", " ").split(" ")
                            .joinToString(" ") { it.capitalize() }
                    }

                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.bodySmall,
                        color = iconColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Timestamp
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )

                Spacer(modifier = Modifier.width(4.dp))

                Text(
                    text = if (activity.activityTime != null) {
                        formatTimeAgo(activity.activityTime)
                    } else {
                        "Recently"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// Extension function to capitalize first letter
private fun String.capitalize(): String {
    return this.replaceFirstChar {
        if (it.isLowerCase()) it.titlecase()
        else it.toString()
    }
}