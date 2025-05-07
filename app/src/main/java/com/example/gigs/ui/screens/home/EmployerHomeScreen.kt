package com.example.gigs.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.gigs.data.model.Job
import com.example.gigs.data.model.JobStatus
import com.example.gigs.navigation.AdminButton
import com.example.gigs.ui.components.GigWorkHeaderText
import com.example.gigs.ui.components.GigWorkSubtitleText
import com.example.gigs.ui.screens.dashboard.JobItem
import com.example.gigs.viewmodel.AuthViewModel
import com.example.gigs.viewmodel.EmployerDashboardViewModel
import com.example.gigs.viewmodel.JobViewModel
import com.example.gigs.viewmodel.ProfileViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmployerHomeScreen(
    authViewModel: AuthViewModel,
    dashboardViewModel: EmployerDashboardViewModel = hiltViewModel(),
    onSignOut: () -> Unit,
    onNavigateToDashboard: () -> Unit,
    onNavigateToCreateJob: () -> Unit,
    onNavigateToAdminDashboard: () -> Unit,
    onNavigateToJobDetails: (String) -> Unit,
    onNavigateToEditProfile: () -> Unit,
    onViewAllApplications: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    val jobViewModel: JobViewModel = hiltViewModel()
    val profileViewModel: ProfileViewModel = hiltViewModel()

    // Load employer profile when the screen launches
    LaunchedEffect(Unit) {
        profileViewModel.getEmployerProfile()
        // Load recent jobs for the Jobs tab
        jobViewModel.getMyJobs(30) // increase the limit to get more jobs
    }

    val employerProfile by profileViewModel.employerProfile.collectAsState()
    val jobs by jobViewModel.jobs.collectAsState()
    val isLoading by jobViewModel.isLoading.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("GigWork - Employer") },
                actions = {
                    IconButton(onClick = {
                        authViewModel.signOut()
                        onSignOut()
                    }) {
                        Icon(
                            imageVector = Icons.Default.ExitToApp,
                            contentDescription = "Sign Out"
                        )
                    }
                },
                navigationIcon = {
                    if (selectedTab != 0) {
                        IconButton(onClick = { selectedTab = 0 }) {
                            Icon(
                                Icons.Default.ArrowBack,
                                contentDescription = "Back to Home"
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Home,
                            contentDescription = "Home"
                        )
                    },
                    label = { Text("Home") }
                )

                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Work,
                            contentDescription = "My Jobs"
                        )
                    },
                    label = { Text("My Jobs") }
                )

                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Profile"
                        )
                    },
                    label = { Text("Profile") }
                )
            }
        },
        floatingActionButton = {
            if (selectedTab == 1) {
                FloatingActionButton(
                    onClick = { onNavigateToCreateJob() }
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Create Job"
                    )
                }
            }
        }
    ) { paddingValues ->
        when (selectedTab) {
            0 -> EmployerHomeTab(
                modifier = Modifier.padding(paddingValues),
                authViewModel = authViewModel,
                dashboardViewModel = dashboardViewModel,
                onNavigateToDashboard = onNavigateToDashboard,
                onNavigateToAdminDashboard = onNavigateToAdminDashboard,
                onViewApplications = onViewAllApplications
            )
            1 -> EmployerJobsTab(
                modifier = Modifier.padding(paddingValues),
                jobViewModel = jobViewModel,
                jobs = jobs,
                isLoading = isLoading,
                onJobSelected = onNavigateToJobDetails,
                onViewApplicationsForJob = { jobId, jobTitle ->
                    // Navigate to job applications screen for this specific job
                    // This would be implemented in your navigation graph
                }
            )
            2 -> EmployerProfileTab(
                modifier = Modifier.padding(paddingValues),
                profileViewModel = profileViewModel,
                employerProfile = employerProfile,
                onEditProfile = onNavigateToEditProfile
            )
        }
    }
}

