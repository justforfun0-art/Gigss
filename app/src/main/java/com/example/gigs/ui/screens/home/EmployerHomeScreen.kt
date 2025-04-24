package com.example.gigs.ui.screens.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.gigs.navigation.AdminButton
import com.example.gigs.ui.components.GigWorkHeaderText
import com.example.gigs.ui.components.GigWorkSubtitleText
import com.example.gigs.viewmodel.AuthViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmployerHomeScreen(
    authViewModel: AuthViewModel,
    onSignOut: () -> Unit,
    onNavigateToDashboard: () -> Unit,
    onNavigateToCreateJob: () -> Unit,
    onNavigateToAdminDashboard: () -> Unit // Add this
) {
    var selectedTab by remember { mutableStateOf(0) }

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
                authViewModel = authViewModel,  // Pass authViewModel here
                onNavigateToDashboard = onNavigateToDashboard,
                onNavigateToAdminDashboard = onNavigateToAdminDashboard  // Pass onNavigateToAdminDashboard here
            )
            1 -> EmployerJobsTab(modifier = Modifier.padding(paddingValues))
            2 -> EmployerProfileTab(modifier = Modifier.padding(paddingValues))
        }
    }
}

@Composable
fun EmployerHomeTab(
    modifier: Modifier = Modifier,
    authViewModel: AuthViewModel,  // Add this parameter
    onNavigateToDashboard: () -> Unit,
    onNavigateToAdminDashboard: () -> Unit  // Add this parameter
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
fun EmployerJobsTab(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        GigWorkHeaderText(text = "My Job Postings")

        Spacer(modifier = Modifier.height(32.dp))

        // Placeholder for job postings
        Text(
            text = "Your job postings will appear here. Tap the + button to create a new job posting.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun EmployerProfileTab(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        GigWorkHeaderText(text = "Company Profile")

        Spacer(modifier = Modifier.height(32.dp))

        // Placeholder for profile info
        Text(
            text = "Your company profile information will appear here.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
    }
}