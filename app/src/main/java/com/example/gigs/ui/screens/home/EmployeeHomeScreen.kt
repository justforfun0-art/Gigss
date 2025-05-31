package com.example.gigs.ui.screens.home

import android.util.Log
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.gigs.data.model.EmployeeProfile
import com.example.gigs.data.model.Job
import com.example.gigs.data.model.JobAlert
import com.example.gigs.data.model.JobWithEmployer
import com.example.gigs.data.model.WorkPreference
import com.example.gigs.data.repository.JobRepository
import com.example.gigs.ui.components.*
import com.example.gigs.ui.screens.dashboard.JobItem
import com.example.gigs.viewmodel.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmployeeHomeScreen(
    authViewModel: AuthViewModel,
    jobRepository: JobRepository, // Add this parameter
    onSignOut: () -> Unit,
    onNavigateToDashboard: () -> Unit,
    onNavigateToJobListing: (String) -> Unit,
    onNavigateToMessages: () -> Unit = {},
    onNavigateToNotifications: () -> Unit = {},
    onNavigateToJobHistory: () -> Unit = {},
    onNavigateToJobDetails: (String) -> Unit = {} // Add this parameter
) {
    var selectedTab by remember { mutableStateOf(0) }
    val profileViewModel: ProfileViewModel = hiltViewModel()
    val jobViewModel: JobViewModel = hiltViewModel()
    val processedJobsViewModel: ProcessedJobsViewModel = hiltViewModel()
    val jobHistoryViewModel: JobHistoryViewModel = hiltViewModel()

    val employeeProfile by profileViewModel.employeeProfile.collectAsState()

    // Load profile when screen launches
    LaunchedEffect(Unit) {
        profileViewModel.getEmployeeProfile()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("GigWork") },
                actions = {
                    // History button in top app bar
                    IconButton(onClick = onNavigateToJobHistory) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = "Job History"
                        )
                    }

                    IconButton(onClick = {
                        authViewModel.signOut()
                        onSignOut()
                    }) {
                        Icon(
                            imageVector = Icons.Default.ExitToApp,
                            contentDescription = "Sign Out"
                        )
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
                    onClick = {
                        selectedTab = 1
                        employeeProfile?.district?.let { district ->
                            onNavigateToJobListing(district)
                        }
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Find Jobs"
                        )
                    },
                    label = { Text("Find Jobs") }
                )

                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = {
                        selectedTab = 2
                        onNavigateToDashboard()
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Profile"
                        )
                    },
                    label = { Text("Profile") }
                )

                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = {
                        selectedTab = 3
                        onNavigateToJobHistory()
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = "Job History"
                        )
                    },
                    label = { Text("History") }
                )
            }
        }
    ) { paddingValues ->
        when (selectedTab) {
            0 -> EnhancedEmployeeHomeTab(
                modifier = Modifier.padding(paddingValues),
                jobViewModel = jobViewModel,
                processedJobsViewModel = processedJobsViewModel,
                jobHistoryViewModel = jobHistoryViewModel,
                jobRepository = jobRepository,
                onJobDetails = onNavigateToJobDetails,
                onViewJobHistory = onNavigateToJobHistory,
                onNavigateToJobListing = onNavigateToJobListing,
                onNavigateToDashboard = onNavigateToDashboard
            )
            1 -> EmployeeJobsTab(
                modifier = Modifier.padding(paddingValues),
                district = employeeProfile?.district ?: "",
                onJobSelected = onNavigateToJobDetails
            )
            2 -> EmployeeProfileTab(
                modifier = Modifier.padding(paddingValues),
                profile = employeeProfile
            )
            3 -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
                LaunchedEffect(Unit) {
                    onNavigateToJobHistory()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnhancedEmployeeHomeTab(
    modifier: Modifier = Modifier,
    jobViewModel: JobViewModel,
    processedJobsViewModel: ProcessedJobsViewModel,
    jobHistoryViewModel: JobHistoryViewModel,
    jobRepository: JobRepository,
    onJobDetails: (String) -> Unit,
    onViewJobHistory: () -> Unit,
    onNavigateToJobListing: (String) -> Unit,
    onNavigateToDashboard: () -> Unit
) {
    val TAG = "EnhancedEmployeeHomeTab"

    // State collections
    val jobs by jobViewModel.jobs.collectAsStateWithLifecycle()
    val featuredJobs by jobViewModel.featuredJobs.collectAsStateWithLifecycle()
    val isLoading by jobViewModel.isLoading.collectAsStateWithLifecycle()
    val employeeProfile by jobViewModel.employeeProfile.collectAsStateWithLifecycle()
    val isShowingRejectedJobs by processedJobsViewModel.isShowingRejectedJobs.collectAsStateWithLifecycle()
    val processedJobIds by processedJobsViewModel.processedJobIds.collectAsStateWithLifecycle()
    val appliedJobIds by processedJobsViewModel.appliedJobIds.collectAsStateWithLifecycle()
    val rejectedJobIds by processedJobsViewModel.rejectedJobIds.collectAsStateWithLifecycle()

    // Local state
    var showJobAlertDialog by remember { mutableStateOf(false) }
    var showLocationSearchDialog by remember { mutableStateOf(false) }
    var showNewJobsSnackbar by remember { mutableStateOf(false) }
    var newJobsCount by remember { mutableStateOf(0) }
    var hasInitialized by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Create JobWithEmployer list
    val jobsWithEmployers = remember(featuredJobs) {
        featuredJobs.map { job ->
            val employerName = job.employerId.takeIf { it.isNotEmpty() }?.let { id ->
                "Employer ${id.takeLast(4)}"
            } ?: "Unknown Employer"
            JobWithEmployer(job, employerName)
        }
    }

    // Lifecycle management
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    Log.d(TAG, "Screen resumed - checking for updates")
                    coroutineScope.launch {
                        // Restore session state
                        processedJobsViewModel.restoreSessionState()

                        // Check for new jobs
                        val district = employeeProfile?.district ?: ""
                        if (district.isNotBlank()) {
                            jobViewModel.checkForNewJobs(district).collect { count ->
                                if (count > 0 && hasInitialized) {
                                    newJobsCount = count
                                    showNewJobsSnackbar = true
                                }
                            }
                        }
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Initial data loading
    LaunchedEffect(Unit) {
        jobViewModel.getEmployeeProfile()
    }

    LaunchedEffect(employeeProfile, isShowingRejectedJobs) {
        if (employeeProfile != null) {
            val employeeDistrict = employeeProfile?.district ?: ""
            if (employeeDistrict.isNotEmpty()) {
                if (isShowingRejectedJobs) {
                    Log.d(TAG, "Loading rejected jobs for district: $employeeDistrict")
                    jobViewModel.getOnlyRejectedJobs(employeeDistrict)
                } else {
                    Log.d(TAG, "Loading regular jobs for district: $employeeDistrict")
                    jobViewModel.getLocalizedFeaturedJobs(employeeDistrict, 10)
                }
                hasInitialized = true
            } else {
                jobViewModel.getFeaturedJobs(10)
            }
        } else {
            jobViewModel.getFeaturedJobs(10)
        }
    }

    // Show snackbar for new jobs
    LaunchedEffect(showNewJobsSnackbar) {
        if (showNewJobsSnackbar) {
            val result = snackbarHostState.showSnackbar(
                message = "$newJobsCount new job${if (newJobsCount > 1) "s" else ""} available!",
                actionLabel = "Refresh",
                duration = SnackbarDuration.Long
            )

            if (result == SnackbarResult.ActionPerformed) {
                val district = employeeProfile?.district ?: ""
                jobViewModel.refreshJobsForDistrict(district)
            }

            showNewJobsSnackbar = false
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (isShowingRejectedJobs) "Reconsidering Jobs" else "Job Opportunities",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        employeeProfile?.district?.let { district ->
                            Text(
                                text = "in $district",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    // Job alerts icon
                    IconButton(
                        onClick = { showJobAlertDialog = true }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = "Job Alerts"
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when {
                    isLoading && featuredJobs.isEmpty() -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator()
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Loading jobs...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    employeeProfile?.district == null -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocationOff,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Location Not Set",
                                    style = MaterialTheme.typography.headlineSmall,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Please complete your profile to see jobs in your area",
                                    style = MaterialTheme.typography.bodyLarge,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(24.dp))
                                Button(
                                    onClick = onNavigateToDashboard
                                ) {
                                    Icon(Icons.Default.Person, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Complete Profile")
                                }
                            }
                        }
                    }

                    else -> {
                        // Job History Button
                        Button(
                            onClick = onViewJobHistory,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = "History",
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                                Text("View Your Job History")
                                if (appliedJobIds.isNotEmpty()) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Badge {
                                        Text(
                                            text = appliedJobIds.size.toString(),
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Swipe instruction text
                        Text(
                            text = "Swipe right to apply, left to reject",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Use the existing SwipeableJobCards component
                        SwipeableJobCards(
                            jobs = featuredJobs,
                            jobsWithEmployers = jobsWithEmployers,
                            onJobAccepted = { job ->
                                coroutineScope.launch {
                                    jobViewModel.applyForJob(job.id)
                                    snackbarHostState.showSnackbar(
                                        message = "Applied for: ${job.title}"
                                    )
                                }
                            },
                            onJobRejected = { job ->
                                coroutineScope.launch {
                                    jobViewModel.markJobAsNotInterested(job.id)
                                    snackbarHostState.showSnackbar(
                                        message = "Job rejected: ${job.title}"
                                    )
                                }
                            },
                            onJobDetails = onJobDetails,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Floating action button
            if (!isShowingRejectedJobs && featuredJobs.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = {
                        employeeProfile?.district?.let { district ->
                            onNavigateToJobListing(district)
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Icon(
                        imageVector = Icons.Default.List,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("View All Jobs")
                }
            }
        }
    }

    // Job Alert Dialog
    if (showJobAlertDialog) {
        JobAlertDialog(
            currentDistrict = employeeProfile?.district ?: "",
            onDismiss = { showJobAlertDialog = false },
            onCreateAlert = { alert ->
                coroutineScope.launch {
                    jobRepository.createJobAlert(alert).collect { result ->
                        if (result.isSuccess) {
                            snackbarHostState.showSnackbar(
                                message = "Job alert created successfully!",
                                duration = SnackbarDuration.Short
                            )
                            showJobAlertDialog = false
                        } else {
                            snackbarHostState.showSnackbar(
                                message = "Failed to create job alert",
                                duration = SnackbarDuration.Short
                            )
                        }
                    }
                }
            }
        )
    }

    // Location Search Dialog
    if (showLocationSearchDialog) {
        LocationSearchDialog(
            currentDistrict = employeeProfile?.district ?: "",
            onDismiss = { showLocationSearchDialog = false },
            onLocationSelected = { newDistrict ->
                showLocationSearchDialog = false
                onNavigateToJobListing(newDistrict)
            }
        )
    }
}

// Keep the existing EmployeeJobsTab and EmployeeProfileTab components unchanged
@Composable
fun EmployeeJobsTab(
    modifier: Modifier = Modifier,
    district: String,
    onJobSelected: (String) -> Unit
) {
    val jobViewModel: JobViewModel = hiltViewModel()
    val jobs by jobViewModel.jobs.collectAsState()
    val isLoading by jobViewModel.isLoading.collectAsState()

    LaunchedEffect(district) {
        if (district.isNotEmpty()) {
            jobViewModel.getJobsByDistrict(district)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        GigWorkHeaderText(text = "Jobs in $district")

        Spacer(modifier = Modifier.height(16.dp))

        if (isLoading) {
            CircularProgressIndicator()
        } else if (jobs.isEmpty()) {
            Text(
                text = "No jobs available in your area at the moment.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
        } else {
            LazyColumn {
                items(jobs) { job ->
                    JobItem(
                        job = job,
                        onClick = { onJobSelected(job.id) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
fun EmployeeProfileTab(
    modifier: Modifier = Modifier,
    profile: EmployeeProfile?
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        GigWorkHeaderText(text = "Your Profile")

        Spacer(modifier = Modifier.height(24.dp))

        if (profile == null) {
            CircularProgressIndicator()
        } else {
            // Profile photo
            if (profile.profilePhotoUrl != null) {
                AsyncImage(
                    model = profile.profilePhotoUrl,
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
                text = profile.name,
                style = MaterialTheme.typography.headlineSmall
            )

            profile.email?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            ProfileSection(
                title = "Personal Details",
                content = {
                    ProfileRow("Gender", profile.gender.toString())
                    ProfileRow("Location", "${profile.district}, ${profile.state}")
                    ProfileRow("Computer Knowledge", if (profile.hasComputerKnowledge) "Yes" else "No")
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            ProfileSection(
                title = "Work Preferences",
                content = {
                    ProfileRow("Work Types", profile.workPreferences.joinToString(", ") {
                        it.toString().replace("_", " ")
                    })
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { /* Navigate to edit profile */ },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Edit Profile")
            }
        }
    }
}

@Composable
fun ProfileSection(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                content()
            }
        }
    }
}

@Composable
fun ProfileRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

/**
 * Simple location search dialog
 */
@Composable
fun LocationSearchDialog(
    currentDistrict: String,
    onDismiss: () -> Unit,
    onLocationSelected: (String) -> Unit
) {
    val nearbyDistricts = remember {
        listOf(
            "Hisar", "Rohtak", "Karnal", "Panipat",
            "Sonipat", "Kaithal", "Kurukshetra", "Fatehabad",
            "Bhiwani", "Jhajjar", "Rewari", "Mahendragarh"
        ).filter { it != currentDistrict }.sorted()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Search Nearby Areas")
        },
        text = {
            Column {
                Text(
                    "Select a nearby district to search for jobs:",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    nearbyDistricts.chunked(2).forEach { row ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            row.forEach { district ->
                                SuggestionChip(
                                    onClick = { onLocationSelected(district) },
                                    label = { Text(district) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (row.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}