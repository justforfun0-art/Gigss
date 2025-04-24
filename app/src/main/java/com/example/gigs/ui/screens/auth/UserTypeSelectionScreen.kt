// UserTypeSelectionScreen.kt
package com.example.gigs.ui.screens.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.gigs.R
import com.example.gigs.data.model.UserType
import com.example.gigs.ui.components.GigWorkHeaderText
import com.example.gigs.ui.components.GigWorkSubtitleText
import com.example.gigs.viewmodel.ProfileViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserTypeSelectionScreen(
    profileViewModel: ProfileViewModel,
    onUserTypeSelected: (UserType) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Select Account Type") }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            GigWorkHeaderText(text = "I want to...")

            Spacer(modifier = Modifier.height(32.dp))

            // Employee Option
            Card(
                onClick = {
                    profileViewModel.setUserType(UserType.EMPLOYEE)
                    onUserTypeSelected(UserType.EMPLOYEE)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_person),
                        contentDescription = "Employee",
                        tint = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Find Work",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    GigWorkSubtitleText(text = "Browse and apply to jobs in your area")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Employer Option
            Card(
                onClick = {
                    profileViewModel.setUserType(UserType.EMPLOYER)
                    onUserTypeSelected(UserType.EMPLOYER)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_business),
                        contentDescription = "Employer",
                        tint = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Hire People",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    GigWorkSubtitleText(text = "Post jobs and find qualified workers")
                }
            }
        }
    }
}