/*package com.example.gigs.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.gigs.data.model.AuthState
import com.example.gigs.data.model.UserType
import com.example.gigs.ui.screens.auth.OtpVerificationScreen
import com.example.gigs.ui.screens.auth.PhoneAuthScreen
import com.example.gigs.ui.screens.auth.UserTypeSelectionScreen
import com.example.gigs.ui.screens.home.EmployeeHomeScreen
import com.example.gigs.ui.screens.home.EmployerHomeScreen
import com.example.gigs.ui.screens.profile.BasicProfileSetupScreen
import com.example.gigs.ui.screens.profile.EmployeeProfileSetupScreen
import com.example.gigs.ui.screens.profile.EmployerProfileSetupScreen
import com.example.gigs.ui.screens.welcome.WelcomeScreen
import com.example.gigs.viewmodel.AuthViewModel
import com.example.gigs.viewmodel.ProfileViewModel
import com.example.gigs.ui.navigation.Screen.EmployeeProfileDetails
import com.example.gigs.ui.navigation.Screen.EmployerProfileDetails

@Composable
fun NavGraph(navController: NavHostController) {
    val authViewModel: AuthViewModel = hiltViewModel()
    val profileViewModel: ProfileViewModel = hiltViewModel()

    val authState by authViewModel.authState.collectAsState()
    val userType by profileViewModel.userType.collectAsState()

    // Determine start destination based on auth state
    val startDestination = when (authState) {
        is AuthState.Authenticated -> {
            when (userType) {
                UserType.EMPLOYEE -> Screen.EmployeeHome.route
                UserType.EMPLOYER -> Screen.EmployerHome.route
                else -> Screen.Welcome.route
            }
        }
        is AuthState.ProfileIncomplete -> {
            // Go directly to the appropriate profile setup screen based on userType
            when (userType) {
                UserType.EMPLOYEE -> Screen.EmployeeProfileSetup.route
                UserType.EMPLOYER -> Screen.EmployerProfileSetup.route
                else -> Screen.Welcome.route // Fallback to welcome if userType not set
            }
        }
        else -> Screen.Welcome.route
    }

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        // Welcome screen
        composable(Screen.Welcome.route) {
            WelcomeScreen(
                onFindJobsClick = {
                    profileViewModel.setUserType(UserType.EMPLOYEE)
                    navController.navigate(Screen.PhoneAuth.route)
                },
                onPostJobsClick = {
                    profileViewModel.setUserType(UserType.EMPLOYER)
                    navController.navigate(Screen.PhoneAuth.route)
                }
            )
        }

        // Authentication flow
        composable(Screen.PhoneAuth.route) {
            PhoneAuthScreen(
                authViewModel = authViewModel,
                profileViewModel = profileViewModel, // Pass profileViewModel
                onNavigateToOtp = { navController.navigate(Screen.OtpVerification.route) }
            )
        }

        composable(Screen.OtpVerification.route) {
            OtpVerificationScreen(
                authViewModel = authViewModel,
                onVerificationSuccess = {
                    when (authState) {
                        is AuthState.Authenticated -> {
                            when (userType) {
                                UserType.EMPLOYEE -> navController.navigate(Screen.EmployeeHome.route) {
                                    popUpTo(Screen.Welcome.route) { inclusive = true }
                                }
                                UserType.EMPLOYER -> navController.navigate(Screen.EmployerHome.route) {
                                    popUpTo(Screen.Welcome.route) { inclusive = true }
                                }
                                else -> navController.navigate(Screen.Welcome.route) {
                                    popUpTo(Screen.Welcome.route) { inclusive = true }
                                }
                            }
                        }
                        is AuthState.ProfileIncomplete -> {
                            // Go directly to profile setup based on user type
                            when (userType) {
                                UserType.EMPLOYEE -> navController.navigate(Screen.EmployeeProfileSetup.route) {
                                    popUpTo(Screen.Welcome.route) { inclusive = true }
                                }
                                UserType.EMPLOYER -> navController.navigate(Screen.EmployerProfileSetup.route) {
                                    popUpTo(Screen.Welcome.route) { inclusive = true }
                                }
                                else -> navController.navigate(Screen.Welcome.route) {
                                    popUpTo(Screen.Welcome.route) { inclusive = true }
                                }
                            }
                        }
                        else -> {}
                    }
                }
            )
        }

        // Profile setup flow
        composable(Screen.UserTypeSelection.route) {
            UserTypeSelectionScreen(
                profileViewModel = profileViewModel,
                onUserTypeSelected = { type ->
                    profileViewModel.setUserType(type)
                    navController.navigate(Screen.BasicProfileSetup.route)
                }
            )
        }

        composable(Screen.BasicProfileSetup.route) {
            BasicProfileSetupScreen(
                profileViewModel = profileViewModel,
                onProfileCreated = {
                    when (userType) {
                        UserType.EMPLOYEE -> navController.navigate(Screen.EmployeeProfileSetup.route)
                        UserType.EMPLOYER -> navController.navigate(Screen.EmployerProfileSetup.route)
                        else -> {}
                    }
                }
            )
        }

        composable(Screen.EmployeeProfileSetup.route) {
            EmployeeProfileSetupScreen(
                profileViewModel = profileViewModel,
                onProfileCreated = {
                    navController.navigate(Screen.EmployeeHome.route) {
                        popUpTo(Screen.Welcome.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.EmployerProfileSetup.route) {
            EmployerProfileSetupScreen(
                profileViewModel = profileViewModel,
                onProfileCreated = {
                    navController.navigate(Screen.EmployerHome.route) {
                        popUpTo(Screen.Welcome.route) { inclusive = true }
                    }
                }
            )
        }

        // Home screens
        composable(Screen.EmployeeHome.route) {
            EmployeeHomeScreen(
                authViewModel = authViewModel,
                onSignOut = {
                    navController.navigate(Screen.Welcome.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.EmployerHome.route) {
            EmployerHomeScreen(
                authViewModel = authViewModel,
                onSignOut = {
                    navController.navigate(Screen.Welcome.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
    }
}

 */