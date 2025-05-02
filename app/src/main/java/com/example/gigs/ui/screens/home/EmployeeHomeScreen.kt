package com.example.gigs.ui.screens.home

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.gigs.data.model.EmployeeProfile
import com.example.gigs.data.model.Job
import com.example.gigs.data.model.JobWithEmployer
import com.example.gigs.ui.components.GigWorkHeaderText
import com.example.gigs.ui.components.GigWorkSubtitleText
import com.example.gigs.ui.components.SwipeableJobCards
import com.example.gigs.ui.screens.dashboard.JobItem
import com.example.gigs.viewmodel.AuthViewModel
import com.example.gigs.viewmodel.JobViewModel
import com.example.gigs.viewmodel.ProfileViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmployeeHomeScreen(
    authViewModel: AuthViewModel,
    onSignOut: () -> Unit,
    onNavigateToDashboard: () -> Unit,
    onNavigateToJobListing: (String) -> Unit,
    onNavigateToMessages: () -> Unit = {}, // Optional with default
    onNavigateToNotifications: () -> Unit = {}, // Optional with default
    onNavigateToJobHistory: () -> Unit = {} // Added parameter for job history navigation
) {
    var selectedTab by remember { mutableStateOf(0) }
    val profileViewModel: ProfileViewModel = hiltViewModel()
    val jobViewModel: JobViewModel = hiltViewModel()

    // Get employee profile to access district for job search
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
                        // Navigate to job listings with current district
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

                // Add History tab to bottom navigation
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
            0 -> EmployeeHomeTab(
                modifier = Modifier.padding(paddingValues),
                jobViewModel = jobViewModel,
                onJobDetails = { jobId -> onNavigateToJobListing(jobId) },
                onViewJobHistory = onNavigateToJobHistory  // Pass job history navigation
            )
            1 -> EmployeeJobsTab(
                modifier = Modifier.padding(paddingValues),
                district = employeeProfile?.district ?: "",
                onJobSelected = { jobId -> onNavigateToJobListing(jobId) }
            )
            2 -> EmployeeProfileTab(
                modifier = Modifier.padding(paddingValues),
                profile = employeeProfile
            )
            3 -> {
                // You could handle in-page content, but we're navigating away
                // So this is just a placeholder in case tab state is 3 but navigation hasn't happened yet
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }

                // Trigger navigation to job history
                LaunchedEffect(Unit) {
                    onNavigateToJobHistory()
                }
            }
        }
    }
}

@Composable
fun EmployeeHomeTab(
    modifier: Modifier = Modifier,
    jobViewModel: JobViewModel,
    onJobDetails: (String) -> Unit,
    onViewJobHistory: () -> Unit
) {
    val featuredJobs by jobViewModel.featuredJobs.collectAsState()
    val isLoading by jobViewModel.isLoading.collectAsState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val employeeProfile by jobViewModel.employeeProfile.collectAsState()

    // Create JobWithEmployer list by mapping the featuredJobs
    val jobsWithEmployers = remember(featuredJobs) {
        featuredJobs.map { job ->
            // Use the employerId to create a readable employer name
            // This is a placeholder - ideally, you would fetch real employer names
            val employerName = job.employerId.takeIf { it.isNotEmpty() }?.let { id ->
                // For now, just create a formatted name from the ID
                "Employer ${id.takeLast(4)}"
            } ?: "Unknown Employer"

            JobWithEmployer(job, employerName)
        }
    }

    // Load featured jobs and employee profile
    LaunchedEffect(Unit) {
        // First, load the employee profile to get their district
        jobViewModel.getEmployeeProfile()
    }

    // When the employee profile changes, load jobs for their district
    LaunchedEffect(employeeProfile) {
        if (employeeProfile != null) {
            val employeeDistrict = employeeProfile?.district ?: ""
            if (employeeDistrict.isNotEmpty()) {
                jobViewModel.getLocalizedFeaturedJobs(employeeDistrict, 10)
            } else {
                // Fallback to getting general featured jobs if no district is set
                jobViewModel.getFeaturedJobs(10)
            }
        } else {
            // If profile isn't loaded yet, get general featured jobs
            jobViewModel.getFeaturedJobs(10)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            GigWorkHeaderText(text = "Welcome to GigWork!")

            Spacer(modifier = Modifier.height(8.dp))

            GigWorkSubtitleText(
                text = "Find local jobs that match your skills and availability"
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Add Job History Button
            Button(
                onClick = onViewJobHistory,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = "History",
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text("View Your Job History")
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Featured Jobs with Tinder-style swiping
            Text(
                text = employeeProfile?.let { "Jobs in ${it.district}" } ?: "Featured Jobs",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.align(Alignment.Start)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Swipe instruction text
            Text(
                text = "Swipe right to apply, left to reject",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            } else if (featuredJobs.isEmpty()) {
                Text(
                    text = "No jobs available in your area at the moment.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
            } else {
                // Tinder-style swipeable job cards
                SwipeableJobCards(
                    jobs = featuredJobs,
                    jobsWithEmployers = jobsWithEmployers, // Pass the employer info
                    onJobAccepted = { job ->
                        // Apply for job
                        scope.launch {
                            jobViewModel.applyForJob(job.id)
                            snackbarHostState.showSnackbar(
                                message = "Applied for: ${job.title}"
                            )
                        }
                    },
                    onJobRejected = { job ->
                        // Reject job
                        scope.launch {
                            jobViewModel.markJobAsNotInterested(job.id)
                            snackbarHostState.showSnackbar(
                                message = "Job rejected: ${job.title}"
                            )
                        }
                    },
                    onJobDetails = { jobId -> onJobDetails(jobId) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Snackbar host at the bottom
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        )
    }
}

@Composable
fun EmployeeJobsTab(
    modifier: Modifier = Modifier,
    district: String,
    onJobSelected: (String) -> Unit
) {
    val jobViewModel: JobViewModel = hiltViewModel()
    val jobs by jobViewModel.jobs.collectAsState()
    val isLoading by jobViewModel.isLoading.collectAsState()

    // Load jobs for the district
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