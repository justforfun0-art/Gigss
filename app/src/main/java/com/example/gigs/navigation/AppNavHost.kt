package com.example.gigs.navigation

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.gigs.data.model.AuthState
import com.example.gigs.data.model.UserType
import com.example.gigs.data.repository.AuthRepository
import com.example.gigs.ui.screens.admin.AdminJobApprovalScreen
import com.example.gigs.ui.screens.auth.OtpVerificationScreen
import com.example.gigs.ui.screens.auth.PhoneAuthScreen
import com.example.gigs.ui.screens.auth.UserTypeSelectionScreen
import com.example.gigs.ui.screens.dashboard.EmployeeDashboardScreen
import com.example.gigs.ui.screens.dashboard.EmployerDashboardScreen
import com.example.gigs.ui.screens.home.EmployeeHomeScreen
import com.example.gigs.ui.screens.home.EmployerHomeScreen
import com.example.gigs.ui.screens.jobs.JobDetailsScreen
import com.example.gigs.ui.screens.jobs.JobListingScreen
import com.example.gigs.ui.screens.jobs.JobPostingScreen
import com.example.gigs.ui.screens.messages.ChatScreen
import com.example.gigs.ui.screens.messages.ConversationsScreen
import com.example.gigs.ui.screens.notifications.NotificationsScreen
import com.example.gigs.ui.screens.profile.BasicProfileSetupScreen
import com.example.gigs.ui.screens.profile.EmployeeProfileSetupScreen
import com.example.gigs.ui.screens.profile.EmployerProfileSetupScreen
import com.example.gigs.ui.screens.reviews.CreateReviewScreen
import com.example.gigs.ui.screens.reviews.ReviewsScreen
import com.example.gigs.ui.screens.welcome.WelcomeScreen
import com.example.gigs.viewmodel.AuthViewModel
import com.example.gigs.viewmodel.NotificationViewModel
import com.example.gigs.viewmodel.ProfileViewModel

