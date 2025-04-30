package com.example.gigs.ui.screens.jobs

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.BusinessCenter
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.gigs.data.model.ApplicationWithJob
import com.example.gigs.ui.theme.PrimaryBlue
import com.example.gigs.utils.DateUtils
import com.example.gigs.viewmodel.JobApplicationDetailsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobApplicationDetailsScreen(
    viewModel: JobApplicationDetailsViewModel = hiltViewModel(),
    applicationId: String,
    onBackPressed: () -> Unit,
    onMessageEmployer: (String) -> Unit,
    onWriteReview: (String, String, String) -> Unit
) {
    val application by viewModel.application.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val scrollState = rememberScrollState()

    LaunchedEffect(applicationId) {
        viewModel.loadApplicationDetails(applicationId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Application Details") },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            } else if (application == null) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Application not found",
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(onClick = onBackPressed) {
                        Text("Go Back")
                    }
                }
            } else {
                // Application details content
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .verticalScroll(scrollState)
                ) {
                    // Application status
                    val (statusColor, statusLabel) = when (application?.status?.uppercase()) {
                        "APPLIED" -> Pair(PrimaryBlue, "Application Submitted")
                        "SHORTLISTED" -> Pair(MaterialTheme.colorScheme.tertiary, "Shortlisted")
                        "HIRED" -> Pair(MaterialTheme.colorScheme.secondary, "Hired")
                        "REJECTED" -> Pair(MaterialTheme.colorScheme.error, "Application Rejected")
                        else -> Pair(MaterialTheme.colorScheme.outlineVariant, application?.status ?: "Unknown")
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(statusColor.copy(alpha = 0.1f))
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = statusLabel,
                                style = MaterialTheme.typography.titleMedium,
                                color = statusColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Job title
                    Text(
                        text = application?.job?.title ?: "Unknown Job",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Employer info (this would be better if you have employer name)
                    Text(
                        text = "Employer ID: ${application?.job?.employerId}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Application dates
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.DateRange,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )

                                Spacer(modifier = Modifier.width(4.dp))

                                Text(
                                    text = "Applied On",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            Text(
                                text = application?.appliedAt?.let { DateUtils.formatDate(it) } ?: "Unknown",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        application?.updatedAt?.let {
                            Column(horizontalAlignment = Alignment.End) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Update,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )

                                    Spacer(modifier = Modifier.width(4.dp))

                                    Text(
                                        text = "Last Updated",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                Text(
                                    text = DateUtils.formatDate(it),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Job details section
                    Text(
                        text = "Job Details",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Divider()

                    Spacer(modifier = Modifier.height(16.dp))

                    // Job location
                    InfoRow(
                        icon = Icons.Default.LocationOn,
                        label = "Location",
                        value = application?.job?.location ?: "Not specified"
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Salary range
                    InfoRow(
                        icon = Icons.Default.AttachMoney,
                        label = "Salary",
                        value = application?.job?.salaryRange ?: "Not specified"
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Job type
                    InfoRow(
                        icon = Icons.Default.BusinessCenter,
                        label = "Job Type",
                        value = application?.job?.jobType?.toString()?.replace("_", " ")?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "Not specified"
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Job description
                    Text(
                        text = "Description",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = application?.job?.description ?: "No description available",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Action buttons
                    if (application?.status?.uppercase() == "HIRED") {
                        // Show write review button for completed jobs
                        Button(
                            onClick = {
                                application?.let {
                                    onWriteReview(
                                        it.jobId,
                                        it.job.employerId,
                                        "Employer" // Ideally use actual employer name
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            Text("Write a Review")
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Message employer button
                    OutlinedButton(
                        onClick = {
                            application?.job?.employerId?.let { employerId ->
                                onMessageEmployer(employerId)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Email,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Text("Message Employer")
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
fun InfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )

        Spacer(modifier = Modifier.width(8.dp))

        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}