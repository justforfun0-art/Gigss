package com.example.gigs.ui.screens.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.gigs.data.model.Activity
import com.example.gigs.data.model.ApplicationWithJob
import com.example.gigs.data.model.CategoryStat
import com.example.gigs.data.model.Job
import com.example.gigs.data.model.LocationStat
import com.example.gigs.ui.components.DashboardCard
import com.example.gigs.ui.components.DashboardEmptyStateMessage
import com.example.gigs.ui.components.DashboardSectionHeader
import com.example.gigs.utils.DateUtils
import com.example.gigs.utils.DateUtils.formatDate
import com.example.gigs.utils.DateUtils.formatTimeAgo
import com.example.gigs.viewmodel.DashboardNavigationEvent
import com.example.gigs.viewmodel.EmployerApplicationsViewModel
import com.example.gigs.viewmodel.EmployerDashboardViewModel
import com.example.gigs.viewmodel.DashboardCardType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmployerDashboardScreen(
    dashboardViewModel: EmployerDashboardViewModel = hiltViewModel(),
    applicationsViewModel: EmployerApplicationsViewModel = hiltViewModel(),
    onViewAllJobs: () -> Unit,
    onViewAllActivities: () -> Unit,
    onNavigateToAllApplications: () -> Unit,
    onCreateJob: () -> Unit,
    onNavigateToNotifications: () -> Unit,
    onNavigateToMessages: () -> Unit,
    onViewApplication: (String) -> Unit,
    onBackPressed: () -> Unit,
    onNavigateToEditProfile: () -> Unit,
    // 🚀 NEW: Add navigation callbacks for dashboard cards
    onNavigateToMyJobs: ((filter: String, title: String) -> Unit)? = null
) {
    // Collect state from the view models
    val totalJobs by dashboardViewModel.totalJobs.collectAsState()
    val activeJobs by dashboardViewModel.activeJobs.collectAsState()
    val totalApplications by dashboardViewModel.totalApplications.collectAsState()
    val averageRating by dashboardViewModel.averageRating.collectAsState()
    val isLoading by dashboardViewModel.isLoading.collectAsState()
    val recentJobs by dashboardViewModel.recentJobs.collectAsState()
    val recentActivities by dashboardViewModel.recentActivities.collectAsState()
    val locationStats by dashboardViewModel.locationStats.collectAsState()
    val categoryStats by dashboardViewModel.categoryStats.collectAsState()

    // Get applications data
    val recentApplications by applicationsViewModel.recentApplications.collectAsState()
    val isLoadingApplications by applicationsViewModel.isLoading.collectAsState()

    // Get employer profile data
    val employerProfile by dashboardViewModel.employerProfile.collectAsState()
    val isProfileLoading by dashboardViewModel.isProfileLoading.collectAsState()

    // 🚀 NEW: Handle navigation events from ViewModel
    LaunchedEffect(Unit) {
        dashboardViewModel.navigationEvent.collect { event ->
            when (event) {
                is DashboardNavigationEvent.NavigateToMyJobs -> {
                    onNavigateToMyJobs?.invoke(event.filter.name, event.title)
                }
                is DashboardNavigationEvent.NavigateToApplications -> {
                    onNavigateToAllApplications()
                }
            }
        }
    }

    // Load dashboard data when screen is shown
    LaunchedEffect(Unit) {
        println("🚀 DASHBOARD: Loading dashboard data...")
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
                    // 🚀 DEBUG: Add refresh button for testing
                    IconButton(onClick = {
                        dashboardViewModel.refreshDashboard()
                        println("🔄 DASHBOARD: Manual refresh triggered")
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
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
        if ((isLoading && recentJobs.isEmpty()) || isProfileLoading) {
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
                // 🚀 Profile Section
                EmployerProfileSection(
                    name = employerProfile?.companyName ?: "Your Company",
                    location = "${employerProfile?.district ?: "District"}, ${employerProfile?.state ?: "State"}",
                    onEditProfileClick = onNavigateToEditProfile
                )

                Divider(modifier = Modifier.padding(horizontal = 16.dp))

                // Dashboard Section
                Text(
                    text = "Dashboard",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(16.dp)
                )

                // 🚀 UPDATED: Dashboard cards with click handlers using LazyVerticalGrid
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .height(200.dp), // Fixed height for the grid
                    userScrollEnabled = false // Disable scrolling within the grid
                ) {
                    // 🚀 UPDATED: Total Jobs Card - clickable
                    item {
                        DashboardCard(
                            icon = Icons.Default.Work,
                            value = totalJobs.toString(),
                            label = "Total Jobs",
                            onClick = {
                                dashboardViewModel.onDashboardCardClicked(DashboardCardType.TOTAL_JOBS)
                            }
                        )
                    }

                    // 🚀 UPDATED: Active Jobs Card - clickable
                    item {
                        DashboardCard(
                            icon = Icons.Default.CheckCircle,
                            value = activeJobs.toString(),
                            label = "Active Jobs",
                            onClick = {
                                dashboardViewModel.onDashboardCardClicked(DashboardCardType.ACTIVE_JOBS)
                            }
                        )
                    }

                    // 🚀 UPDATED: Applications Card - clickable
                    item {
                        DashboardCard(
                            icon = Icons.Default.Description,
                            value = totalApplications.toString(),
                            label = "Applications",
                            onClick = {
                                dashboardViewModel.onDashboardCardClicked(DashboardCardType.APPLICATIONS)
                            }
                        )
                    }

                    // Rating Card (optional click)
                    item {
                        DashboardCard(
                            icon = Icons.Default.Star,
                            value = String.format("%.1f", averageRating),
                            label = "Rating",
                            onClick = {
                                dashboardViewModel.onDashboardCardClicked(DashboardCardType.RATING)
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Post New Job Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    onClick = onCreateJob
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Post New Job",
                                style = MaterialTheme.typography.titleMedium
                            )

                            Text(
                                text = "Create a new job listing to find candidates",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = "Post new job",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Recent Applications Section
                if (!isLoadingApplications) {
                    DashboardSectionHeader(
                        title = "Recent Applications",
                        onViewAll = onNavigateToAllApplications
                    )

                    if (recentApplications.isEmpty()) {
                        DashboardEmptyStateMessage(
                            message = "No applications yet",
                            actionText = "Post a Job",
                            onActionClick = onCreateJob
                        )
                    } else {
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp)
                        ) {
                            recentApplications.take(3).forEach { application ->
                                ApplicationItem(
                                    application = application,
                                    onClick = { onViewApplication(application.id) }
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Recent Jobs Section
                DashboardSectionHeader(
                    title = "My Recent Jobs",
                    onViewAll = onViewAllJobs
                )

                if (recentJobs.isEmpty()) {
                    DashboardEmptyStateMessage(
                        message = "You haven't posted any jobs yet",
                        actionText = "Post a Job",
                        onActionClick = onCreateJob
                    )
                } else {
                    // Display recent jobs
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        recentJobs.take(3).forEach { job ->
                            JobItem(
                                job = job,
                                onClick = { /* Navigate to job details */ }
                            )

                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }

                // Stats Sections
                if (locationStats.isNotEmpty() || categoryStats.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))

                    if (locationStats.isNotEmpty()) {
                        Text(
                            text = "Applications by Location",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        LocationStatsChart(stats = locationStats)

                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    if (categoryStats.isNotEmpty()) {
                        Text(
                            text = "Applications by Category",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        CategoryStatsChart(stats = categoryStats)
                    }
                }

                // Recent Activities Section
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
                            ActivityItem(activity = activity)
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

/**
 * 🚀 Employer profile section with working edit navigation
 */
@Composable
fun EmployerProfileSection(
    name: String,
    location: String,
    onEditProfileClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Profile logo/avatar
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Business,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Company name
        Text(
            text = name,
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
                text = location,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Edit Profile button with working navigation
        Button(
            onClick = {
                println("🚀 NAVIGATION: Edit profile button clicked")
                onEditProfileClick()
            },
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
                        text = "Posted on ${DateUtils.formatDate(job.createdAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Add "View Details" text button
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = { onClick?.invoke() }) {
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

            Spacer(modifier = Modifier.height(8.dp))

            // Add "View Details" button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = { onClick?.invoke() }) {
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

    if (topLocations.isEmpty()) return

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
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

    if (topCategories.isEmpty()) return

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
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