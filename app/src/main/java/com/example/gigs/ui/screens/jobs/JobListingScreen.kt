package com.example.gigs.ui.screens.jobs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.gigs.data.model.Job
import com.example.gigs.viewmodel.JobViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobListingScreen(
    jobViewModel: JobViewModel,
    district: String,
    onNavigateToHome: () -> Unit,
    onJobSelected: (String) -> Unit,
    onBackPressed: () -> Unit
) {
    // Use lifecycle-aware state collection to prevent unnecessary recompositions
    val jobs by jobViewModel.jobs.collectAsStateWithLifecycle()
    val isLoading by jobViewModel.isLoading.collectAsStateWithLifecycle()
    val appliedJobIds by jobViewModel.appliedJobIds.collectAsStateWithLifecycle()
    val currentSortOption by jobViewModel.currentSortOption.collectAsStateWithLifecycle()
    val currentFilters by jobViewModel.jobFilters.collectAsStateWithLifecycle()

    // Memoize expensive calculations
    val appliedJobsSet by remember(appliedJobIds) {
        derivedStateOf { appliedJobIds.toSet() }
    }

    val sortedAndFilteredJobs by remember(jobs, currentSortOption, currentFilters) {
        derivedStateOf {
            // Apply sorting and filtering logic here if needed
            jobs.distinctBy { it.id }
        }
    }

    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var showFilterDialog by remember { mutableStateOf(false) }

    // Load jobs only once when district changes
    LaunchedEffect(district) {
        jobViewModel.getJobsByDistrict(district)
    }

    // Handle pagination with optimized scroll detection
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo }
            .collect { layoutInfo ->
                val totalItemsCount = layoutInfo.totalItemsCount
                val lastVisibleItemIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0

                // Load more when near the end (but not if we're loading or have no items)
                if (totalItemsCount > 0 &&
                    lastVisibleItemIndex >= totalItemsCount - 3 &&
                    !isLoading &&
                    sortedAndFilteredJobs.isNotEmpty()) {
                    // Trigger load more if your ViewModel supports pagination
                    // jobViewModel.loadMoreJobs()
                }
            }
    }

    Scaffold(
        topBar = {
            OptimizedTopAppBar(
                district = district,
                onBackPressed = onBackPressed,
                onFilterClick = { showFilterDialog = true }
            )
        }
    ) { paddingValues ->
        when {
            isLoading && sortedAndFilteredJobs.isEmpty() -> {
                LoadingContent(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                )
            }

            sortedAndFilteredJobs.isEmpty() && !isLoading -> {
                EmptyJobsContent(
                    district = district,
                    onShowAllJobs = {
                        scope.launch {
                            jobViewModel.getAllJobsWithoutFiltering(district)
                        }
                    },
                    onNavigateToHome = onNavigateToHome,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                )
            }

            else -> {
                JobListContent(
                    jobs = sortedAndFilteredJobs,
                    appliedJobsSet = appliedJobsSet,
                    district = district,
                    isLoading = isLoading,
                    listState = listState,
                    onJobSelected = onJobSelected,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                )
            }
        }

        // Filter Dialog
        if (showFilterDialog) {
            JobSortFilterDialog(
                currentSortOption = currentSortOption,
                currentFilters = currentFilters,
                onDismiss = { showFilterDialog = false },
                onApplyFilters = { sortOption, filters ->
                    jobViewModel.setSortOption(sortOption)
                    jobViewModel.setJobFilters(filters)
                    showFilterDialog = false
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OptimizedTopAppBar(
    district: String,
    onBackPressed: () -> Unit,
    onFilterClick: () -> Unit
) {
    TopAppBar(
        title = {
            Text(
                text = "Jobs in $district",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        navigationIcon = {
            IconButton(onClick = onBackPressed) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back"
                )
            }
        },
        actions = {
            IconButton(onClick = onFilterClick) {
                Icon(
                    imageVector = Icons.Default.FilterList,
                    contentDescription = "Filter and Sort"
                )
            }
        }
    )
}

@Composable
private fun LoadingContent(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Loading jobs...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmptyJobsContent(
    district: String,
    onShowAllJobs: () -> Unit,
    onNavigateToHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.SearchOff,
            contentDescription = null,
            modifier = Modifier
                .size(80.dp)
                .padding(bottom = 24.dp),
            tint = Color(0xFF4267B2).copy(alpha = 0.7f)
        )

        Text(
            text = "No Available Jobs",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "You've already applied to or rejected all current job openings. Check back later for new opportunities.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Location: $district",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onShowAllJobs,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ),
            shape = RoundedCornerShape(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Show All Jobs Again",
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onNavigateToHome,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF4267B2)
            ),
            shape = RoundedCornerShape(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Home,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = Color.White
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Go to Home",
                    color = Color.White,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(64.dp))
    }
}

@Composable
private fun JobListContent(
    jobs: List<Job>,
    appliedJobsSet: Set<String>,
    district: String,
    isLoading: Boolean,
    listState: LazyListState,
    onJobSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.padding(horizontal = 16.dp),
        state = listState,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            JobListHeader(district = district)
        }

        items(
            items = jobs,
            key = { job -> job.id }
        ) { job ->
            OptimizedJobItem(
                job = job,
                hasApplied = appliedJobsSet.contains(job.id),
                onJobSelected = onJobSelected
            )
        }

        if (isLoading) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Loading more jobs...",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun JobListHeader(district: String) {
    Column {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Available Jobs",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Jobs matching your location in $district",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun OptimizedJobItem(
    job: Job,
    hasApplied: Boolean,
    onJobSelected: (String) -> Unit
) {
    // Memoize job item content to prevent unnecessary recompositions
    val jobItemContent = remember(job.id, hasApplied) {
        JobItemData(
            id = job.id,
            title = job.title,
            location = job.location,
            salaryRange = job.salaryRange ?: "Salary not specified",
            description = job.description,
            hasApplied = hasApplied
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        onClick = { onJobSelected(jobItemContent.id) },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
                    text = jobItemContent.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                if (jobItemContent.hasApplied) {
                    AppliedIndicator()
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Location: ${jobItemContent.location}",
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Salary: ${jobItemContent.salaryRange}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = jobItemContent.description,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AppliedIndicator() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 8.dp)
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = "Applied",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "Applied",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

// Stable data class to prevent recompositions
private data class JobItemData(
    val id: String,
    val title: String,
    val location: String,
    val salaryRange: String,
    val description: String,
    val hasApplied: Boolean
)