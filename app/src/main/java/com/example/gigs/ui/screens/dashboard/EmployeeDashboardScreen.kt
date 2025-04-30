package com.example.gigs.ui.screens.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.gigs.data.model.Activity
import com.example.gigs.data.model.ApplicationWithJob
import com.example.gigs.data.model.EmployeeProfile
import com.example.gigs.ui.components.GigWorkHeaderText
import com.example.gigs.utils.DateUtils.formatDate
import com.example.gigs.utils.DateUtils.formatTimeAgo
import com.example.gigs.viewmodel.EmployeeDashboardViewModel
import com.example.gigs.viewmodel.ProfileViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmployeeDashboardScreen(
    dashboardViewModel: EmployeeDashboardViewModel = hiltViewModel(),
    profileViewModel: ProfileViewModel = hiltViewModel(),
    onViewAllApplications: () -> Unit,
    onViewApplication: (String) -> Unit, // New parameter for specific application navigation
    onViewAllActivities: () -> Unit,
    onNavigateToNotifications: () -> Unit,
    onNavigateToMessages: () -> Unit,
    onNavigateToJobHistory: () -> Unit, // New parameter for job history navigation
    onEditProfile: () -> Unit,
    onBackPressed: () -> Unit
) {
    val dashboardData by dashboardViewModel.dashboardData.collectAsState()
    val recentActivities by dashboardViewModel.recentActivities.collectAsState()
    val recentApplications by dashboardViewModel.recentApplications.collectAsState()
    val isLoading by dashboardViewModel.isLoading.collectAsState()
    val employeeProfile by profileViewModel.employeeProfile.collectAsState()
    val scrollState = rememberScrollState()

    LaunchedEffect(Unit) {
        dashboardViewModel.loadDashboardData()
        profileViewModel.getEmployeeProfile()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Profile & Dashboard") },
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(scrollState),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Profile section
                ProfileSection(
                    employeeProfile = employeeProfile,
                    onEditProfile = onEditProfile
                )

                Spacer(modifier = Modifier.height(24.dp))
                Divider()
                Spacer(modifier = Modifier.height(16.dp))

                // Dashboard section
                GigWorkHeaderText(text = "Dashboard")

                Spacer(modifier = Modifier.height(16.dp))

                // Stats Cards
                Row(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    EmployeeStatCard(
                        title = "Applications",
                        value = dashboardData?.totalApplications ?: 0,
                        icon = Icons.Default.Description,
                        modifier = Modifier.weight(1f),
                        onClick = onViewAllApplications
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    EmployeeStatCard(
                        title = "Hired",
                        value = dashboardData?.hiredCount ?: 0,
                        icon = Icons.Default.WorkOutline,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    EmployeeStatCard(
                        title = "Rating",
                        valueText = String.format("%.1f", dashboardData?.averageRating ?: 0f),
                        icon = Icons.Default.Star,
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    EmployeeStatCard(
                        title = "Reviews",
                        value = dashboardData?.reviewCount ?: 0,
                        icon = Icons.Default.RateReview,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Job History Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    onClick = onNavigateToJobHistory
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )

                                Spacer(modifier = Modifier.width(8.dp))

                                Text(
                                    text = "Job History",
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }

                            Icon(
                                imageVector = Icons.Default.ArrowForward,
                                contentDescription = "View Job History",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "View your application history, including pending, active, and completed jobs",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Recent Applications Section
                EmployeeSectionHeader(
                    title = "Recent Applications",
                    onViewAll = onViewAllApplications
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (recentApplications.isEmpty()) {
                    EmployeeEmptyStateMessage(
                        message = "You haven't applied to any jobs yet",
                        actionText = "Browse Jobs"
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                } else {
                    recentApplications.forEach { application ->
                        ApplicationItem(
                            application = application,
                            onClick = { onViewApplication(application.id) }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                // Recent Activities Section
                Spacer(modifier = Modifier.height(16.dp))
                EmployeeSectionHeader(
                    title = "Recent Activities",
                    onViewAll = onViewAllActivities
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (recentActivities.isEmpty()) {
                    EmployeeEmptyStateMessage(
                        message = "No recent activities to show"
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                } else {
                    recentActivities.forEach { activity ->
                        EmployeeActivityItem(activity = activity)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun ProfileSection(
    employeeProfile: EmployeeProfile?,
    onEditProfile: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (employeeProfile == null) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text("Loading profile...")
            return
        }

        // Profile photo
        if (employeeProfile.profilePhotoUrl != null) {
            AsyncImage(
                model = employeeProfile.profilePhotoUrl,
                contentDescription = "Profile Photo",
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.size(60.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = employeeProfile.name,
            style = MaterialTheme.typography.headlineSmall
        )

        employeeProfile.email?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )

            Spacer(modifier = Modifier.width(4.dp))

            Text(
                text = "${employeeProfile.district}, ${employeeProfile.state}",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        FilledTonalButton(
            onClick = onEditProfile,
            modifier = Modifier.fillMaxWidth(0.8f)
        ) {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text("Edit Profile")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmployeeStatCard(
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
fun EmployeeSectionHeader(
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
fun EmployeeEmptyStateMessage(
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
    onClick: () -> Unit  // This parameter is used to navigate to application details
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick   // Make the entire card clickable
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

                val statusColor = when (application.status.uppercase()) {
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

            // Add "View Details" button
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onClick) {
                    Text("View Details")
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun EmployeeActivityItem(
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
                        text = activity.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )

                    Text(
                        text = activity.action,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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