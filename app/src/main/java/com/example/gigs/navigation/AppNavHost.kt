package com.example.gigs.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.gigs.data.model.*
import com.example.gigs.data.repository.ApplicationRepository
import com.example.gigs.data.repository.AuthRepository
import com.example.gigs.ui.screens.admin.AdminJobApprovalScreen
import com.example.gigs.ui.screens.auth.OtpVerificationScreen
import com.example.gigs.ui.screens.auth.PhoneAuthScreen
import com.example.gigs.ui.screens.auth.UserTypeSelectionScreen
import com.example.gigs.ui.screens.dashboard.ApplicationItem
import com.example.gigs.ui.screens.dashboard.EmployerDashboardScreen
import com.example.gigs.ui.screens.home.EmployeeHomeScreen
import com.example.gigs.ui.screens.home.EmployerHomeScreen
import com.example.gigs.ui.screens.jobs.JobDetailsScreen
import com.example.gigs.ui.screens.jobs.JobListingScreen
import com.example.gigs.ui.screens.jobs.JobPostingScreen
import com.example.gigs.ui.screens.messages.ChatScreen
import com.example.gigs.ui.screens.messages.ConversationsScreen
import com.example.gigs.ui.screens.profile.BasicProfileSetupScreen
import com.example.gigs.ui.screens.profile.EmployeeProfileSetupScreen
import com.example.gigs.ui.screens.profile.EmployerProfileSetupScreen
import com.example.gigs.ui.screens.reviews.CreateReviewScreen
import com.example.gigs.ui.screens.reviews.ReviewsScreen
import com.example.gigs.ui.screens.welcome.WelcomeScreen
import com.example.gigs.viewmodel.AuthViewModel
import com.example.gigs.viewmodel.NotificationViewModel
import com.example.gigs.viewmodel.ProfileViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import android.widget.Toast
import com.example.gigs.ui.screens.dashboard.EmployeeDashboardScreen
import com.example.gigs.ui.screens.jobs.JobHistoryScreen
import com.example.gigs.ui.screens.notifications.NotificationsScreen
import androidx.navigation.navArgument
import com.example.gigs.ui.screens.jobs.AllApplicationsScreen
import com.example.gigs.ui.screens.jobs.EmployerJobDetailsScreen
import com.example.gigs.ui.screens.jobs.JobApplicationDetailsScreen
import com.example.gigs.ui.screens.jobs.JobApplicationsScreen
import com.example.gigs.ui.screens.jobs.JobHistoryScreen
import com.example.gigs.ui.screens.profile.ApplicantProfileScreen
import com.example.gigs.ui.screens.profile.EditEmployerProfileScreen

