// Fixed ActiveJobCard.kt
package com.example.gigs.ui.components

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.gigs.data.model.ApplicationStatus
import com.example.gigs.data.model.ApplicationWithJob
import com.example.gigs.data.model.getDisplayTextWithJob
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant


@Composable
fun ActiveJobCard(
    activeJob: ApplicationWithJob,
    onAcceptJob: (() -> Unit)? = null,
    onDeclineJob: (() -> Unit)? = null,
    onStartWork: (String) -> Unit,
    onCompleteWork: () -> Unit,
    onEnterOtp: (String) -> Unit,
    isLoading: Boolean = false,
    errorMessage: String? = null,
    modifier: Modifier = Modifier
) {
    var showOtpDialog by remember { mutableStateOf(false) }
    var otpInput by remember { mutableStateOf("") }
    var workDuration by remember { mutableStateOf("00:00:00") }

    // Update work duration timer for WORK_IN_PROGRESS status
    LaunchedEffect(activeJob.status, activeJob.workSession?.workStartTime) {
        if (activeJob.status == ApplicationStatus.WORK_IN_PROGRESS) {
            val startTimeString = activeJob.workSession?.workStartTime
            val startTime = if (startTimeString != null) {
                try {
                    Instant.parse(startTimeString)
                } catch (e: Exception) {
                    Log.w("ActiveJobCard", "Failed to parse work start time: $startTimeString")
                    Instant.now()
                }
            } else {
                Instant.now()
            }

            Log.d("ActiveJobCard", "🚀 Starting timer from: $startTime")

            while (activeJob.status == ApplicationStatus.WORK_IN_PROGRESS) {
                val duration = Duration.between(startTime, Instant.now())
                val hours = duration.toHours()
                val minutes = duration.toMinutes() % 60
                val seconds = duration.seconds % 60
                workDuration = String.format("%02d:%02d:%02d", hours, minutes, seconds)
                delay(1000)
            }
        } else {
            workDuration = "00:00:00"
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (activeJob.status) {
                ApplicationStatus.SELECTED -> MaterialTheme.colorScheme.secondaryContainer
                ApplicationStatus.ACCEPTED -> MaterialTheme.colorScheme.primaryContainer
                ApplicationStatus.WORK_IN_PROGRESS -> MaterialTheme.colorScheme.tertiaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header with status icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = when (activeJob.status) {
                            ApplicationStatus.SELECTED -> Icons.Default.CheckCircle
                            ApplicationStatus.ACCEPTED -> Icons.Default.Schedule
                            ApplicationStatus.WORK_IN_PROGRESS -> Icons.Default.Timer
                            else -> Icons.Default.Work
                        },
                        contentDescription = null,
                        tint = when (activeJob.status) {
                            ApplicationStatus.SELECTED -> MaterialTheme.colorScheme.secondary
                            ApplicationStatus.ACCEPTED -> MaterialTheme.colorScheme.primary
                            ApplicationStatus.WORK_IN_PROGRESS -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )

                    Text(
                        text = "Active Job",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Status badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            when (activeJob.status) {
                                ApplicationStatus.SELECTED -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)
                                ApplicationStatus.ACCEPTED -> MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                ApplicationStatus.WORK_IN_PROGRESS -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            }
                        )
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = when (activeJob.status) {
                            ApplicationStatus.SELECTED -> "Ready to Accept"
                            ApplicationStatus.ACCEPTED -> "Ready to Start"
                            ApplicationStatus.WORK_IN_PROGRESS -> "In Progress"
                            else -> activeJob.status.name
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = when (activeJob.status) {
                            ApplicationStatus.SELECTED -> MaterialTheme.colorScheme.secondary
                            ApplicationStatus.ACCEPTED -> MaterialTheme.colorScheme.primary
                            ApplicationStatus.WORK_IN_PROGRESS -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Job details
            Text(
                text = activeJob.job.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Location and salary info
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = activeJob.job.location,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            activeJob.job.salaryRange?.let { salary ->
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AttachMoney,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = salary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Status message
            Text(
                text = activeJob.status.getDisplayTextWithJob(activeJob.job.title),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                        RoundedCornerShape(8.dp)
                    )
                    .padding(12.dp)
            )

            // Work duration timer - Show when work is in progress
            if (activeJob.status == ApplicationStatus.WORK_IN_PROGRESS) {
                Spacer(modifier = Modifier.height(12.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Work Duration",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = workDuration,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action buttons based on status
            when (activeJob.status) {
                ApplicationStatus.SELECTED -> {
                    // Show Accept/Decline buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                Log.d("ActiveJobCard", "🚀 Decline Job button clicked")
                                onDeclineJob?.invoke()
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !isLoading
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Processing...")
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Decline")
                            }
                        }

                        Button(
                            onClick = {
                                Log.d("ActiveJobCard", "🚀 Accept Job button clicked")
                                onAcceptJob?.invoke()
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !isLoading,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary
                            )
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = MaterialTheme.colorScheme.onSecondary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Accepting...")
                            } else {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Accept Job")
                            }
                        }
                    }

                    // Instructions
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "You've been selected for this job! Accept to confirm your participation or decline if you're not available.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                ApplicationStatus.ACCEPTED -> {
                    // Show Start Work button with OTP input
                    Button(
                        onClick = {
                            Log.d("ActiveJobCard", "🚀 Start Work button clicked")
                            showOtpDialog = true
                            otpInput = ""
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isLoading,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Starting...")
                        } else {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Start Work")
                        }
                    }

                    // Instructions
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Job accepted! Get the OTP from your employer when you arrive at the work location to start work",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                ApplicationStatus.WORK_IN_PROGRESS -> {
                    Button(
                        onClick = {
                            Log.d("ActiveJobCard", "🚀 Complete Work button clicked")
                            onCompleteWork()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isLoading,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary
                        )
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = MaterialTheme.colorScheme.onTertiary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Completing...")
                        } else {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Complete Work")
                        }
                    }
                }

                else -> {
                    Text(
                        text = "Status: ${activeJob.status}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Error message display
            errorMessage?.let { error ->
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }
    }

    // OTP Dialog - shown when Start Work is clicked for ACCEPTED status
    if (showOtpDialog) {
        AlertDialog(
            onDismissRequest = {
                showOtpDialog = false
                otpInput = ""
            },
            title = {
                Text("Enter Work OTP")
            },
            text = {
                Column {
                    Text(
                        text = "Please enter the 6-digit OTP provided by your employer to start work.",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = otpInput,
                        onValueChange = {
                            if (it.length <= 6 && it.all { char -> char.isDigit() }) {
                                otpInput = it
                            }
                        },
                        label = { Text("6-digit OTP") },
                        placeholder = { Text("123456") },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        isError = errorMessage?.contains("OTP", ignoreCase = true) == true
                    )

                    // Show OTP-specific error in dialog
                    if (errorMessage?.contains("OTP", ignoreCase = true) == true ||
                        errorMessage?.contains("expired", ignoreCase = true) == true) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = errorMessage,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (otpInput.length == 6) {
                            Log.d("ActiveJobCard", "🚀 Submitting OTP: '$otpInput'")
                            onStartWork(otpInput)
                            showOtpDialog = false
                            otpInput = ""
                        }
                    },
                    enabled = otpInput.length == 6 && !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text("Verify & Start")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showOtpDialog = false
                        otpInput = ""
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}