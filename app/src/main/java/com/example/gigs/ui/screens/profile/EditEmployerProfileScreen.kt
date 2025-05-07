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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.gigs.viewmodel.ProfileState
import com.example.gigs.viewmodel.ProfileViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditEmployerProfileScreen(
    profileViewModel: ProfileViewModel = hiltViewModel(),
    onProfileUpdated: () -> Unit,
    onBackPressed: () -> Unit
) {
    // Get existing employer profile
    val employerProfile by profileViewModel.employerProfile.collectAsState()
    val profileState by profileViewModel.profileState.collectAsState()
    val selectedState by profileViewModel.selectedState.collectAsState()
    val selectedDistrict by profileViewModel.selectedDistrict.collectAsState()

    // Form state variables
    var companyName by remember { mutableStateOf("") }
    var industry by remember { mutableStateOf("") }
    var companySize by remember { mutableStateOf("") }
    var website by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    val scrollState = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Initialize form fields with existing profile data
    LaunchedEffect(employerProfile) {
        employerProfile?.let { profile ->
            companyName = profile.companyName
            industry = profile.industry
            companySize = profile.companySize
            website = profile.website ?: ""
            description = profile.description ?: ""

            // Set selected state and district
            profileViewModel.setSelectedState(profile.state)
            profileViewModel.setSelectedDistrict(profile.district)
        }
    }

    // Handle profile update result
    LaunchedEffect(profileState) {
        when (profileState) {
            is ProfileState.EmployerProfileCreated -> {
                scope.launch {
                    snackbarHostState.showSnackbar("Profile updated successfully")
                }
                onProfileUpdated()
            }
            is ProfileState.Error -> {
                scope.launch {
                    snackbarHostState.showSnackbar((profileState as ProfileState.Error).message)
                }
            }
            else -> {}
        }
    }

    // Load employer profile data when screen is first shown
    LaunchedEffect(Unit) {
        profileViewModel.getEmployerProfile()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Company Profile") },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (employerProfile == null) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .verticalScroll(scrollState),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Top
                ) {
                    Spacer(modifier = Modifier.height(16.dp))

                    // Company Name
                    OutlinedTextField(
                        value = companyName,
                        onValueChange = { companyName = it },
                        label = { Text("Company Name") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Industry
                    OutlinedTextField(
                        value = industry,
                        onValueChange = { industry = it },
                        label = { Text("Industry") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Company Size
                    OutlinedTextField(
                        value = companySize,
                        onValueChange = { companySize = it },
                        label = { Text("Company Size") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Location selection
                    CompanyLocationSection(
                        selectedState = selectedState,
                        onStateSelected = { profileViewModel.setSelectedState(it) },
                        selectedDistrict = selectedDistrict,
                        onDistrictSelected = { profileViewModel.setSelectedDistrict(it) }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Website
                    OutlinedTextField(
                        value = website,
                        onValueChange = { website = it },
                        label = { Text("Website (optional)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Description
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

                    // Save button
                    Button(
                        onClick = {
                            profileViewModel.createEmployerProfile(
                                companyName = companyName,
                                industry = industry,
                                companySize = companySize,
                                state = selectedState,
                                district = selectedDistrict,
                                website = if (website.isBlank()) null else website,
                                description = description
                            )
                        },
                        enabled = companyName.isNotEmpty() &&
                                industry.isNotEmpty() &&
                                selectedState.isNotEmpty() &&
                                selectedDistrict.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Save Changes")
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}