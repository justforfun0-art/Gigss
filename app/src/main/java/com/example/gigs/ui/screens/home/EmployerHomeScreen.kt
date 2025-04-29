package com.example.gigs.ui.screens.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.gigs.data.model.Job
import com.example.gigs.navigation.AdminButton
import com.example.gigs.ui.components.GigWorkHeaderText
import com.example.gigs.ui.components.GigWorkSubtitleText
import com.example.gigs.ui.screens.dashboard.JobItem
import com.example.gigs.viewmodel.AuthViewModel
import com.example.gigs.viewmodel.JobViewModel
import com.example.gigs.viewmodel.ProfileViewModel
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmployerHomeScreen(
    authViewModel: AuthViewModel,
    onSignOut: () -> Unit,
    onNavigateToDashboard: () -> Unit,
    onNavigateToCreateJob: () -> Unit,
    onNavigateToAdminDashboard: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    val jobViewModel: JobViewModel = hiltViewModel()
    val profileViewModel: ProfileViewModel = hiltViewModel()

    // Load employer profile when the screen launches
    LaunchedEffect(Unit) {
        profileViewModel.getEmployerProfile()
    }

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
                onNavigateToDashboard = onNavigateToDashboard,
                onNavigateToAdminDashboard = onNavigateToAdminDashboard
            )
            1 -> EmployerJobsTab(
                modifier = Modifier.padding(paddingValues),
                jobViewModel = jobViewModel,
                onJobSelected = { /* Handle job selection */ }
            )
            2 -> EmployerProfileTab(
                modifier = Modifier.padding(paddingValues),
                profileViewModel = profileViewModel
            )
        }
    }
}

@Composable
fun EmployerHomeTab(
    modifier: Modifier = Modifier,
    authViewModel: AuthViewModel,
    onNavigateToDashboard: () -> Unit,
    onNavigateToAdminDashboard: () -> Unit
) {
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

        // Placeholder for dashboard
        Text(
            text = "Your hiring dashboard will appear here soon.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onNavigateToDashboard,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Icon(
                imageVector = Icons.Default.Dashboard,
                contentDescription = "Dashboard Icon",
                modifier = Modifier.padding(end = 8.dp)
            )
            Text("View Dashboard")
        }

        Spacer(modifier = Modifier.height(24.dp))

        AdminButton(
            authViewModel = authViewModel,
            onNavigateToAdminDashboard = onNavigateToAdminDashboard
        )
    }
}

@Composable
fun EmployerJobsTab(
    modifier: Modifier = Modifier,
    jobViewModel: JobViewModel,
    onJobSelected: (String) -> Unit
) {
    val jobs by jobViewModel.jobs.collectAsState()
    val isLoading by jobViewModel.isLoading.collectAsState()

    // Load employer's jobs when tab is selected
    LaunchedEffect(Unit) {
        jobViewModel.getMyJobs(20) // Load more jobs
    }

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
            CircularProgressIndicator()
        } else if (jobs.isEmpty()) {
            Text(
                text = "You haven't posted any jobs yet. Tap the + button to create a new job posting.",
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
fun EmployerProfileTab(
    modifier: Modifier = Modifier,
    profileViewModel: ProfileViewModel
) {
    val employerProfile by profileViewModel.employerProfile.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
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
                        text = employerProfile?.companyName ?: "Company Name",
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
                            text = employerProfile?.industry ?: "Industry",
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
                            text = "${employerProfile?.district ?: ""}, ${employerProfile?.state ?: ""}",
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
                            text = employerProfile?.companySize ?: "Company Size",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    if (employerProfile?.website != null) {
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
                                text = employerProfile?.website ?: "",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    if (!employerProfile?.description.isNullOrEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "About",
                            style = MaterialTheme.typography.titleMedium
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = employerProfile?.description ?: "",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { /* Navigate to edit profile */ },
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