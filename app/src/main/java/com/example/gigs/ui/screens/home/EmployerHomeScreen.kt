// EmployerHomeScreen.kt - Updated with Selected Candidates section as requested

package com.example.gigs.ui.screens.home

import android.util.Log
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
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.example.gigs.data.model.ApplicationStatus
import com.example.gigs.data.model.ApplicationWithJob
import com.example.gigs.data.model.Job
import com.example.gigs.data.model.JobStatus
import com.example.gigs.navigation.AdminButton
import com.example.gigs.ui.components.GigWorkHeaderText
import com.example.gigs.ui.components.GigWorkSubtitleText
import com.example.gigs.ui.components.OtpDisplayDialog
import com.example.gigs.ui.screens.dashboard.JobItem
import com.example.gigs.utils.DateUtils
import com.example.gigs.viewmodel.AuthViewModel
import com.example.gigs.viewmodel.DashboardCardType
import com.example.gigs.viewmodel.DashboardNavigationEvent
import com.example.gigs.viewmodel.EmployerApplicationsViewModel
import com.example.gigs.viewmodel.EmployerDashboardViewModel
import com.example.gigs.viewmodel.JobViewModel
import com.example.gigs.viewmodel.JobsFilter
import com.example.gigs.viewmodel.ProfileViewModel
import androidx.compose.ui.unit.sp

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
    onViewAllApplications: () -> Unit,
    onNavigateToActiveJobs: (Int) -> Unit = {},
    onNavigateToAllJobs: (Int) -> Unit = {}
) {
    var selectedTab by remember { mutableStateOf(0) }
    val jobViewModel: JobViewModel = hiltViewModel()
    val profileViewModel: ProfileViewModel = hiltViewModel()
    val applicationsViewModel: EmployerApplicationsViewModel = hiltViewModel()

    // 🚀 FIX: Add selectedTabChanged state to control when to switch tabs
    var shouldSwitchToSelectedTab by remember { mutableStateOf(false) }

    // Load employer profile when the screen launches
    LaunchedEffect(Unit) {
        profileViewModel.getEmployerProfile()
        applicationsViewModel.loadRecentApplications(50) // Load more to filter selected ones
    }

    // 🚀 FIX: Handle tab switching when "View All Selected" is clicked
    LaunchedEffect(shouldSwitchToSelectedTab) {
        if (shouldSwitchToSelectedTab) {
            selectedTab = 2 // Switch to Selected Candidates tab
            shouldSwitchToSelectedTab = false
        }
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

                // Selected Candidates tab
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Groups,
                            contentDescription = "Selected"
                        )
                    },
                    label = { Text("Selected") }
                )

                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
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
                applicationsViewModel = applicationsViewModel,
                onNavigateToDashboard = onNavigateToDashboard,
                onNavigateToAdminDashboard = onNavigateToAdminDashboard,
                onViewApplications = onViewAllApplications,
                onNavigateToActiveJobs = onNavigateToActiveJobs,
                onNavigateToAllJobs = onNavigateToAllJobs,
                // 🚀 FIX: Add callback to switch to Selected tab
                onViewAllSelected = { shouldSwitchToSelectedTab = true }
            )
            1 -> EmployerJobsTab(
                modifier = Modifier.padding(paddingValues),
                jobViewModel = jobViewModel,
                jobs = jobs,
                isLoading = isLoading,
                onJobSelected = onNavigateToJobDetails,
                onViewApplicationsForJob = { jobId, jobTitle ->
                    // Navigate to job applications screen for this specific job
                }
            )
            2 -> SelectedCandidatesTab(
                modifier = Modifier.padding(paddingValues),
                applicationsViewModel = applicationsViewModel,
                onViewApplicantProfile = { /* Navigate to profile */ }
            )
            3 -> EmployerProfileTab(
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
    applicationsViewModel: EmployerApplicationsViewModel = hiltViewModel(),
    onNavigateToDashboard: () -> Unit,
    onNavigateToAdminDashboard: () -> Unit,
    onViewApplications: () -> Unit,
    onNavigateToActiveJobs: (Int) -> Unit = {},
    onNavigateToAllJobs: (Int) -> Unit = {},
    // 🚀 FIX: Add callback for "View All Selected"
    onViewAllSelected: () -> Unit = {}
) {
    // Get dashboard data from the view model
    val dashboardData by dashboardViewModel.dashboardData.collectAsState()
    val totalJobs by dashboardViewModel.totalJobs.collectAsState()
    val activeJobs by dashboardViewModel.activeJobs.collectAsState()
    val totalApplications by dashboardViewModel.totalApplications.collectAsState()
    val isLoading by dashboardViewModel.isLoading.collectAsState()

    // Get selected candidates
    val recentApplications by applicationsViewModel.recentApplications.collectAsState()
    val selectedCandidates = remember(recentApplications) {
        recentApplications.filter { it.status == ApplicationStatus.SELECTED }
    }

    // Handle navigation events from dashboard ViewModel
    LaunchedEffect(Unit) {
        dashboardViewModel.navigationEvent.collect { event ->
            when (event) {
                is DashboardNavigationEvent.NavigateToMyJobs -> {
                    when (event.filter) {
                        JobsFilter.ACTIVE_ONLY -> onNavigateToActiveJobs(activeJobs)
                        JobsFilter.ALL_JOBS -> onNavigateToAllJobs(totalJobs)
                    }
                }
                is DashboardNavigationEvent.NavigateToApplications -> {
                    onViewApplications()
                }
            }
        }
    }

    // Add lifecycle awareness to refresh data when screen becomes visible
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            dashboardViewModel.loadDashboardData()
            applicationsViewModel.loadRecentApplications(50)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
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

        // 🚀 FIXED: Selected Candidates Section
        if (selectedCandidates.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Selected Candidates",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )

                        Box(
                            modifier = Modifier
                                .background(
                                    MaterialTheme.colorScheme.secondary,
                                    CircleShape
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "${selectedCandidates.size}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Show first 3 selected candidates
                    selectedCandidates.take(3).forEach { application ->
                        SelectedCandidateItem(
                            application = application,
                            applicationsViewModel = applicationsViewModel,
                            onViewProfile = { /* Navigate to profile */ },
                            // 🚀 FIX: Pass false to prevent auto-popup on home screen
                            shouldShowOtpDialog = false
                        )

                        if (application != selectedCandidates.take(3).last()) {
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }

                    if (selectedCandidates.size > 3) {
                        Spacer(modifier = Modifier.height(8.dp))

                        TextButton(
                            // 🚀 FIX: Use the callback to switch to Selected tab
                            onClick = onViewAllSelected,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Text("View All ${selectedCandidates.size} Selected")
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

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

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Loading latest data...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        // Active Jobs stat
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable {
                                dashboardViewModel.onDashboardCardClicked(DashboardCardType.ACTIVE_JOBS)
                            }
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
                                text = "$activeJobs",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        // Applications stat
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable {
                                dashboardViewModel.onDashboardCardClicked(DashboardCardType.APPLICATIONS)
                            }
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
                                text = "$totalApplications",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }

                        // Selected Candidates stat
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.tertiaryContainer,
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Groups,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "Selected",
                                style = MaterialTheme.typography.bodyMedium
                            )

                            Text(
                                text = "${selectedCandidates.size}",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Quick refresh button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            TextButton(
                onClick = {
                    dashboardViewModel.refreshDashboard()
                    applicationsViewModel.loadRecentApplications(50)
                },
                enabled = !isLoading
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Refresh Data")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

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
// SelectedCandidateItem - FIXED OTP Generation and Display


@Composable
fun SelectedCandidateItem(
    application: ApplicationWithJob,
    applicationsViewModel: EmployerApplicationsViewModel,
    onViewProfile: () -> Unit,
    shouldShowOtpDialog: Boolean = false
) {
    // 🚀 CRITICAL FIX: Use local state for dialog, not ViewModel state
    var showOtpDialog by remember { mutableStateOf(false) }

    // 🚀 FIXED: Observe state properly with remember for stable references
    val applicationOtps by applicationsViewModel.applicationOtps.collectAsState()
    val applicationWorkSessions by applicationsViewModel.applicationWorkSessions.collectAsState()
    val applicationLoadingStates by applicationsViewModel.applicationLoadingStates.collectAsState()
    val applicationErrors by applicationsViewModel.applicationOtpErrors.collectAsState()

    // 🚀 CRITICAL FIX: Use remember to prevent constant recomputation
    val currentOtp = remember(applicationOtps, application.id) {
        applicationOtps[application.id]?.also {
            Log.d("SelectedCandidateItem", "🔍 Current OTP for ${application.id}: $it")
        }
    }

    val currentWorkSession = remember(applicationWorkSessions, application.id) {
        applicationWorkSessions[application.id]?.also {
            Log.d("SelectedCandidateItem", "🔍 Current work session for ${application.id}: ${it.status}")
        }
    }

    val isCurrentlyLoading = remember(applicationLoadingStates, application.id) {
        applicationLoadingStates[application.id] ?: false
    }

    val currentError = remember(applicationErrors, application.id) {
        applicationErrors[application.id]
    }

    // 🚀 FIXED: Only load work session once when component mounts
    LaunchedEffect(application.id) {
        Log.d("SelectedCandidateItem", "🔍 LaunchedEffect: Loading work session for ${application.id}")
        if (currentWorkSession == null && application.status == ApplicationStatus.SELECTED) {
            applicationsViewModel.getWorkSessionForApplication(application.id)
        }
    }

    // 🚀 CRITICAL FIX: Remove automatic dialog triggering
    // LaunchedEffect(currentOtp) - REMOVED to prevent auto-popup

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = application.employeeId.take(1).uppercase(),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSecondary,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Column {
                        Text(
                            text = "Applicant ${application.employeeId.takeLast(5)}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )

                        Text(
                            text = application.job.title,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Text(
                            text = "${application.job.district}, ${application.job.state}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // 🚀 FIXED: OTP Button Logic with explicit dialog control
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    when {
                        isCurrentlyLoading -> {
                            // Show loading state
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Generating...",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }

                        currentOtp != null -> {
                            // Show "View OTP" button if OTP exists
                            TextButton(
                                onClick = {
                                    Log.d("SelectedCandidateItem", "📱 View OTP clicked for ${application.id}")
                                    showOtpDialog = true // 🚀 Use local state
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    "View OTP",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }

                        else -> {
                            // Show "Generate OTP" button
                            TextButton(
                                onClick = {
                                    Log.d("SelectedCandidateItem", "🚀 Generate OTP clicked for ${application.id}")
                                    // 🚀 FIXED: Generate OTP without auto-showing dialog
                                    applicationsViewModel.generateOtpSilently(application.id)
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    "Gen OTP",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
            }

            // 🚀 FIXED: Show inline OTP display when available
            if (currentOtp != null) {
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "OTP: $currentOtp",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Show error if any for this application
            currentError?.let { error ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }

    // 🚀 FIXED: OTP Display Dialog - only shows when explicitly requested
    if (showOtpDialog && currentOtp != null) {
        OtpDisplayDialog(
            otp = currentOtp,
            employeeName = "Applicant ${application.employeeId.takeLast(5)}",
            jobTitle = application.job.title,
            expiryTime = currentWorkSession?.otpExpiry,
            onDismiss = {
                showOtpDialog = false // 🚀 Use local state
            },
            onRegenerateOtp = {
                showOtpDialog = false // 🚀 Use local state
                Log.d("SelectedCandidateItem", "🔄 Regenerate OTP clicked for ${application.id}")
                applicationsViewModel.generateOtpSilently(application.id)
            }
        )
    }
}


@Composable
fun EmployerJobsTab(
    modifier: Modifier = Modifier,
    jobViewModel: JobViewModel,
    jobs: List<Job>,
    isLoading: Boolean,
    onJobSelected: (String) -> Unit,
    onViewApplicationsForJob: (String, String) -> Unit
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

// 🚀 NEW: Selected Candidates Tab
@Composable
fun SelectedCandidatesTab(
    modifier: Modifier = Modifier,
    applicationsViewModel: EmployerApplicationsViewModel,
    onViewApplicantProfile: (String) -> Unit
) {
    val recentApplications by applicationsViewModel.recentApplications.collectAsState()
    val isLoading by applicationsViewModel.isLoading.collectAsState()

    // Filter only selected candidates
    val selectedCandidates = remember(recentApplications) {
        recentApplications.filter { it.status == ApplicationStatus.SELECTED }
    }
    LaunchedEffect(Unit) {
        applicationsViewModel.loadRecentApplications(100) // Load more for this dedicated view
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            GigWorkHeaderText(text = "Selected Candidates")

            if (selectedCandidates.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .background(
                            MaterialTheme.colorScheme.secondary,
                            CircleShape
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "${selectedCandidates.size}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSecondary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (selectedCandidates.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Groups,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "No Selected Candidates",
                        style = MaterialTheme.typography.titleLarge
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "When you select candidates for jobs, they'll appear here with OTP generation options",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(selectedCandidates) { application ->
                    SelectedCandidateDetailedItem(
                        application = application,
                        applicationsViewModel = applicationsViewModel,
                        onViewProfile = { onViewApplicantProfile(application.employeeId) }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
fun SelectedCandidateDetailedItem(
    application: ApplicationWithJob,
    applicationsViewModel: EmployerApplicationsViewModel,
    onViewProfile: () -> Unit
) {
    // 🚀 CRITICAL FIX: Use local state for dialog control
    var showOtpDialog by remember { mutableStateOf(false) }

    // 🚀 FIX: Use application-specific OTP data
    val applicationOtps by applicationsViewModel.applicationOtps.collectAsState()
    val applicationWorkSessions by applicationsViewModel.applicationWorkSessions.collectAsState()
    val applicationLoadingStates by applicationsViewModel.applicationLoadingStates.collectAsState()
    val applicationErrors by applicationsViewModel.applicationOtpErrors.collectAsState()

    // Get data for this specific application
    val currentOtp = remember(applicationOtps, application.id) {
        applicationOtps[application.id]
    }
    val currentWorkSession = remember(applicationWorkSessions, application.id) {
        applicationWorkSessions[application.id]
    }
    val isCurrentlyLoading = remember(applicationLoadingStates, application.id) {
        applicationLoadingStates[application.id] ?: false
    }
    val currentError = remember(applicationErrors, application.id) {
        applicationErrors[application.id]
    }

    // Only load work session once per application
    LaunchedEffect(application.id) {
        if (currentWorkSession == null && application.status == ApplicationStatus.SELECTED) {
            applicationsViewModel.getWorkSessionForApplication(application.id)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header with candidate info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = application.employeeId.take(1).uppercase(),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSecondary,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = "Applicant ${application.employeeId.takeLast(5)}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = "Selected ${application.appliedAt?.let { DateUtils.formatDate(it) } ?: "Recently"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Status badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "SELECTED",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Job information
            Text(
                text = application.job.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )

            Text(
                text = "${application.job.district}, ${application.job.state}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (application.job.salaryRange != null) {
                Text(
                    text = "Salary: ${application.job.salaryRange}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action buttons row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(
                    onClick = onViewProfile,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("View Profile")
                }

                Spacer(modifier = Modifier.width(8.dp))

                // 🚀 FIXED: OTP Generation Button with proper dialog control
                if (currentOtp != null) {
                    Button(
                        onClick = {
                            showOtpDialog = true // 🚀 Use local state
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("View OTP")
                    }
                } else {
                    Button(
                        onClick = {
                            // 🚀 FIXED: Generate OTP without auto-showing dialog
                            applicationsViewModel.generateOtpSilently(application.id)
                        },
                        enabled = !isCurrentlyLoading,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary
                        )
                    ) {
                        if (isCurrentlyLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onSecondary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Generate OTP")
                        }
                    }
                }
            }

            // 🚀 FIX: Show inline OTP only for this specific application
            if (currentOtp != null && currentWorkSession != null) {
                Spacer(modifier = Modifier.height(12.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Work Session OTP",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = currentOtp,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 4.sp
                            )
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Timer,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "Expires in 30 minutes",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Show OTP error if any for this application
            currentError?.let { error ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }

    // 🚀 FIXED: OTP Display Dialog - only shows when explicitly requested
    if (showOtpDialog && currentOtp != null) {
        OtpDisplayDialog(
            otp = currentOtp,
            employeeName = "Applicant ${application.employeeId.takeLast(5)}",
            jobTitle = application.job.title,
            expiryTime = currentWorkSession?.otpExpiry,
            onDismiss = {
                showOtpDialog = false // 🚀 Use local state
            },
            onRegenerateOtp = {
                showOtpDialog = false // 🚀 Use local state
                applicationsViewModel.generateOtpSilently(application.id)
            }
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
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    // Company Name
                    Text(
                        text = employerProfile.companyName ?: "Company Name",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Industry
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Business,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Industry",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = employerProfile.industry ?: "Not specified",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Location
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Location",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${employerProfile.district ?: "District"}, ${employerProfile.state ?: "State"}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Company Size
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.People,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Company Size",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = employerProfile.companySize ?: "Not specified",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    // Website (if available)
                    if (!employerProfile.website.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Web,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Website",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = employerProfile.website,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    // Company Description (if available)
                    if (!employerProfile.description.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(16.dp))

                        HorizontalDivider(
                            thickness = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "About Company",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = employerProfile.description,
                            style = MaterialTheme.typography.bodyMedium,
                            lineHeight = 20.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Edit Profile Button
            Button(
                onClick = onEditProfile,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Edit Profile",
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}