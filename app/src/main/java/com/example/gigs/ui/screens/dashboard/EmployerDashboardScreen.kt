package com.example.gigs.ui.screens.dashboard

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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gigs.data.model.*
import com.example.gigs.data.repository.ApplicationRepository
import com.example.gigs.data.repository.JobRepository
import com.example.gigs.utils.DateUtils.formatDate
import com.example.gigs.utils.DateUtils.formatTimeAgo
import com.example.gigs.viewmodel.EmployerApplicationsViewModel
import com.example.gigs.viewmodel.EmployerDashboardViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmployerDashboardScreen(
    dashboardViewModel: EmployerDashboardViewModel = hiltViewModel(),
    applicationsViewModel: EmployerApplicationsViewModel = hiltViewModel(),
    onViewAllJobs: () -> Unit,
    onViewAllActivities: () -> Unit,
    onCreateJob: () -> Unit,
    onNavigateToNotifications: () -> Unit,
    onNavigateToMessages: () -> Unit,
    onViewApplication: (String) -> Unit,
    onBackPressed: () -> Unit
) {
    val dashboardData by dashboardViewModel.dashboardData.collectAsState()
    val recentActivities by dashboardViewModel.recentActivities.collectAsState()
    val recentJobs by dashboardViewModel.recentJobs.collectAsState()
    val recentApplications by applicationsViewModel.recentApplications.collectAsState()
    val locationStats by dashboardViewModel.locationStats.collectAsState()
    val categoryStats by dashboardViewModel.categoryStats.collectAsState()
    val isLoading by dashboardViewModel.isLoading.collectAsState()
    val isLoadingApplications by applicationsViewModel.isLoading.collectAsState()

    // Load dashboard data and applications
    LaunchedEffect(Unit) {
        dashboardViewModel.loadDashboardData()
        applicationsViewModel.loadRecentApplications(5)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Employer Dashboard") },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToNotifications) {
                        Icon(Icons.Default.Notifications, contentDescription = "Notifications")
                    }
                    IconButton(onClick = onNavigateToMessages) {
                        Icon(Icons.Default.Email, contentDescription = "Messages")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreateJob
            ) {
                Icon(Icons.Default.Add, contentDescription = "Create Job")
            }
        }
    ) { paddingValues ->
        if (isLoading && dashboardData == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(16.dp))

                    // Stats Cards
                    Row(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        StatCard(
                            title = "Total Jobs",
                            value = dashboardData?.totalJobs ?: 0,
                            icon = Icons.Default.Work,
                            modifier = Modifier.weight(1f),
                            onClick = onViewAllJobs
                        )

                        Spacer(modifier = Modifier.width(16.dp))

                        StatCard(
                            title = "Active Jobs",
                            value = dashboardData?.activeJobs ?: 0,
                            icon = Icons.Default.Visibility,
                            modifier = Modifier.weight(1f),
                            onClick = onViewAllJobs
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        StatCard(
                            title = "Applications",
                            value = dashboardData?.totalApplicationsReceived ?: 0,
                            icon = Icons.Default.Description,
                            modifier = Modifier.weight(1f),
                            onClick = { /* Navigate to all applications */ }
                        )

                        Spacer(modifier = Modifier.width(16.dp))

                        StatCard(
                            title = "Rating",
                            valueText = String.format("%.1f", dashboardData?.averageRating ?: 0f),
                            icon = Icons.Default.Star,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }

                // Recent Applications Section
                item {
                    Text(
                        text = "Recent Applications",
                        style = MaterialTheme.typography.titleLarge
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (isLoadingApplications) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(40.dp))
                        }
                    }
                } else if (recentApplications.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "No applications yet",
                                    style = MaterialTheme.typography.bodyLarge
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Button(onClick = onCreateJob) {
                                    Text("Create a job to receive applications")
                                }
                            }
                        }
                    }
                } else {
                    items(recentApplications) { application ->
                        ApplicationItem(
                            application = application,
                            onClick = { onViewApplication(application.id) }
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                // Recent Jobs Section
                item {
                    Spacer(modifier = Modifier.height(16.dp))

                    SectionHeader(
                        title = "Your Jobs",
                        onViewAll = onViewAllJobs
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (recentJobs.isEmpty()) {
                    item {
                        EmptyStateMessage(
                            message = "You haven't posted any jobs yet",
                            actionText = "Create Job",
                            onActionClick = onCreateJob
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                    }
                } else {
                    items(recentJobs) { job ->
                        JobItem(
                            job = job,
                            onClick = { /* Navigate to job details */ }
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                // Stats Sections
                if (locationStats.isNotEmpty() || categoryStats.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Applications by Location",
                            style = MaterialTheme.typography.titleMedium
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Location stats chart
                        LocationStatsChart(stats = locationStats)

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Applications by Category",
                            style = MaterialTheme.typography.titleMedium
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Category stats chart
                        CategoryStatsChart(stats = categoryStats)

                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                // Recent Activities Section
                item {
                    SectionHeader(
                        title = "Recent Activities",
                        onViewAll = onViewAllActivities
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (recentActivities.isEmpty()) {
                    item {
                        EmptyStateMessage(
                            message = "No recent activities to show"
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                    }
                } else {
                    items(recentActivities) { activity ->
                        ActivityItem(activity = activity)

                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

// Common composables for dashboard screens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatCard(
    title: String,
    value: Int? = null,
    valueText: String? = null,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Card(
        modifier = modifier,
        onClick = { onClick?.invoke() },
        enabled = onClick != null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (value != null) value.toString() else valueText ?: "0",
                style = MaterialTheme.typography.headlineSmall
            )

            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    onViewAll: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium
        )

        if (onViewAll != null) {
            TextButton(onClick = onViewAll) {
                Text("View All")
            }
        }
    }
}

@Composable
fun EmptyStateMessage(
    message: String,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )

            if (actionText != null && onActionClick != null) {
                Spacer(modifier = Modifier.height(8.dp))

                Button(onClick = onActionClick) {
                    Text(actionText)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApplicationItem(
    application: ApplicationWithJob,
    onClick: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = { onClick?.invoke() },
        enabled = onClick != null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = application.job.title,
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = application.job.location,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                val statusColor = when (application.status.name) {
                    "APPLIED" -> MaterialTheme.colorScheme.primary
                    "SHORTLISTED" -> MaterialTheme.colorScheme.tertiary
                    "HIRED" -> MaterialTheme.colorScheme.secondary
                    "REJECTED" -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }

                Text(
                    text = application.status.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = statusColor
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            if (application.appliedAt != null) {
                Text(
                    text = "Applied on ${formatDate(application.appliedAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Applicant ID: ${application.employeeId}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobItem(
    job: Job,
    onClick: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = { onClick?.invoke() },
        enabled = onClick != null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = job.title,
                    style = MaterialTheme.typography.titleMedium
                )

                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(
                            if (job.isActive) MaterialTheme.colorScheme.secondaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (job.isActive) "Active" else "Inactive",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (job.isActive)
                            MaterialTheme.colorScheme.onSecondaryContainer
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = job.location,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = job.salaryRange ?: "Not specified",
                    style = MaterialTheme.typography.bodyMedium
                )

                if (job.createdAt != null) {
                    Text(
                        text = "Posted on ${formatDate(job.createdAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun ActivityItem(
    activity: Activity
) {
    Card(
        modifier = Modifier.fillMaxWidth()
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
                val icon = when (activity.activityType) {
                    "application" -> Icons.Default.Description
                    "status_update" -> Icons.Default.Update
                    "job_posting" -> Icons.Default.Work
                    "review" -> Icons.Default.RateReview
                    else -> Icons.Default.Notifications
                }

                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Column {
                    Text(
                        text = buildAnnotatedString {
                            append(activity.userName)
                            append(" ${activity.action} ")
                            if (activity.targetUserName.isNotEmpty()) {
                                append(activity.targetUserName)
                            }
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Text(
                        text = activity.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            if (activity.activityTime != null) {
                Text(
                    text = formatTimeAgo(activity.activityTime),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}

@Composable
fun LocationStatsChart(
    stats: List<LocationStat>
) {
    val topLocations = stats.take(5)

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            topLocations.forEach { stat ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stat.location,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.width(120.dp)
                    )

                    val maxCount = topLocations.maxOf { it.applicationCount }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(24.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(stat.applicationCount.toFloat() / maxCount)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = stat.applicationCount.toString(),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
fun CategoryStatsChart(
    stats: List<CategoryStat>
) {
    val topCategories = stats.take(5)

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            topCategories.forEach { stat ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stat.category,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.width(120.dp)
                    )

                    val maxCount = topCategories.maxOf { it.applicationCount }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(24.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(stat.applicationCount.toFloat() / maxCount)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.7f))
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = stat.applicationCount.toString(),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}
