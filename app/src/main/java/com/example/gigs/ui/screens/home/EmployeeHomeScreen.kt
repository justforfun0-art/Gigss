package com.example.gigs.ui.screens.home

// EmployeeHomeScreen.kt
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.gigs.ui.components.GigWorkHeaderText
import com.example.gigs.ui.components.GigWorkSubtitleText
import com.example.gigs.viewmodel.AuthViewModel
import com.example.gigs.viewmodel.JobViewModel
import com.example.gigs.viewmodel.ProfileViewModel
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.gigs.data.model.EmployeeProfile
import com.example.gigs.ui.screens.dashboard.JobItem
import com.example.gigs.data.model.Job
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmployeeHomeScreen(
    authViewModel: AuthViewModel,
    onSignOut: () -> Unit,
    onNavigateToDashboard: () -> Unit,
    onNavigateToJobListing: (String) -> Unit
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
            }
        }
    ) { paddingValues ->
        when (selectedTab) {
            0 -> EmployeeHomeTab(
                modifier = Modifier.padding(paddingValues),
                jobViewModel = jobViewModel
            )
            1 -> EmployeeJobsTab(
                modifier = Modifier.padding(paddingValues),
                district = employeeProfile?.district ?: "",
                onJobSelected = { jobId -> /* Navigate to job details */ }
            )
            2 -> EmployeeProfileTab(
                modifier = Modifier.padding(paddingValues),
                profile = employeeProfile
            )
        }
    }
}

@Composable
fun EmployeeHomeTab(
    modifier: Modifier = Modifier,
    jobViewModel: JobViewModel
) {
    val featuredJobs by jobViewModel.featuredJobs.collectAsState()
    val isLoading by jobViewModel.isLoading.collectAsState()

    // Load featured jobs
    LaunchedEffect(Unit) {
        jobViewModel.getFeaturedJobs(5) // Get 5 featured jobs
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
            text = "Find local jobs that match your skills and availability"
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Featured Jobs",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.align(Alignment.Start)
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (isLoading) {
            CircularProgressIndicator()
        } else if (featuredJobs.isEmpty()) {
            Text(
                text = "No featured jobs available at the moment.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
        } else {
            LazyColumn {
                items(featuredJobs) { job ->
                    JobItem(
                        job = job,
                        onClick = { /* Navigate to job details */ }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
fun EmployeeJobsTab(
    modifier: Modifier = Modifier,
    district: String,
    onJobSelected: (String) -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        GigWorkHeaderText(text = "Find Jobs")

        Spacer(modifier = Modifier.height(32.dp))

        // Placeholder for job listings
        Text(
            text = "Job listings will appear here.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
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