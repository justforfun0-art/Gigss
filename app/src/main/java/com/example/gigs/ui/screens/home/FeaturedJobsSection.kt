package com.example.gigs.ui.screens.home

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.gigs.data.model.Job
import com.example.gigs.data.model.JobWithEmployer
import com.example.gigs.ui.components.SwipeableJobCards
import com.example.gigs.viewmodel.JobViewModel
import kotlinx.coroutines.launch

@Composable
fun FeaturedJobsSection(
    jobViewModel: JobViewModel,
    onJobDetails: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val featuredJobs by jobViewModel.featuredJobs.collectAsState()
    val isLoading by jobViewModel.isLoading.collectAsState()
    val scope = rememberCoroutineScope()

    // Show snackbar on job action
    val snackbarHostState = remember { SnackbarHostState() }

    // Create JobWithEmployer list for better employer name display
    val jobsWithEmployers = remember(featuredJobs) {
        featuredJobs.map { job ->
            // Create a more user-friendly employer name
            val employerName = when {
                job.employerId.isNotEmpty() -> "Company ${job.employerId.takeLast(4)}"
                else -> "Unknown Employer"
            }

            JobWithEmployer(job, employerName)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(480.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator()
        } else {
            SwipeableJobCards(
                jobs = featuredJobs,
                jobsWithEmployers = jobsWithEmployers, // Add the employer information
                onJobAccepted = { job ->
                    // Apply for job
                    scope.launch {
                        jobViewModel.applyForJob(job.id)
                        snackbarHostState.showSnackbar(
                            message = "Applied for: ${job.title}",
                            duration = SnackbarDuration.Short
                        )
                    }
                },
                onJobRejected = { job ->
                    // Reject job (optionally mark as not interested)
                    scope.launch {
                        jobViewModel.markJobAsNotInterested(job.id)
                        snackbarHostState.showSnackbar(
                            message = "Rejected: ${job.title}",
                            duration = SnackbarDuration.Short
                        )
                    }
                },
                onJobDetails = { jobId ->
                    // Navigate to job details screen
                    onJobDetails(jobId)
                }
            )
        }

        // Snackbar host
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
        )
    }
}

// Add these methods to JobViewModel.kt
/**
 * fun applyForJob(jobId: String) {
 *     viewModelScope.launch {
 *         // Call repository to apply for job
 *         val result = jobRepository.applyForJob(jobId)
 *         // Handle result
 *     }
 * }
 *
 * fun markJobAsNotInterested(jobId: String) {
 *     viewModelScope.launch {
 *         // Call repository to mark job as not interested
 *         val result = jobRepository.markJobAsNotInterested(jobId)
 *         // Handle result or just track locally
 *     }
 * }
 */