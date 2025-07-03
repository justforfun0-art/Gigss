package com.example.gigs.ui.screens.jobs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.WorkHistory
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.gigs.data.model.ApplicationStatus
import com.example.gigs.data.model.ApplicationWithJob
import com.example.gigs.data.model.getDisplayTextWithJob
import com.example.gigs.data.model.shouldShowInStepper
import com.example.gigs.data.repository.ApplicationRepository
import com.example.gigs.ui.components.ActiveJobCard
import com.example.gigs.ui.components.ApplicationStatusStepper
import com.example.gigs.ui.theme.PrimaryBlue
import com.example.gigs.utils.DateUtils
import com.example.gigs.viewmodel.JobHistoryViewModel
import com.example.gigs.viewmodel.JobViewModel
import kotlinx.coroutines.launch

// Data class for status filter options
data class StatusFilter(
    val status: ApplicationStatus,
    val displayName: String,
    val isSelected: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun JobHistoryScreen(
    viewModel: JobHistoryViewModel = hiltViewModel(),
    onJobSelected: (String) -> Unit,
    onApplicationSelected: (String) -> Unit,
    onBackPressed: () -> Unit
) {
    val allApplications by viewModel.allApplications.collectAsState()
    val activeApplications by viewModel.activeApplications.collectAsState()
    val completedApplications by viewModel.completedApplications.collectAsState()
    val rejectedApplications by viewModel.rejectedApplications.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    // Get JobViewModel to access ApplicationRepository
    val jobViewModel: JobViewModel = hiltViewModel()
    val applicationRepository = jobViewModel.applicationRepository

    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("All", "Active", "Completed", "Rejected")

    // 🚀 NEW: Filter state management
    var showFilters by remember { mutableStateOf(false) }
    var selectedStatusFilters by remember { mutableStateOf(setOf<ApplicationStatus>()) }

    // Create status filter options
    val availableStatusFilters = remember {
        listOf(
            StatusFilter(ApplicationStatus.APPLIED, "Applied"),
            StatusFilter(ApplicationStatus.SELECTED, "Selected"),
            StatusFilter(ApplicationStatus.ACCEPTED, "Accepted"),
            StatusFilter(ApplicationStatus.WORK_IN_PROGRESS, "Work in Progress"),
            StatusFilter(ApplicationStatus.COMPLETED, "Completed"),
            StatusFilter(ApplicationStatus.REJECTED, "Rejected"),
            StatusFilter(ApplicationStatus.DECLINED, "Declined"),
            StatusFilter(ApplicationStatus.NOT_INTERESTED, "Not Interested")
        )
    }

    // Active job state management
    var activeJob by remember { mutableStateOf<ApplicationWithJob?>(null) }
    var isLoadingActiveJob by remember { mutableStateOf(false) }
    var activeJobError by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Load applications and find active job
    LaunchedEffect(Unit) {
        viewModel.loadApplicationsHistory()
        viewModel.refreshApplicationHistory()

        // Find active job
        coroutineScope.launch {
            try {
                activeJob = viewModel.getActiveJob()
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    // Update active job when applications change
    LaunchedEffect(activeApplications) {
        activeJob = viewModel.getActiveJob()
    }

    // 🚀 NEW: Function to sort applications with priority order
    fun sortApplications(applications: List<ApplicationWithJob>): List<ApplicationWithJob> {
        return applications.sortedWith(compareBy<ApplicationWithJob> { app ->
            // First sort by priority (active statuses first, not interested last)
            when (app.status) {
                ApplicationStatus.WORK_IN_PROGRESS -> 0      // Highest priority
                ApplicationStatus.SELECTED -> 1              // High priority
                ApplicationStatus.ACCEPTED -> 2              // High priority
                ApplicationStatus.APPLIED -> 3               // Medium priority
                ApplicationStatus.COMPLETED -> 4             // Medium-low priority
                ApplicationStatus.REJECTED -> 5              // Low priority
                ApplicationStatus.DECLINED -> 6              // Low priority
                ApplicationStatus.NOT_INTERESTED -> 7        // Lowest priority
                else -> 8
            }
        }.thenByDescending {
            // Then sort by last updated (most recent first within same priority)
            it.updatedAt ?: it.appliedAt ?: ""
        })
    }

    // 🚀 NEW: Function to filter applications based on selected filters
    fun filterApplications(applications: List<ApplicationWithJob>): List<ApplicationWithJob> {
        return if (selectedStatusFilters.isEmpty()) {
            sortApplications(applications)
        } else {
            sortApplications(applications.filter { app ->
                selectedStatusFilters.contains(app.status)
            })
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Job History") },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // 🚀 NEW: Filter toggle button
                    IconButton(
                        onClick = { showFilters = !showFilters }
                    ) {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = "Filter",
                            tint = if (selectedStatusFilters.isNotEmpty() || showFilters) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            TabRow(selectedTabIndex = selectedTabIndex) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = {
                            selectedTabIndex = index
                            // Clear filters when switching tabs
                            selectedStatusFilters = setOf()
                        },
                        text = { Text(title) }
                    )
                }
            }

            // 🚀 NEW: Status filter chips (only show for "All" tab)
            AnimatedVisibility(
                visible = showFilters && selectedTabIndex == 0,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Filter by Status",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )

                            if (selectedStatusFilters.isNotEmpty()) {
                                TextButton(
                                    onClick = { selectedStatusFilters = setOf() }
                                ) {
                                    Text("Clear All")
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            availableStatusFilters.forEach { filter ->
                                FilterChip(
                                    selected = selectedStatusFilters.contains(filter.status),
                                    onClick = {
                                        selectedStatusFilters = if (selectedStatusFilters.contains(filter.status)) {
                                            selectedStatusFilters - filter.status
                                        } else {
                                            selectedStatusFilters + filter.status
                                        }
                                    },
                                    label = { Text(filter.displayName) },
                                    leadingIcon = if (selectedStatusFilters.contains(filter.status)) {
                                        {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    } else null
                                )
                            }
                        }

                        // Show active filter count
                        if (selectedStatusFilters.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "${selectedStatusFilters.size} filter(s) applied",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                // 🚀 UPDATED: Apply sorting and filtering to current list
                val currentList = when (selectedTabIndex) {
                    0 -> filterApplications(allApplications) // Apply filters only to "All" tab
                    1 -> sortApplications(activeApplications)
                    2 -> sortApplications(completedApplications)
                    3 -> sortApplications(rejectedApplications)
                    else -> sortApplications(allApplications)
                }

                if (currentList.isEmpty()) {
                    EmptyHistoryMessage(
                        tabName = tabs[selectedTabIndex],
                        hasFilters = selectedStatusFilters.isNotEmpty() && selectedTabIndex == 0
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Show active job card at the top of Active tab
                        if (selectedTabIndex == 1 && activeJob != null) {
                            item {
                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = "Current Active Job",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )

                                ActiveJobCard(
                                    activeJob = activeJob!!,
                                    onStartWork = { otp ->
                                        coroutineScope.launch {
                                            isLoadingActiveJob = true
                                            activeJobError = null

                                            try {
                                                // Use ApplicationRepository through JobViewModel
                                                val result = applicationRepository.startWorkWithOtp(activeJob!!.id, otp)
                                                if (result.isSuccess) {
                                                    snackbarHostState.showSnackbar("Work started successfully!")
                                                    // Refresh history
                                                    viewModel.refreshApplicationHistory()
                                                    // Update active job
                                                    activeJob = viewModel.getActiveJob()
                                                } else {
                                                    activeJobError = result.exceptionOrNull()?.message ?: "Invalid OTP"
                                                    snackbarHostState.showSnackbar(
                                                        activeJobError ?: "Failed to start work",
                                                        duration = SnackbarDuration.Long
                                                    )
                                                }
                                            } catch (e: Exception) {
                                                activeJobError = e.message ?: "Failed to start work"
                                            } finally {
                                                isLoadingActiveJob = false
                                            }
                                        }
                                    },
                                    onCompleteWork = {
                                        coroutineScope.launch {
                                            isLoadingActiveJob = true
                                            activeJobError = null

                                            try {
                                                // Use ApplicationRepository through JobViewModel
                                                val result = applicationRepository.completeWork(activeJob!!.id)
                                                if (result.isSuccess) {
                                                    snackbarHostState.showSnackbar("Work completed successfully!")
                                                    activeJob = null
                                                    viewModel.refreshApplicationHistory()
                                                } else {
                                                    activeJobError = result.exceptionOrNull()?.message ?: "Failed to complete work"
                                                    snackbarHostState.showSnackbar(
                                                        activeJobError ?: "Failed to complete",
                                                        duration = SnackbarDuration.Long
                                                    )
                                                }
                                            } catch (e: Exception) {
                                                activeJobError = e.message ?: "Failed to complete work"
                                            } finally {
                                                isLoadingActiveJob = false
                                            }
                                        }
                                    },
                                    onEnterOtp = { /* Handled in onStartWork */ },
                                    isLoading = isLoadingActiveJob,
                                    errorMessage = activeJobError
                                )

                                Divider(
                                    modifier = Modifier.padding(vertical = 16.dp),
                                    thickness = 2.dp,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                )

                                if (currentList.size > 1) {
                                    Text(
                                        text = "Other Active Applications",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    )
                                }
                            }
                        }

                        // 🚀 NEW: Show filter summary for "All" tab
                        if (selectedTabIndex == 0 && selectedStatusFilters.isNotEmpty()) {
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                    )
                                ) {
                                    Text(
                                        text = "Showing ${currentList.size} job(s) with selected status filters",
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(12.dp),
                                        textAlign = TextAlign.Center,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        items(
                            items = currentList.filter {
                                // Don't show the active job again in the list
                                it.id != activeJob?.id
                            },
                            key = { it.id }
                        ) { application ->
                            JobHistoryItemWithStepper(
                                application = application,
                                onClick = { onJobSelected(application.jobId) },
                                onViewApplicationDetails = { onApplicationSelected(application.id) }
                            )

                            Divider(
                                modifier = Modifier.padding(vertical = 8.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        }

                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyHistoryMessage(
    tabName: String,
    hasFilters: Boolean = false
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = if (hasFilters) Icons.Default.FilterList else Icons.Default.WorkHistory,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = if (hasFilters) "No Matching Jobs" else "No $tabName Jobs",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (hasFilters) {
                    "No jobs match your selected filters. Try adjusting your filter criteria."
                } else {
                    when (tabName) {
                        "All" -> "You haven't applied to any jobs yet"
                        "Active" -> "You don't have any active job applications"
                        "Completed" -> "You haven't completed any jobs yet"
                        "Rejected" -> "You don't have any rejected applications"
                        else -> "No job history to show"
                    }
                },
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// Keep all existing components (JobHistoryList, JobHistoryItemWithStepper) unchanged
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobHistoryList(
    applications: List<ApplicationWithJob>,
    onJobSelected: (String) -> Unit,
    onApplicationSelected: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        items(applications) { application ->
            JobHistoryItemWithStepper(
                application = application,
                onClick = { onJobSelected(application.jobId) },
                onViewApplicationDetails = { onApplicationSelected(application.id) }
            )

            Divider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
        }

        // Add bottom padding
        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobHistoryItemWithStepper(
    application: ApplicationWithJob,
    onClick: () -> Unit,
    onViewApplicationDetails: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Job title and basic info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = application.job.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = application.job.location,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = application.job.salaryRange ?: "Salary not specified",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // Expand/collapse button
                IconButton(
                    onClick = { isExpanded = !isExpanded },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Application status stepper - FIXED: Handle NOT_INTERESTED properly
            if (application.status.shouldShowInStepper()) {
                ApplicationStatusStepper(
                    applicationStatus = application.status,
                    isCompact = !isExpanded,
                    isVertical = isExpanded,
                    showLabels = true,
                    applicationWithJob = application,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                // For NOT_INTERESTED, show compact status indicator
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.error),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ThumbDown,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Not Interested",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.error
                        )

                        if (isExpanded) {
                            Text(
                                text = application.status.getDisplayTextWithJob(application.job.title),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }
            }

            // FIXED: Properly handle expanded content without overlap
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp) // Add top padding to prevent overlap
                ) {
                    // Additional details when expanded
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        application.appliedAt?.let { dateString ->
                            Column {
                                Text(
                                    text = "Applied On",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = DateUtils.formatDate(dateString),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }

                        application.updatedAt?.let { dateString ->
                            Column(
                                horizontalAlignment = Alignment.End
                            ) {
                                Text(
                                    text = "Last Updated",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = DateUtils.formatDate(dateString),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }

                    // Job description preview
                    if (application.job.description.isNotBlank()) {
                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Job Description",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = application.job.description,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }

                    // Show status-specific message for NOT_INTERESTED
                    if (application.status == ApplicationStatus.NOT_INTERESTED) {
                        Spacer(modifier = Modifier.height(12.dp))

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                            )
                        ) {
                            Text(
                                text = "You can reconsider this job from the rejected jobs section",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onClick
                ) {
                    Text(
                        text = "View Job",
                        style = MaterialTheme.typography.labelMedium
                    )
                }

                TextButton(
                    onClick = onViewApplicationDetails
                ) {
                    Text(
                        text = "Application Details",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = "View application details",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}