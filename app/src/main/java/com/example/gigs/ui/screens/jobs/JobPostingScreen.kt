package com.example.gigs.ui.screens.jobs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.gigs.data.model.IndianStates
import com.example.gigs.data.model.Job
import com.example.gigs.data.model.JobStatus
import com.example.gigs.data.model.WorkType
import com.example.gigs.ui.components.GigWorkHeaderText
import com.example.gigs.ui.components.GigWorkPrimaryButton
import com.example.gigs.ui.components.GigWorkSubtitleText
import com.example.gigs.ui.components.GigWorkTextField
import com.example.gigs.viewmodel.JobPostingViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobPostingScreen(
    viewModel: JobPostingViewModel = hiltViewModel(),
    onJobCreated: () -> Unit,
    onBackPressed: () -> Unit
) {
    // Get current user ID
    val currentUserId by remember { derivedStateOf { viewModel.getCurrentUserId() } }

    val jobState by viewModel.jobState.collectAsState()
    val scrollState = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Form state
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var salaryFrom by remember { mutableStateOf("") }
    var salaryTo by remember { mutableStateOf("") }
    var selectedWorkType by remember { mutableStateOf<WorkType?>(null) }
    var skillsRequired by remember { mutableStateOf("") }
    var preferredSkills by remember { mutableStateOf("") }
    var isRemote by remember { mutableStateOf(false) }
    var showSuccessDialog by remember { mutableStateOf(false) }

    // Location selection
    var selectedState by remember { mutableStateOf("") }
    var selectedDistrict by remember { mutableStateOf("") }
    var isStateExpanded by remember { mutableStateOf(false) }
    var isDistrictExpanded by remember { mutableStateOf(false) }
    val districts = remember(selectedState) {
        IndianStates.statesAndDistricts[selectedState] ?: emptyList()
    }

    // Work duration options
    var workDuration by remember { mutableStateOf("") }
    var isWorkDurationExpanded by remember { mutableStateOf(false) }
    val workDurationOptions = listOf(
        "Less than 1 week",
        "1-2 weeks",
        "2-4 weeks",
        "1-3 months",
        "3-6 months",
        "More than 6 months",
        "Ongoing"
    )

    // Handle job creation success
    LaunchedEffect(jobState) {
        when (jobState) {
            is JobPostingViewModel.JobState.Success -> {
                showSuccessDialog = true
            }
            is JobPostingViewModel.JobState.Error -> {
                val errorMessage = (jobState as JobPostingViewModel.JobState.Error).message
                snackbarHostState.showSnackbar(errorMessage)
            }
            else -> {}
        }
    }

    // Success dialog
    if (showSuccessDialog) {
        AlertDialog(
            onDismissRequest = {
                showSuccessDialog = false
                onJobCreated()
            },
            title = { Text("Job Posted Successfully") },
            text = { Text("Your job has been submitted for admin approval. You will be notified once it's approved.") },
            confirmButton = {
                Button(
                    onClick = {
                        showSuccessDialog = false
                        onJobCreated()
                    }
                ) {
                    Text("OK")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Post a New Job") },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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
                .padding(16.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            GigWorkHeaderText(text = "Create a New Job")

            Spacer(modifier = Modifier.height(8.dp))

            GigWorkSubtitleText(text = "Fill in the details to post a job")

            Spacer(modifier = Modifier.height(24.dp))

            // Job Title
            GigWorkTextField(
                value = title,
                onValueChange = { title = it },
                label = "Job Title",
                isRequired = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Job Description
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Job Description") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                maxLines = 5
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Location selection
            Text(
                text = "Job Location",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            )

            // State dropdown
            ExposedDropdownMenuBox(
                expanded = isStateExpanded,
                onExpandedChange = { isStateExpanded = it },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = selectedState.ifEmpty { "Select State" },
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("State (Required)") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = isStateExpanded)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    isError = selectedState.isEmpty()
                )

                ExposedDropdownMenu(
                    expanded = isStateExpanded,
                    onDismissRequest = { isStateExpanded = false }
                ) {
                    IndianStates.statesAndDistricts.keys.forEach { state ->
                        DropdownMenuItem(
                            text = { Text(state) },
                            onClick = {
                                selectedState = state
                                selectedDistrict = "" // Reset district when state changes
                                isStateExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // District dropdown
            ExposedDropdownMenuBox(
                expanded = isDistrictExpanded && selectedState.isNotEmpty(),
                onExpandedChange = {
                    if (selectedState.isNotEmpty()) {
                        isDistrictExpanded = it
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = selectedDistrict.ifEmpty { "Select District" },
                    onValueChange = {},
                    readOnly = true,
                    enabled = selectedState.isNotEmpty(),
                    label = { Text("District (Required)") },
                    trailingIcon = {
                        if (selectedState.isNotEmpty()) {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = isDistrictExpanded)
                        } else {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Disabled",
                                tint = Color.Gray
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    isError = selectedState.isNotEmpty() && selectedDistrict.isEmpty()
                )

                if (selectedState.isNotEmpty()) {
                    ExposedDropdownMenu(
                        expanded = isDistrictExpanded,
                        onDismissRequest = { isDistrictExpanded = false }
                    ) {
                        districts.forEach { district ->
                            DropdownMenuItem(
                                text = { Text(district) },
                                onClick = {
                                    selectedDistrict = district
                                    isDistrictExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Remote work option
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = isRemote,
                    onCheckedChange = { isRemote = it }
                )
                Text("This job can be done remotely")
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Work Type selection
            Text(
                text = "Work Type",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectableGroup()
            ) {
                WorkType.values().forEach { workType ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = selectedWorkType == workType,
                                onClick = { selectedWorkType = workType }
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedWorkType == workType,
                            onClick = null // null because we're handling the click through the Row
                        )
                        Text(
                            text = workType.toString().replace("_", " "),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Work Duration dropdown
            ExposedDropdownMenuBox(
                expanded = isWorkDurationExpanded,
                onExpandedChange = { isWorkDurationExpanded = it },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = workDuration.ifEmpty { "Select Work Duration" },
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Work Duration (Required)") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = isWorkDurationExpanded)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    isError = workDuration.isEmpty()
                )

                ExposedDropdownMenu(
                    expanded = isWorkDurationExpanded,
                    onDismissRequest = { isWorkDurationExpanded = false }
                ) {
                    workDurationOptions.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            onClick = {
                                workDuration = option
                                isWorkDurationExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Salary Range
            Text(
                text = "Salary Range (₹)",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = salaryFrom,
                    onValueChange = { salaryFrom = it },
                    label = { Text("From") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )

                OutlinedTextField(
                    value = salaryTo,
                    onValueChange = { salaryTo = it },
                    label = { Text("To") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Skills
            OutlinedTextField(
                value = skillsRequired,
                onValueChange = { skillsRequired = it },
                label = { Text("Required Skills (comma separated)") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = preferredSkills,
                onValueChange = { preferredSkills = it },
                label = { Text("Preferred Skills (comma separated)") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Submit button
            GigWorkPrimaryButton(
                text = "Submit for Approval",
                onClick = {
                    // Create new job
                    val newJob = Job(
                        id = "",  // Will be generated by the database
                        employerId = currentUserId ?: "", // Use the retrieved user ID
                        title = title,
                        description = description,
                        location = "$selectedDistrict, $selectedState",
                        salaryRange = "₹$salaryFrom - ₹$salaryTo",
                        workType = selectedWorkType ?: WorkType.PART_TIME,
                        isRemote = isRemote,
                        status = JobStatus.PENDING_APPROVAL, // Set to pending approval
                        skillsRequired = skillsRequired.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                        preferredSkills = preferredSkills.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                        workDuration = workDuration,
                        district = selectedDistrict,
                        state = selectedState,
                        createdAt = null, // Will be set by the repository
                        updatedAt = null  // Will be set by the repository
                    )

                    viewModel.createJob(newJob)
                },
                enabled = title.isNotEmpty() &&
                        description.isNotEmpty() &&
                        selectedState.isNotEmpty() &&
                        selectedDistrict.isNotEmpty() &&
                        workDuration.isNotEmpty() &&
                        selectedWorkType != null &&
                        salaryFrom.isNotEmpty() &&
                        salaryTo.isNotEmpty(),
                isLoading = jobState is JobPostingViewModel.JobState.Loading
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}