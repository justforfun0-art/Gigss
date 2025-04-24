package com.example.gigs.ui.screens.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.gigs.ui.components.GigWorkHeaderText
import com.example.gigs.ui.components.GigWorkPrimaryButton
import com.example.gigs.ui.components.GigWorkSubtitleText
import com.example.gigs.ui.components.GigWorkTextField
import com.example.gigs.viewmodel.ProfileState
import com.example.gigs.viewmodel.ProfileViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BasicProfileSetupScreen(
    profileViewModel: ProfileViewModel,
    onProfileCreated: () -> Unit
) {
    val profileState by profileViewModel.profileState.collectAsState()
    val userType by profileViewModel.userType.collectAsState()

    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(profileState) {
        when (profileState) {
            is ProfileState.BasicProfileCreated -> {
                onProfileCreated()
            }
            is ProfileState.Error -> {
                val errorMessage = (profileState as ProfileState.Error).message
                snackbarHostState.showSnackbar(errorMessage)
            }
            else -> {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Create Profile") }
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
            verticalArrangement = Arrangement.Top
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            GigWorkHeaderText(text = "Tell us about yourself")

            Spacer(modifier = Modifier.height(8.dp))

            GigWorkSubtitleText(
                text = if (userType.toString() == "EMPLOYEE")
                    "Set up your profile to find the perfect gig"
                else
                    "Set up your profile to find the perfect candidate"
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Profile picture placeholder
            IconButton(
                onClick = { /* TODO: Implement image picker */ },
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
            ) {
                Icon(
                    painter = androidx.compose.ui.res.painterResource(id = com.example.gigs.R.drawable.ic_person),
                    contentDescription = "Add Profile Picture",
                    modifier = Modifier.size(60.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            GigWorkTextField(
                value = fullName,
                onValueChange = { fullName = it },
                label = "Full Name"
            )

            Spacer(modifier = Modifier.height(16.dp))

            GigWorkTextField(
                value = email,
                onValueChange = { email = it },
                label = "Email",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
            )

            Spacer(modifier = Modifier.height(32.dp))

            GigWorkPrimaryButton(
                text = "Continue",
                onClick = { profileViewModel.createBasicProfile(fullName, email) },
                enabled = fullName.isNotEmpty() && email.isNotEmpty(),
                isLoading = profileState is ProfileState.Loading
            )
        }
    }
}