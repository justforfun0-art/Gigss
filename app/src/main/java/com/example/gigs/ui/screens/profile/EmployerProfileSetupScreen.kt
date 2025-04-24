package com.example.gigs.ui.screens.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.gigs.data.model.IndianStates
import com.example.gigs.ui.components.GigWorkHeaderText
import com.example.gigs.ui.components.GigWorkPrimaryButton
import com.example.gigs.ui.components.GigWorkSubtitleText
import com.example.gigs.ui.components.GigWorkTextField
import com.example.gigs.viewmodel.ProfileState
import com.example.gigs.viewmodel.ProfileViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmployerProfileSetupScreen(
    profileViewModel: ProfileViewModel,
    onProfileCreated: () -> Unit
) {
    val profileState by profileViewModel.profileState.collectAsState()

    val selectedState by profileViewModel.selectedState.collectAsState()
    val selectedDistrict by profileViewModel.selectedDistrict.collectAsState()

    var companyName by remember { mutableStateOf("") }
    var industry by remember { mutableStateOf("") }
    var companySize by remember { mutableStateOf("") }
    var website by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    val snackbarHostState = remember { SnackbarHostState() }
    val scrollState = rememberScrollState()

    // Industry dropdown
    var isIndustryExpanded by remember { mutableStateOf(false) }
    val industryOptions = listOf(
        "Technology", "Healthcare", "Education", "Finance", "Retail",
        "Hospitality", "Construction", "Manufacturing", "Transportation",
        "Entertainment", "Real Estate", "Other"
    )

    // Company size dropdown
    var isSizeExpanded by remember { mutableStateOf(false) }
    val sizeOptions = listOf(
        "1-10 employees", "11-50 employees", "51-200 employees",
        "201-500 employees", "501-1000 employees", "1000+ employees"
    )

    LaunchedEffect(profileState) {
        when (profileState) {
            is ProfileState.EmployerProfileCreated -> {
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
                title = { Text("Complete Your Profile") }
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
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Box(
                modifier = Modifier.fillMaxWidth()
            ) {
                LinearProgressIndicator(
                    progress = 0.7f,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            GigWorkHeaderText(text = "Almost there!")

            Spacer(modifier = Modifier.height(8.dp))

            GigWorkSubtitleText(text = "Let's finish setting up your employer profile")

            Spacer(modifier = Modifier.height(24.dp))

            GigWorkTextField(
                value = companyName,
                onValueChange = { companyName = it },
                label = "Company Name"
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Industry dropdown
            ExposedDropdownMenuBox(
                expanded = isIndustryExpanded,
                onExpandedChange = { isIndustryExpanded = it },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = industry,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Industry") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = isIndustryExpanded)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )

                ExposedDropdownMenu(
                    expanded = isIndustryExpanded,
                    onDismissRequest = { isIndustryExpanded = false }
                ) {
                    industryOptions.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            onClick = {
                                industry = option
                                isIndustryExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Company size dropdown
            ExposedDropdownMenuBox(
                expanded = isSizeExpanded,
                onExpandedChange = { isSizeExpanded = it },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = companySize,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Company Size") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = isSizeExpanded)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )

                ExposedDropdownMenu(
                    expanded = isSizeExpanded,
                    onDismissRequest = { isSizeExpanded = false }
                ) {
                    sizeOptions.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            onClick = {
                                companySize = option
                                isSizeExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
/*
            GigWorkTextField(
                value = companyLocation,
                onValueChange = { companyLocation = it },
                label = "Company Location"
            )

 */

            CompanyLocationSection(
                selectedState = selectedState,
                onStateSelected = { profileViewModel.setSelectedState(it) },
                selectedDistrict = selectedDistrict,
                onDistrictSelected = { profileViewModel.setSelectedDistrict(it) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            GigWorkTextField(
                value = website,
                onValueChange = { website = it },
                label = "Website (optional)"
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Company Description") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                maxLines = 5
            )

            Spacer(modifier = Modifier.height(32.dp))

            GigWorkPrimaryButton(
                text = "Complete Profile",
                onClick = {
                    profileViewModel.createEmployerProfile(
                        companyName = companyName,
                        industry = industry,
                        companySize = companySize,
                        state = selectedState,         // Use selectedState instead of companyLocation
                        district = selectedDistrict,   // Add selectedDistrict
                        website = if (website.isBlank()) null else website,
                        description = description
                    )
                },
                enabled = companyName.isNotEmpty() &&
                        industry.isNotEmpty() &&
                        selectedState.isNotEmpty() &&
                        selectedDistrict.isNotEmpty(),
                isLoading = profileState is ProfileState.Loading
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompanyLocationSection(
    selectedState: String,
    onStateSelected: (String) -> Unit,
    selectedDistrict: String,
    onDistrictSelected: (String) -> Unit
) {
    var isStateExpanded by remember { mutableStateOf(false) }
    var isDistrictExpanded by remember { mutableStateOf(false) }

    val districts = remember(selectedState) {
        IndianStates.statesAndDistricts[selectedState] ?: emptyList()
    }

    Text(
        text = "Company Location",
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
                        onStateSelected(state)
                        isStateExpanded = false
                    }
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    // District dropdown (enabled only if state is selected)
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
                            onDistrictSelected(district)
                            isDistrictExpanded = false
                        }
                    )
                }
            }
        }
    }
}