package com.example.gigs.ui.screens.welcome

// WelcomeScreen.kt

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.gigs.R
import com.example.gigs.data.model.UserType
import com.example.gigs.ui.components.GigWorkOutlinedButton
import com.example.gigs.ui.components.GigWorkPrimaryButton
import com.example.gigs.ui.components.GigWorkSubtitleText
import com.example.gigs.ui.theme.PrimaryBlue
import com.example.gigs.viewmodel.AuthViewModel
import kotlinx.coroutines.launch

@Composable
fun WelcomeScreen(
    onFindJobsClick: () -> Unit,
    onPostJobsClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.weight(0.5f))

            // Logo
            Card(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White
                )
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_logo),
                        contentDescription = "GigWork Logo",
                        modifier = Modifier.size(60.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // App Name
            Text(
                text = "GigWork",
                style = MaterialTheme.typography.headlineMedium,
                color = PrimaryBlue
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Tagline
            Text(
                text = "Connect with opportunities",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Info card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color.LightGray.copy(alpha = 0.2f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Your marketplace for local jobs",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    GigWorkSubtitleText(
                        text = "Whether you're looking for work or hiring, GigWork helps you connect with opportunities in your area.",
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Buttons
            GigWorkPrimaryButton(
                text = "Find Jobs",
                onClick = onFindJobsClick,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            GigWorkOutlinedButton(
                text = "Post Jobs",
                onClick = onPostJobsClick,
                modifier = Modifier.fillMaxWidth()
            )



          /*  TypeSelectionWithCheck(
                authViewModel = hiltViewModel(),
                onFindJobsClick = onFindJobsClick,
                onPostJobsClick = onPostJobsClick
            )

           */
        }
    }

}
@Composable
fun TypeSelectionWithCheck(
    authViewModel: AuthViewModel = hiltViewModel(),
    onFindJobsClick: () -> Unit,
    onPostJobsClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Employee button
    GigWorkPrimaryButton(
        text = "Find Jobs",
        onClick = {
            scope.launch {
                val result = authViewModel.checkUserType(UserType.EMPLOYEE)
                if (result.isSuccess) {
                    onFindJobsClick()
                } else {
                    // Show error toast
                    Toast.makeText(context, result.exceptionOrNull()?.message ?: "Cannot register as employee", Toast.LENGTH_LONG).show()
                }
            }
        },
        modifier = Modifier.fillMaxWidth()
    )

    Spacer(modifier = Modifier.height(12.dp))

    // Employer button
    GigWorkOutlinedButton(
        text = "Post Jobs",
        onClick = {
            scope.launch {
                val result = authViewModel.checkUserType(UserType.EMPLOYER)
                if (result.isSuccess) {
                    onPostJobsClick()
                } else {
                    // Show error toast
                    Toast.makeText(context, result.exceptionOrNull()?.message ?: "Cannot register as employer", Toast.LENGTH_LONG).show()
                }
            }
        },
        modifier = Modifier.fillMaxWidth()
    )
}