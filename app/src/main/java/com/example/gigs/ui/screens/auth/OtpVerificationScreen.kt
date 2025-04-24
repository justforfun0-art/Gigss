package com.example.gigs.ui.screens.auth

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OtpVerificationScreen(
    authViewModel: AuthViewModel,
    onVerificationSuccess: () -> Unit
) {
    val otpState by authViewModel.otpState.collectAsState()
    val phoneNumber by authViewModel.phoneNumber.collectAsState()

    var otp by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(otpState) {
        when (otpState) {
            is OtpState.Verified, is OtpState.AutoVerified -> {
                onVerificationSuccess()
            }
            is OtpState.Error -> {
                val errorMessage = (otpState as OtpState.Error).message
                snackbarHostState.showSnackbar(errorMessage)
            }
            else -> {}
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