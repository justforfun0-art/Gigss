package com.example.gigs.ui.screens.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import com.example.gigs.ui.components.DashboardApplicationItem
import com.example.gigs.ui.components.DashboardCard
import com.example.gigs.ui.components.DashboardEmptyStateMessage
import com.example.gigs.ui.components.DashboardSectionHeader
import com.example.gigs.ui.components.EmployeeProfileSection
import com.example.gigs.utils.DateUtils
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
    onViewApplication: (String) -> Unit,
    onViewAllActivities: () -> Unit,
    onNavigateToNotifications: () -> Unit,
    onNavigateToMessages: () -> Unit,
    onNavigateToJobHistory: () -> Unit,
    onEditProfile: () -> Unit,
    onBackPressed: () -> Unit
) {
    // Collect state from the view model
    val totalApplications by dashboardViewModel.totalApplications.collectAsState()
    val totalHired by dashboardViewModel.totalHired.collectAsState()
    val averageRating by dashboardViewModel.averageRating.collectAsState()
    val totalReviews by dashboardViewModel.totalReviews.collectAsState()
    val isLoading by dashboardViewModel.isLoading.collectAsState()
    val recentApplications by dashboardViewModel.recentApplications.collectAsState()
    val recentActivities by dashboardViewModel.recentActivities.collectAsState(emptyList())

    // Get employee profile data
    val employeeProfile by profileViewModel.employeeProfile.collectAsState()
    val isProfileLoading by profileViewModel.isLoading.collectAsState()

    // Load dashboard data when screen is shown
    LaunchedEffect(Unit) {
        dashboardViewModel.loadDashboardData()
        profileViewModel.getEmployeeProfile() // Load the employee profile
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
        if ((isLoading && recentApplications.isEmpty()) || isProfileLoading) {
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
                    .verticalScroll(rememberScrollState())
            ) {
                // Profile Section - Use the shared component
                EmployeeProfileSection(
                    employeeProfile = employeeProfile,
                    onEditProfile = onEditProfile
                )

                Divider(modifier = Modifier.padding(horizontal = 16.dp))

                // Dashboard Section
                Text(
                    text = "Dashboard",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(16.dp)
                )

                // Stats cards in a grid
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    DashboardCard(
                        icon = Icons.Default.Description,
                        value = totalApplications.toString(),
                        label = "Applications",
                        modifier = Modifier.weight(1f),
                        onClick = onViewAllApplications
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    DashboardCard(
                        icon = Icons.Default.WorkOutline,
                        value = totalHired.toString(),
                        label = "Hired",
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    DashboardCard(
                        icon = Icons.Default.Star,
                        value = String.format("%.1f", averageRating),
                        label = "Rating",
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    DashboardCard(
                        icon = Icons.Default.RateReview,
                        value = totalReviews.toString(),
                        label = "Reviews",
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Job History Card - Made clickable
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    onClick = onNavigateToJobHistory
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Job History",
                                style = MaterialTheme.typography.titleMedium
                            )

                            Text(
                                text = "View your application history, including pending, active, and completed jobs",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = "View job history",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Recent Applications Section
                DashboardSectionHeader(
                    title = "Recent Applications",
                    onViewAll = onViewAllApplications
                )

                if (recentApplications.isEmpty()) {
                    DashboardEmptyStateMessage(
                        message = "You haven't applied to any jobs yet",
                        actionText = "Find Jobs",
                        onActionClick = { /* Navigate to find jobs */ }
                    )
                } else {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        recentApplications.forEach { application ->
                            DashboardApplicationItem(
                                application = application,
                                onClick = { onViewApplication(application.id) }
                            )

                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }

                // Recent Activities Section (if any activities exist)
                if (recentActivities.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))

                    DashboardSectionHeader(
                        title = "Recent Activities",
                        onViewAll = onViewAllActivities
                    )

                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        recentActivities.take(3).forEach { activity ->
                            EmployeeActivityItem(activity = activity)
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Find Jobs Button
                Button(
                    onClick = { /* Navigate to find jobs */ },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text("Find Jobs")
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun ProfileSection(
    onEditProfileClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Profile avatar
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // User name
        Text(
            text = "Saba", // Replace with actual name from profile
            style = MaterialTheme.typography.titleLarge
        )

        // Location
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.width(4.dp))

            Text(
                text = "Jind, Haryana",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Edit Profile button
        Button(
            onClick = onEditProfileClick,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
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
fun DashboardCard(
    icon: ImageVector,
    value: String,
    label: String,
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
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium
            )

            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApplicationItem(
    application: ApplicationWithJob,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
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

                val statusColor = when (application.status.toString().uppercase()) {
                    "APPLIED" -> MaterialTheme.colorScheme.primary
                    "SHORTLISTED" -> MaterialTheme.colorScheme.tertiary
                    "HIRED" -> MaterialTheme.colorScheme.secondary
                    "REJECTED" -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }

                Text(
                    text = application.status.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = statusColor
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            if (application.appliedAt != null) {
                Text(
                    text = application.appliedAt?.let {
                        DateUtils.formatApplicationDate(it)
                    } ?: "Applied recently",
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