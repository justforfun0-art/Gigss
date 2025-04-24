package com.example.gigs.ui.screens.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.gigs.data.model.Activity
import com.example.gigs.data.model.ApplicationWithJob
import com.example.gigs.data.model.CategoryStat
import com.example.gigs.data.model.Job
import com.example.gigs.data.model.LocationStat
import com.example.gigs.viewmodel.EmployerDashboardViewModel
import io.ktor.websocket.Frame
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmployerDashboardScreen(
    viewModel: EmployerDashboardViewModel = hiltViewModel(),
    onViewAllJobs: () -> Unit,
    onViewAllActivities: () -> Unit,
    onCreateJob: () -> Unit,
    onNavigateToNotifications: () -> Unit,
    onNavigateToMessages: () -> Unit,
    onBackPressed: () -> Unit
) {
    val dashboardData by viewModel.dashboardData.collectAsState()
    val recentActivities by viewModel.recentActivities.collectAsState()
    val recentJobs by viewModel.recentJobs.collectAsState()
    val locationStats by viewModel.locationStats.collectAsState()
    val categoryStats by viewModel.categoryStats.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadDashboardData()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Frame.Text("Employer Dashboard") },
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
                            modifier = Modifier.weight(1f)
                        )

                        Spacer(modifier = Modifier.width(16.dp))

                        StatCard(
                            title = "Active Jobs",
                            value = dashboardData?.activeJobs ?: 0,
                            icon = Icons.Default.Visibility,
                            modifier = Modifier.weight(1f)
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
                            modifier = Modifier.weight(1f)
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

                // Recent Jobs Section
                item {
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
                        JobItem(job = job)

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

// Common composables for both dashboard screens

@Composable
fun StatCard(
    title: String,
    value: Int? = null,
    valueText: String? = null,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
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

@Composable
fun ApplicationItem(
    application: ApplicationWithJob,
    onClick: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = { onClick?.invoke() }
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

                val statusColor = when (application.status) {
                    "APPLIED" -> MaterialTheme.colorScheme.primary
                    "SHORTLISTED" -> MaterialTheme.colorScheme.tertiary
                    "HIRED" -> MaterialTheme.colorScheme.secondary
                    "REJECTED" -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }

                Text(
                    text = application.status,
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
        }
    }
}

@Composable
fun JobItem(
    job: Job,
    onClick: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = { onClick?.invoke() }
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

// Utility functions
fun formatDate(dateString: String): String {
    try {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
        inputFormat.timeZone = TimeZone.getTimeZone("UTC")
        val date = inputFormat.parse(dateString)

        val outputFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        return outputFormat.format(date)
    } catch (e: Exception) {
        return dateString
    }
}

fun formatTimestamp(dateString: String): String {
    return try {
        // First try the full ISO format with milliseconds
        val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
        inputFormat.timeZone = TimeZone.getTimeZone("UTC")

        var date: Date? = null

        try {
            date = inputFormat.parse(dateString)
        } catch (e: Exception) {
            // If that fails, try without milliseconds
            val simpleFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
            simpleFormat.timeZone = TimeZone.getTimeZone("UTC")

            try {
                date = simpleFormat.parse(dateString)
            } catch (e: Exception) {
                // If that fails too, try just the date
                val dateOnlyFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                date = dateOnlyFormat.parse(dateString)
            }
        }

        if (date != null) {
            val outputFormat = SimpleDateFormat("MMM dd, yyyy h:mm a", Locale.getDefault())
            outputFormat.timeZone = TimeZone.getDefault() // Convert to local time
            return outputFormat.format(date)
        } else {
            // If all parsing attempts fail, return the original string
            return dateString
        }
    } catch (e: Exception) {
        // If any exception occurs, return the original string
        return dateString
    }
}


fun formatTimeAgo(dateString: String): String {
    try {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
        inputFormat.timeZone = TimeZone.getTimeZone("UTC")
        val date = inputFormat.parse(dateString)

        val now = Calendar.getInstance().time
        val diffInMillis = now.time - date.time
        val diffInMinutes = diffInMillis / (60 * 1000)
        val diffInHours = diffInMillis / (60 * 60 * 1000)
        val diffInDays = diffInMillis / (24 * 60 * 60 * 1000)

        return when {
            diffInMinutes < 1 -> "Just now"
            diffInMinutes < 60 -> "$diffInMinutes min ago"
            diffInHours < 24 -> "$diffInHours hr ago"
            diffInDays < 7 -> "$diffInDays days ago"
            else -> formatDate(dateString)
        }
    } catch (e: Exception) {
        return dateString
    }
}