// Add applications view screen route
object ApplicationsView : Screen("applications_view")

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

    // In AppNavHost.kt - add a key to ensure recomposition
    val authStateKey = authState.toString() + System.currentTimeMillis().toString()

    LaunchedEffect(authStateKey) {
        if (authState is AuthState.Unauthenticated) {
            navController.navigate(Screen.Welcome.route) {
                // Clear the entire back stack
                popUpTo(0) { inclusive = true }
            }
        }
    }

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
                profileViewModel = profileViewModel,
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
                },
                onNavigateToWelcome = {
                    // Add this new parameter
                    navController.navigate(Screen.Welcome.route) {
                        popUpTo(0) { inclusive = true }
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

        // Job History Screen Route
        composable(Screen.JobHistory.route) {
            JobHistoryScreen(
                onJobSelected = { jobId -> navController.navigate(Screen.JobDetails.createRoute(jobId)) },
                onApplicationSelected = { applicationId ->
                    navController.navigate(Screen.JobApplicationDetails.createRoute(applicationId))
                },
                onBackPressed = { navController.popBackStack() }
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
                },
                onNavigateToMessages = {
                    navController.navigate(Screen.Conversations.route)
                },
                onNavigateToNotifications = {
                    navController.navigate(Screen.Notifications.route)
                },
                onNavigateToJobHistory = {
                    navController.navigate(Screen.JobHistory.route)
                }
            )
        }

        composable(
            route = Screen.EmployerJobDetails.route,
            arguments = listOf(navArgument("jobId") { type = NavType.StringType })
        ) { backStackEntry ->
            val jobId = backStackEntry.arguments?.getString("jobId") ?: ""
            EmployerJobDetailsScreen(
                jobViewModel = hiltViewModel(),
                jobId = jobId,
                onBackPressed = { navController.popBackStack() },
                onEditJob = { /* Navigate to edit job screen when implemented */ },
                onViewApplications = { id, title ->
                    navController.navigate(Screen.JobApplications.createRoute(id, title))
                }
            )
        }

        composable(Screen.EditEmployerProfile.route) {
            EditEmployerProfileScreen(
                profileViewModel = hiltViewModel(),
                onProfileUpdated = {
                    navController.navigate(Screen.EmployerHome.route) {
                        popUpTo(Screen.EmployerHome.route) { inclusive = true }
                    }
                },
                onBackPressed = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.ApplicantProfile.route,
            arguments = listOf(navArgument("applicantId") { type = NavType.StringType })
        ) { backStackEntry ->
            val applicantId = backStackEntry.arguments?.getString("applicantId") ?: ""

            ApplicantProfileScreen(
                viewModel = hiltViewModel(),
                applicantId = applicantId,
                onBackPressed = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.JobApplications.route,
            arguments = listOf(
                navArgument("jobId") { type = NavType.StringType },
                navArgument("jobTitle") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val jobId = backStackEntry.arguments?.getString("jobId") ?: ""
            val jobTitle = backStackEntry.arguments?.getString("jobTitle")?.replace("-", "/") ?: ""

            JobApplicationsScreen(
                viewModel = hiltViewModel(),
                jobId = jobId,
                jobTitle = jobTitle,
                onBackPressed = { navController.popBackStack() },
                onViewApplicantProfile = { applicantId ->
                    navController.navigate(Screen.ApplicantProfile.createRoute(applicantId))
                }
            )
        }

        composable(Screen.AllApplications.route) {
            AllApplicationsScreen(
                viewModel = hiltViewModel(),
                onBackPressed = { navController.popBackStack() },
                onViewApplicantProfile = { applicantId ->
                    navController.navigate(Screen.ApplicantProfile.createRoute(applicantId))
                },
                onViewJob = { jobId ->
                    navController.navigate(Screen.EmployerJobDetails.createRoute(jobId))
                }
            )
        }


        // Update the EmployerHomeScreen call in the existing route
        composable(Screen.EmployerHome.route) {
            EmployerHomeScreen(
                authViewModel = authViewModel,
                dashboardViewModel = hiltViewModel(),
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
                },
                onNavigateToJobDetails = { jobId ->
                    navController.navigate(Screen.EmployerJobDetails.createRoute(jobId))
                },
                onNavigateToEditProfile = {
                    navController.navigate(Screen.EditEmployerProfile.route)
                },
                onViewAllApplications = {
                    navController.navigate(Screen.AllApplications.route)
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
                },
                onBackPressed = {
                    navController.popBackStack()
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
                navController = navController, // Added this parameter
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
                navController = navController,
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
            val context = LocalContext.current
            EmployeeDashboardScreen(
                dashboardViewModel = hiltViewModel(),
                profileViewModel = hiltViewModel(),
                onViewAllApplications = {
                    navController.navigate(ApplicationsView.route)
                },
                onViewApplication = { applicationId ->
                    navController.navigate(Screen.JobApplicationDetails.createRoute(applicationId))
                },
                onViewAllActivities = {
                    // Navigate to activities list when implemented
                    Toast.makeText(context, "View all activities", Toast.LENGTH_SHORT).show()
                },
                onNavigateToNotifications = {
                    navController.navigate(Screen.Notifications.route)
                },
                onNavigateToMessages = {
                    navController.navigate(Screen.Conversations.route)
                },
                onNavigateToJobHistory = {
                    navController.navigate(Screen.JobHistory.route)
                },
                onEditProfile = {
                    // Navigate to edit profile screen when implemented
                    Toast.makeText(context, "Edit profile", Toast.LENGTH_SHORT).show()
                },
                onBackPressed = {
                    navController.popBackStack()
                }
            )
        }


        composable(Screen.EmployerDashboard.route) {
            EmployerDashboardScreen(
                dashboardViewModel = hiltViewModel(),
                applicationsViewModel = hiltViewModel(),
                onViewAllJobs = {
                    // Navigate to jobs list when implemented
                },
                onViewAllActivities = {
                    // Navigate to activities list when implemented
                },
                onCreateJob = { navController.navigate(Screen.CreateJob.route) },
                onNavigateToNotifications = { navController.navigate(Screen.Notifications.route) },
                onNavigateToMessages = { navController.navigate(Screen.Conversations.route) },
                onViewApplication = { applicationId ->
                    // Navigate to application details when implemented
                },
                onBackPressed = { navController.popBackStack() }
            )
        }

        // Applications View Screen
        composable(ApplicationsView.route) {
            ApplicationsViewScreen(
                viewModel = hiltViewModel(),
                onApplicationSelected = { applicationId ->
                    navController.navigate(Screen.JobApplicationDetails.createRoute(applicationId))
                },
                onBackPressed = { navController.popBackStack() }
            )
        }

        // Job Application Details Screen Route
        // Job Application Details Screen Route
        composable(
            route = Screen.JobApplicationDetails.route,
            arguments = listOf(navArgument("applicationId") { type = NavType.StringType })
        ) { backStackEntry ->
            val applicationId = backStackEntry.arguments?.getString("applicationId") ?: ""
            JobApplicationDetailsScreen(
                viewModel = hiltViewModel(),
                applicationId = applicationId,
                onBackPressed = { navController.popBackStack() },
                onMessageEmployer = { employerId ->
                    // Create a conversation with the employer
                    navController.navigate(Screen.Conversations.route)
                },
                onWriteReview = { jobId, revieweeId, revieweeName ->
                    navController.navigate(
                        Screen.CreateReview.createRoute(jobId, revieweeId, revieweeName)
                    )
                }
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

// Define ApplicationsViewScreen
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApplicationsViewScreen(
    viewModel: ApplicationsViewModel = hiltViewModel(),
    onApplicationSelected: (String) -> Unit,
    onBackPressed: () -> Unit
) {
    val applications by viewModel.applications.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadAllApplications()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Applications") },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (applications.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text("No applications found")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(applications) { application ->
                    ApplicationItem(
                        application = application,
                        onClick = { onApplicationSelected(application.id) }
                    )
                }
            }
        }
    }
}

// Define ApplicationsViewModel
@HiltViewModel
class ApplicationsViewModel @Inject constructor(
    private val applicationRepository: ApplicationRepository
) : ViewModel() {
    private val _applications = MutableStateFlow<List<ApplicationWithJob>>(emptyList())
    val applications: StateFlow<List<ApplicationWithJob>> = _applications

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    fun loadAllApplications() {
        viewModelScope.launch {
            _isLoading.value = true
            applicationRepository.getMyApplications(0).collect { result ->
                _isLoading.value = false
                if (result.isSuccess) {
                    _applications.value = result.getOrNull() ?: emptyList()
                }
            }
        }
    }
}