@Composable
fun AppNavHost(
    navController: NavHostController,
    authViewModel: AuthViewModel = hiltViewModel(),
    profileViewModel: ProfileViewModel = hiltViewModel(),
    startDestination: String = Screen.Welcome.route
) {
    val authState by authViewModel.authState.collectAsState()
    val userType by profileViewModel.userType.collectAsState()
    val profileState by profileViewModel.profileState.collectAsState()

    // Get notification count for badge
    val notificationViewModel: NotificationViewModel = hiltViewModel()
    val unreadCount by notificationViewModel.unreadCount.collectAsState()

    // Load unread notification count
    LaunchedEffect(Unit) {
        notificationViewModel.loadUnreadCount()
    }

    // Determine start destination based on auth state
    val effectiveStartDestination = when (authState) {
        is AuthState.Initial -> Screen.Welcome.route
        is AuthState.Authenticated -> {
            when (userType) {
                UserType.EMPLOYEE -> Screen.EmployeeHome.route
                UserType.EMPLOYER -> Screen.EmployerHome.route
                else -> startDestination
            }
        }
        is AuthState.ProfileIncomplete -> {
            when (userType) {
                UserType.EMPLOYEE -> Screen.EmployeeProfileDetails.route
                UserType.EMPLOYER -> Screen.EmployerProfileDetails.route
                else -> startDestination
            }
        }
        else -> startDestination
    }

    // Effect to handle authentication state changes
    LaunchedEffect(authState) {
        when (authState) {
            is AuthState.Unauthenticated -> {
                navController.navigate(Screen.Welcome.route) {
                    popUpTo(0) { inclusive = true }
                }
            }
            is AuthState.ProfileIncomplete -> {
                // Navigate directly to the detailed profile screens
                when (userType) {
                    UserType.EMPLOYEE -> {
                        navController.navigate(Screen.EmployeeProfileDetails.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                    UserType.EMPLOYER -> {
                        navController.navigate(Screen.EmployerProfileDetails.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                    else -> {
                        // If user type is somehow undefined, go to user type selection
                        navController.navigate(Screen.SelectUserType.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                }
            }
            is AuthState.Authenticated -> {
                // Navigate based on user type
                when (userType) {
                    UserType.EMPLOYEE -> {
                        navController.navigate(Screen.EmployeeHome.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                    UserType.EMPLOYER -> {
                        navController.navigate(Screen.EmployerHome.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                    else -> {
                        // If user type is undefined, go to user type selection
                        navController.navigate(Screen.SelectUserType.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                }
            }
            else -> {
                // Initial or Error states - stay on current screen
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = effectiveStartDestination
    ) {
        // Authentication flow
        composable(Screen.Welcome.route) {
            WelcomeScreen(
                onFindJobsClick = {
                    // Set user type to EMPLOYEE
                    profileViewModel.setUserType(UserType.EMPLOYEE)
                    navController.navigate(Screen.PhoneAuth.route)
                },
                onPostJobsClick = {
                    // Set user type to EMPLOYER
                    profileViewModel.setUserType(UserType.EMPLOYER)
                    navController.navigate(Screen.PhoneAuth.route)
                }
            )
        }

        composable(Screen.PhoneAuth.route) {
            PhoneAuthScreen(
                authViewModel = authViewModel,
                profileViewModel = profileViewModel,
                onNavigateToOtp = {
                    navController.navigate(Screen.OtpVerification.route)
                }
            )
        }

        composable(Screen.OtpVerification.route) {
            OtpVerificationScreen(
                authViewModel = authViewModel,
                onVerificationSuccess = {
                    // Important: After verification, check state and navigate accordingly
                    when (authState) {
                        is AuthState.Authenticated -> {
                            when (userType) {
                                UserType.EMPLOYEE -> navController.navigate(Screen.EmployeeHome.route) {
                                    popUpTo(0) { inclusive = true }
                                }
                                UserType.EMPLOYER -> navController.navigate(Screen.EmployerHome.route) {
                                    popUpTo(0) { inclusive = true }
                                }
                                else -> navController.navigate(Screen.Welcome.route) {
                                    popUpTo(0) { inclusive = true }
                                }
                            }
                        }
                        is AuthState.ProfileIncomplete -> {
                            // Go directly to detailed profile setup
                            when (userType) {
                                UserType.EMPLOYEE -> navController.navigate(Screen.EmployeeProfileDetails.route) {
                                    popUpTo(0) { inclusive = true }
                                }
                                UserType.EMPLOYER -> navController.navigate(Screen.EmployerProfileDetails.route) {
                                    popUpTo(0) { inclusive = true }
                                }
                                else -> navController.navigate(Screen.Welcome.route) {
                                    popUpTo(0) { inclusive = true }
                                }
                            }
                        }
                        else -> {
                            // Let auth state handle other cases
                            authViewModel.checkAuthState()
                        }
                    }
                }
            )
        }

        // User type selection (kept for fallback)
        composable(Screen.SelectUserType.route) {
            UserTypeSelectionScreen(
                profileViewModel = profileViewModel,
                onUserTypeSelected = { userType ->
                    when (userType) {
                        UserType.EMPLOYEE -> navController.navigate(Screen.EmployeeProfileDetails.route)
                        UserType.EMPLOYER -> navController.navigate(Screen.EmployerProfileDetails.route)
                        else -> { /* do nothing */ }
                    }
                }
            )
        }

        // Basic profile screens (kept for backward compatibility but not in main flow)
        composable(Screen.CreateEmployeeProfile.route) {
            BasicProfileSetupScreen(
                profileViewModel = profileViewModel,
                onProfileCreated = {
                    navController.navigate(Screen.EmployeeProfileDetails.route)
                }
            )
        }

        composable(Screen.CreateEmployerProfile.route) {
            BasicProfileSetupScreen(
                profileViewModel = profileViewModel,
                onProfileCreated = {
                    navController.navigate(Screen.EmployerProfileDetails.route)
                }
            )
        }

        // Detailed profile screens (main profile flow)
        composable(Screen.EmployeeProfileDetails.route) {
            EmployeeProfileSetupScreen(
                profileViewModel = profileViewModel,
                onProfileCreated = { district ->
                    // Navigate to job listing with the selected district
                    navController.navigate(Screen.JobListing.createRoute(district)) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.EmployerProfileDetails.route) {
            EmployerProfileSetupScreen(
                profileViewModel = profileViewModel,
                onProfileCreated = {
                    // Navigate to employer dashboard after profile is created
                    navController.navigate(Screen.EmployerDashboard.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        // Main app screens
        composable(Screen.EmployeeHome.route) {
            EmployeeHomeScreen(
                authViewModel = authViewModel,
                onSignOut = {
                    navController.navigate(Screen.Welcome.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onNavigateToDashboard = {
                    navController.navigate(Screen.EmployeeDashboard.route)
                },
                onNavigateToJobListing = { district ->
                    navController.navigate(Screen.JobListing.createRoute(district))
                }
            )
        }

        composable(Screen.EmployerHome.route) {
            EmployerHomeScreen(
                authViewModel = authViewModel,
                onSignOut = {
                    // Navigate to welcome screen
                    navController.navigate(Screen.Welcome.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onNavigateToDashboard = {
                    navController.navigate(Screen.EmployerDashboard.route)
                },
                onNavigateToCreateJob = {
                    navController.navigate(Screen.CreateJob.route)
                },
                onNavigateToAdminDashboard = {
                    navController.navigate(Screen.AdminDashboard.route)
                }
            )
        }

        // Jobs feature
        composable(
            route = Screen.JobListing.route,
            arguments = listOf(navArgument("district") { type = NavType.StringType })
        ) { backStackEntry ->
            val district = backStackEntry.arguments?.getString("district") ?: ""
            JobListingScreen(
                jobViewModel = hiltViewModel(),
                district = district,
                onNavigateToHome = {
                    navController.navigate(Screen.EmployeeHome.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onJobSelected = { jobId ->
                    navController.navigate(Screen.JobDetails.createRoute(jobId))
                }
            )
        }

        composable(
            route = Screen.JobDetails.route,
            arguments = listOf(navArgument("jobId") { type = NavType.StringType })
        ) { backStackEntry ->
            val jobId = backStackEntry.arguments?.getString("jobId") ?: ""
            JobDetailsScreen(
                jobViewModel = hiltViewModel(),
                jobId = jobId,
                onBackPressed = { navController.popBackStack() },
                onApply = {
                    // Apply for job and show success message, then return to job details
                    navController.popBackStack()
                    navController.navigate(Screen.JobDetails.createRoute(jobId))
                },
                onMessageEmployer = { employerId, employerName ->
                    // Create a new conversation if none exists
                    navController.navigate(Screen.Conversations.route) {
                        // Add popup behavior if needed
                    }
                }
            )
        }

        composable(Screen.CreateJob.route) {
            JobPostingScreen(
                viewModel = hiltViewModel(),
                onJobCreated = {
                    navController.navigate(Screen.EmployerDashboard.route) {
                        popUpTo(Screen.EmployerHome.route) { inclusive = true }
                    }
                },
                onBackPressed = { navController.popBackStack() } // Adding back navigation
            )
        }

        // Messaging feature
        composable(Screen.Conversations.route) {
            ConversationsScreen(
                viewModel = hiltViewModel(),
                onConversationSelected = { conversation ->
                    navController.navigate(
                        Screen.Chat.createRoute(
                            conversation.id,
                            conversation.otherUserName,
                            if (conversation.employerId == authViewModel.getCurrentUserId())
                                conversation.employeeId
                            else
                                conversation.employerId
                        )
                    )
                },
                onBackPressed = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.Chat.route,
            arguments = listOf(
                navArgument("conversationId") { type = NavType.StringType },
                navArgument("otherUserName") { type = NavType.StringType },
                navArgument("receiverId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val conversationId = backStackEntry.arguments?.getString("conversationId") ?: ""
            val otherUserName = backStackEntry.arguments?.getString("otherUserName") ?: ""
            val receiverId = backStackEntry.arguments?.getString("receiverId") ?: ""

            ChatScreen(
                viewModel = hiltViewModel(),
                conversationId = conversationId,
                otherUserName = otherUserName,
                receiverId = receiverId,
                onBackPressed = { navController.popBackStack() }
            )
        }

        // Notifications feature
        composable(Screen.Notifications.route) {
            NotificationsScreen(
                viewModel = hiltViewModel(),
                onNotificationClick = { notification ->
                    // Navigate based on notification type
                    when (notification.type) {
                        "new_application" -> {
                            notification.relatedId?.let { applicationId ->
                                // Navigate to application details when implemented
                            }
                        }
                        "new_message" -> {
                            notification.relatedId?.let { conversationId ->
                                // Navigate to conversation when we have receiver info
                                navController.navigate(Screen.Conversations.route)
                            }
                        }
                        "job_match" -> {
                            notification.relatedId?.let { jobId ->
                                navController.navigate(Screen.JobDetails.createRoute(jobId))
                            }
                        }
                        "job_approval", "job_rejection" -> {
                            notification.relatedId?.let { jobId ->
                                navController.navigate(Screen.JobDetails.createRoute(jobId))
                            }
                        }
                        "application_update" -> {
                            // Navigate to application status screen when implemented
                        }
                    }
                },
                onBackPressed = { navController.popBackStack() }
            )
        }

        // Reviews feature
        composable(
            route = Screen.Reviews.route,
            arguments = listOf(navArgument("jobId") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            })
        ) { backStackEntry ->
            val jobId = backStackEntry.arguments?.getString("jobId")

            ReviewsScreen(
                viewModel = hiltViewModel(),
                isMyReviews = jobId == null,
                jobId = jobId,
                onBackPressed = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.CreateReview.route,
            arguments = listOf(
                navArgument("jobId") { type = NavType.StringType },
                navArgument("revieweeId") { type = NavType.StringType },
                navArgument("revieweeName") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val jobId = backStackEntry.arguments?.getString("jobId") ?: ""
            val revieweeId = backStackEntry.arguments?.getString("revieweeId") ?: ""
            val revieweeName = backStackEntry.arguments?.getString("revieweeName") ?: ""

            CreateReviewScreen(
                viewModel = hiltViewModel(),
                jobId = jobId,
                revieweeId = revieweeId,
                revieweeName = revieweeName,
                onReviewSubmitted = { navController.popBackStack() },
                onBackPressed = { navController.popBackStack() }
            )
        }

        // Dashboard feature
        composable(Screen.EmployeeDashboard.route) {
            EmployeeDashboardScreen(
                viewModel = hiltViewModel(),
                onViewAllApplications = {
                    // Navigate to applications list when implemented
                },
                onViewAllActivities = {
                    // Navigate to activities list when implemented
                },
                onNavigateToNotifications = { navController.navigate(Screen.Notifications.route) },
                onNavigateToMessages = { navController.navigate(Screen.Conversations.route) },
                onBackPressed = { navController.popBackStack() }
            )
        }

        composable(Screen.EmployerDashboard.route) {
            EmployerDashboardScreen(
                viewModel = hiltViewModel(),
                onViewAllJobs = {
                    // Navigate to jobs list when implemented
                },
                onViewAllActivities = {
                    // Navigate to activities list when implemented
                },
                onCreateJob = { navController.navigate(Screen.CreateJob.route) },
                onNavigateToNotifications = { navController.navigate(Screen.Notifications.route) },
                onNavigateToMessages = { navController.navigate(Screen.Conversations.route) },
                onBackPressed = { navController.popBackStack() }
            )
        }

        // Admin screens
        composable(Screen.AdminJobApproval.route) {
            AdminJobApprovalScreen(
                onBackPressed = { navController.popBackStack() },
                onNavigateToJobDetails = { jobId ->
                    navController.navigate(Screen.JobDetails.createRoute(jobId))
                }
            )
        }

        composable(Screen.AdminDashboard.route) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Admin Dashboard",
                    style = MaterialTheme.typography.headlineMedium
                )

                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = { navController.navigate(Screen.AdminJobApproval.route) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Job Approval Management")
                }

                Spacer(modifier = Modifier.height(16.dp))

                // You can add more admin features here

                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = { navController.popBackStack() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Back")
                }
            }
        }
    }
}

@Composable
fun NotificationIcon(unreadCount: Int, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Box {
            Icon(
                imageVector = Icons.Default.Notifications,
                contentDescription = "Notifications"
            )

            if (unreadCount > 0) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .background(MaterialTheme.colorScheme.error, CircleShape)
                        .align(Alignment.TopEnd),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (unreadCount > 9) "9+" else unreadCount.toString(),
                        color = MaterialTheme.colorScheme.onError,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 8.sp
                    )
                }
            }
        }
    }
}

@Composable
fun AdminButton(
    authViewModel: AuthViewModel,
    onNavigateToAdminDashboard: () -> Unit
) {
    var isAdmin by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        // Call the parameterless isUserAdmin() method
        isAdmin = authViewModel.authRepository.isUserAdmin()
    }

    if (isAdmin) {
        Button(
            onClick = onNavigateToAdminDashboard,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Admin Dashboard")
        }
    }
}