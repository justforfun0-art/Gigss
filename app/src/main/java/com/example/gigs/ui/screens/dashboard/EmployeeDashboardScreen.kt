package com.example.gigs.ui.screens.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WorkOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
//import androidx.glance.layout.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.glance.layout.Box
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.gigs.viewmodel.EmployeeDashboardViewModel
import io.ktor.websocket.Frame
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.ui.Alignment

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmployeeDashboardScreen(
    viewModel: EmployeeDashboardViewModel = hiltViewModel(),
    onViewAllApplications: () -> Unit,
    onViewAllActivities: () -> Unit,
    onNavigateToNotifications: () -> Unit,
    onNavigateToMessages: () -> Unit,
    onBackPressed: () -> Unit
) {
    val dashboardData by viewModel.dashboardData.collectAsState()
    val recentActivities by viewModel.recentActivities.collectAsState()
    val recentApplications by viewModel.recentApplications.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadDashboardData()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Frame.Text("Dashboard") },
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
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(16.dp))

                    // Stats Cards
                    Row(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        StatCard(
                            title = "Applications",
                            value = dashboardData?.totalApplications ?: 0,
                            icon = Icons.Default.Description,
                            modifier = Modifier.weight(1f)
                        )

                        Spacer(modifier = Modifier.width(16.dp))

                        StatCard(
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
                        StatCard(
                            title = "Rating",
                            valueText = String.format("%.1f", dashboardData?.averageRating ?: 0f),
                            icon = Icons.Default.Star,
                            modifier = Modifier.weight(1f)
                        )

                        Spacer(modifier = Modifier.width(16.dp))

                        StatCard(
                            title = "Reviews",
                            value = dashboardData?.reviewCount ?: 0,
                            icon = Icons.Default.RateReview,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }

                // Recent Applications Section
                item {
                    SectionHeader(
                        title = "Recent Applications",
                        onViewAll = onViewAllApplications
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (recentApplications.isEmpty()) {
                    item {
                        EmptyStateMessage(
                            message = "You haven't applied to any jobs yet",
                            actionText = "Browse Jobs"
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                    }
                } else {
                    items(recentApplications) { application ->
                        ApplicationItem(application = application)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                // Recent Activities Section
                item {
                    Spacer(modifier = Modifier.height(16.dp))

                    SectionHeader(
                        title = "Recent Activities",
                        onViewAll = onViewAllActivities
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (recentActivities.isEmpty()) {
                    item {
                        EmptyStateMessage(
                            message = "No recent activities to show"
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                    }
                } else {
                    items(recentActivities) { activity ->
                        ActivityItem(activity = activity)

                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}
