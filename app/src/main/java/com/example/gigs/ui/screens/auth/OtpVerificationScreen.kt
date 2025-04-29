package com.example.gigs.ui.screens.auth

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.gigs.data.model.OtpState
import com.example.gigs.ui.components.GigWorkHeaderText
import com.example.gigs.ui.components.GigWorkPrimaryButton
import com.example.gigs.ui.components.GigWorkSubtitleText
import com.example.gigs.ui.components.GigWorkTextField
import com.example.gigs.viewmodel.AuthViewModel
import com.example.gigs.viewmodel.ProfileViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OtpVerificationScreen(
    authViewModel: AuthViewModel,
    profileViewModel: ProfileViewModel = hiltViewModel(),
    onVerificationSuccess: () -> Unit,
    onNavigateToWelcome: () -> Unit = {} // Optional parameter for navigation to welcome screen on error
) {
    val otpState by authViewModel.otpState.collectAsState()
    val phoneNumber by authViewModel.phoneNumber.collectAsState()
    val userType by profileViewModel.userType.collectAsState()
    val authState by authViewModel.authState.collectAsState()

    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var otp by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }

    // Flag to track if user type check has been performed
    var userTypeChecked by remember { mutableStateOf(false) }

    // Flag to prevent verification success callback when role mismatch
    var roleCheckFailed by remember { mutableStateOf(false) }

    // Handle OTP state changes
    LaunchedEffect(otpState) {
        when (otpState) {
            is OtpState.Verified, is OtpState.AutoVerified -> {
                // Only check user type if not already checked
                if (!userTypeChecked) {
                    userTypeChecked = true

                    // Use the scope to launch the coroutine for checking user type
                    scope.launch {
                        val result = authViewModel.checkUserType(userType)
                        if (result.isSuccess) {
                            // Only proceed with navigation if role check passed
                            if (!roleCheckFailed) {
                                onVerificationSuccess()
                            }
                        } else {
                            // User type is incompatible
                            roleCheckFailed = true

                            // Get error message
                            val errorMsg = result.exceptionOrNull()?.message ?:
                            "You are not allowed to register with this account type."

                            // Show error as Toast
                            Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()

                            // Small delay to ensure the Toast is visible
                            delay(500)

                            // Force logout using your new method
                            authViewModel.forceLogout()

                            // Navigate immediately without waiting for dialog
                            onNavigateToWelcome()
                        }
                    }
                }
            }
            is OtpState.Error -> {
                val errorMsg = (otpState as OtpState.Error).message
                snackbarHostState.showSnackbar(errorMsg)
            }
            else -> {
                // Reset user type checked flag when OTP state changes
                userTypeChecked = false
                roleCheckFailed = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("OTP Verification") }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            GigWorkHeaderText(text = "Enter verification code")

            Spacer(modifier = Modifier.height(8.dp))

            GigWorkSubtitleText(text = "We've sent a code to $phoneNumber")

            Spacer(modifier = Modifier.height(32.dp))

            GigWorkTextField(
                value = otp,
                onValueChange = { code ->
                    if (code.length <= 6 && code.all { it.isDigit() }) {
                        otp = code
                    }
                },
                label = "Verification Code",
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number
                )
            )

            Spacer(modifier = Modifier.height(24.dp))

            GigWorkPrimaryButton(
                text = "Verify",
                onClick = { authViewModel.verifyOtp(otp) },
                enabled = otp.length == 6,
                isLoading = otpState is OtpState.Initial && otp.length == 6
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Didn't receive the code?")

                Spacer(modifier = Modifier.width(8.dp))

                TextButton(
                    onClick = {
                        authViewModel.resetOtpState()
                        authViewModel.sendOtp(context as androidx.activity.ComponentActivity)
                    }
                ) {
                    Text("Resend")
                }
            }
        }
    }
}