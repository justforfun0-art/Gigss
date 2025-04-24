package com.example.gigs.ui.screens.auth

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.gigs.data.model.OtpState
import com.example.gigs.ui.components.GigWorkHeaderText
import com.example.gigs.ui.components.GigWorkPrimaryButton
import com.example.gigs.ui.components.GigWorkSubtitleText
import com.example.gigs.ui.components.GigWorkTextField
import com.example.gigs.viewmodel.AuthViewModel
import com.example.gigs.viewmodel.ProfileViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneAuthScreen(
    authViewModel: AuthViewModel,
    profileViewModel: ProfileViewModel, // Added profileViewModel parameter
    onNavigateToOtp: () -> Unit
) {
    val otpState by authViewModel.otpState.collectAsState()
    val phoneNumber by authViewModel.phoneNumber.collectAsState()
    val userType by profileViewModel.userType.collectAsState() // Get current user type

    val context = LocalContext.current
    var isPhoneValid by remember { mutableStateOf(true) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Add a separate state to track verification request
    var isVerificationInProgress by remember { mutableStateOf(false) }

    // Validate phone number
    fun validatePhone(phone: String): Boolean {
        return phone.length >= 10
    }

    LaunchedEffect(otpState) {
        when (otpState) {
            is OtpState.Sent -> {
                isVerificationInProgress = false
                onNavigateToOtp()
            }
            is OtpState.AutoVerified -> {
                isVerificationInProgress = false
                onNavigateToOtp()
            }
            is OtpState.Error -> {
                isVerificationInProgress = false
                val errorMessage = (otpState as OtpState.Error).message
                snackbarHostState.showSnackbar(errorMessage)
            }
            else -> {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Phone Verification") }
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
            GigWorkHeaderText(text = "Enter your phone number")

            Spacer(modifier = Modifier.height(8.dp))

            GigWorkSubtitleText(text = "We'll send you a verification code")

            Spacer(modifier = Modifier.height(32.dp))

            GigWorkTextField(
                value = phoneNumber,
                onValueChange = { phone ->
                    // Format phone number if needed
                    val formattedPhone = if (phone.startsWith("+")) phone else phone
                    authViewModel.setPhoneNumber(formattedPhone)
                    isPhoneValid = validatePhone(formattedPhone)
                },
                label = "Phone Number",
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Phone
                ),
                isError = !isPhoneValid,
                errorText = if (!isPhoneValid) "Enter a valid phone number" else ""
            )

            Spacer(modifier = Modifier.height(8.dp))

            GigWorkSubtitleText(
                text = "Include country code, e.g. +91 for India"
            )

            Spacer(modifier = Modifier.height(24.dp))

            GigWorkPrimaryButton(
                text = "Send Verification Code",
                onClick = {
                    if (validatePhone(phoneNumber)) {
                        isVerificationInProgress = true
                        authViewModel.sendOtp(context as ComponentActivity)
                    } else {
                        isPhoneValid = false
                    }
                },
                enabled = phoneNumber.isNotEmpty() && !isVerificationInProgress,
                isLoading = isVerificationInProgress
            )
        }
    }
}