@Composable
fun EmployerHomeTab(
    modifier: Modifier = Modifier,
    authViewModel: AuthViewModel,
    dashboardViewModel: EmployerDashboardViewModel = hiltViewModel(),
    onNavigateToDashboard: () -> Unit,
    onNavigateToAdminDashboard: () -> Unit,
    onViewApplications: () -> Unit
) {
    // Get dashboard data from the view model
    val dashboardData by dashboardViewModel.dashboardData.collectAsState()
    val isLoading by dashboardViewModel.isLoading.collectAsState()

    // Load dashboard data when screen is shown
    LaunchedEffect(Unit) {
        dashboardViewModel.loadDashboardData()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        GigWorkHeaderText(text = "Welcome to GigWork!")

        Spacer(modifier = Modifier.height(8.dp))

        GigWorkSubtitleText(
            text = "Post jobs and find qualified candidates for your business"
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Statistics Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(4.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Quick Overview",
                    style = MaterialTheme.typography.titleLarge
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        // Active Jobs stat - make clickable
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable { onNavigateToDashboard() }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.WorkOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "Active Jobs",
                                style = MaterialTheme.typography.bodyMedium
                            )

                            Text(
                                text = "${dashboardData?.activeJobs ?: 0}",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        // Applications stat - make clickable
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable { onViewApplications() }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Description,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "Applications",
                                style = MaterialTheme.typography.bodyMedium
                            )

                            Text(
                                text = "${dashboardData?.totalApplicationsReceived ?: 0}",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Dashboard button
        Button(
            onClick = onNavigateToDashboard,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(
                imageVector = Icons.Default.Dashboard,
                contentDescription = "Dashboard Icon",
                modifier = Modifier.padding(end = 8.dp)
            )
            Text("View Full Dashboard")
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Admin button (if user is admin)
        AdminButton(
            authViewModel = authViewModel,
            onNavigateToAdminDashboard = onNavigateToAdminDashboard
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnhancedJobItem(
    job: Job,
    onClick: () -> Unit,
    onViewApplications: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        ),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Status chip
            val (statusColor, statusText) = when (job.status) {
                JobStatus.APPROVED -> Pair(MaterialTheme.colorScheme.primary, "Approved")
                JobStatus.PENDING_APPROVAL -> Pair(Color(0xFF2196F3), "Pending Approval")
                JobStatus.REJECTED -> Pair(MaterialTheme.colorScheme.error, "Rejected")
                else -> Pair(MaterialTheme.colorScheme.outline, "Closed")
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = job.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Box(
                    modifier = Modifier
                        .padding(start = 8.dp)
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

            Spacer(modifier = Modifier.height(8.dp))

            // Location and Salary
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    Text(
                        text = "${job.district}, ${job.state}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = job.salaryRange ?: "Not specified",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Description preview
            if (!job.description.isNullOrBlank()) {
                Text(
                    text = job.description,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )

                Spacer(modifier = Modifier.height(8.dp))
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            // Applications button
            TextButton(
                onClick = onViewApplications,
                modifier = Modifier.align(Alignment.End)
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )

                Spacer(modifier = Modifier.width(4.dp))

                Text("View Applications")
            }
        }
    }
}


@Composable
fun EmployerJobsTab(
    modifier: Modifier = Modifier,
    jobViewModel: JobViewModel,
    jobs: List<Job>,
    isLoading: Boolean,
    onJobSelected: (String) -> Unit,
    onViewApplicationsForJob: (String, String) -> Unit // New parameter: jobId, jobTitle
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        GigWorkHeaderText(text = "My Job Postings")

        Spacer(modifier = Modifier.height(16.dp))

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (jobs.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.WorkOutline,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "No Jobs Posted Yet",
                        style = MaterialTheme.typography.titleLarge
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Tap the + button to create a new job posting",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn {
                items(jobs) { job ->
                    EnhancedJobItem(
                        job = job,
                        onClick = { onJobSelected(job.id) },
                        onViewApplications = {
                            onViewApplicationsForJob(job.id, job.title)
                        }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
fun EmployerProfileTab(
    modifier: Modifier = Modifier,
    profileViewModel: ProfileViewModel,
    employerProfile: com.example.gigs.data.model.EmployerProfile?,
    onEditProfile: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        GigWorkHeaderText(text = "Company Profile")

        Spacer(modifier = Modifier.height(16.dp))

        if (employerProfile == null) {
            CircularProgressIndicator()
        } else {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = employerProfile.companyName ?: "Company Name",
                        style = MaterialTheme.typography.headlineSmall
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Business,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = employerProfile.industry ?: "Industry",
                            style = MaterialTheme.typography.bodyMedium
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
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${employerProfile.district ?: ""}, ${employerProfile.state ?: ""}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.People,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = employerProfile.companySize ?: "Company Size",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    if (employerProfile.website != null) {
                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Web,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = employerProfile.website ?: "",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    if (!employerProfile.description.isNullOrEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "About",
                            style = MaterialTheme.typography.titleMedium
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = employerProfile.description ?: "",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onEditProfile,
                modifier = Modifier.fillMaxWidth()
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
}