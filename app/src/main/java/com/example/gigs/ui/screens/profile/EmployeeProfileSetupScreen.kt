package com.example.gigs.ui.screens.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.example.gigs.data.model.Gender
import com.example.gigs.data.model.IndianStates
import com.example.gigs.data.model.WorkPreference
import com.example.gigs.ui.components.GigWorkHeaderText
import com.example.gigs.ui.components.GigWorkPrimaryButton
import com.example.gigs.ui.components.GigWorkSubtitleText
import com.example.gigs.ui.components.GigWorkTextField
import com.example.gigs.viewmodel.ProfileState
import com.example.gigs.viewmodel.ProfileViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EmployeeProfileSetupScreen(
    profileViewModel: ProfileViewModel,
    onProfileCreated: (String) -> Unit
) {
    val profileState by profileViewModel.profileState.collectAsState()
    val selectedState by profileViewModel.selectedState.collectAsState()
    val selectedDistrict by profileViewModel.selectedDistrict.collectAsState()
    val profilePhotoUri by profileViewModel.profilePhotoUri.collectAsState()

    var name by remember { mutableStateOf("") }
    var nameError by remember { mutableStateOf<String?>(null) }
    var email by remember { mutableStateOf("") }

    // Date of Birth
    val datePickerState = rememberDatePickerState()
    var showDatePicker by remember { mutableStateOf(false) }
    var selectedDate by remember {
        mutableStateOf<LocalDate?>(null)
    }
    val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    val formattedDate = remember(selectedDate) {
        selectedDate?.format(formatter) ?: "Select Date of Birth"
    }

    // Gender selection
    var isGenderExpanded by remember { mutableStateOf(false) }
    var selectedGender by remember { mutableStateOf<Gender?>(null) }

    // Computer knowledge
    var isComputerKnowledgeExpanded by remember { mutableStateOf(false) }
    var hasComputerKnowledge by remember { mutableStateOf<Boolean?>(null) }

    // State and District
    var isStateExpanded by remember { mutableStateOf(false) }
    var isDistrictExpanded by remember { mutableStateOf(false) }
    val districts = remember(selectedState) {
        IndianStates.statesAndDistricts[selectedState] ?: emptyList()
    }

    // Work preferences
    val selectedWorkPreferences = remember { mutableStateListOf<WorkPreference>() }

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollState = rememberScrollState()

    // Image picker
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { profileViewModel.setProfilePhotoUri(it) }
    }

    val isFormValid by remember(
        nameError, name, selectedDate, selectedGender,
        hasComputerKnowledge, selectedState, selectedDistrict,
        profilePhotoUri, selectedWorkPreferences
    ) {
        derivedStateOf {
            nameError == null &&
                    name.isNotEmpty() &&
                    selectedDate != null &&
                    selectedGender != null &&
                    hasComputerKnowledge != null &&
                    selectedState.isNotEmpty() &&
                    selectedDistrict.isNotEmpty() &&
                    profilePhotoUri != null &&
                    selectedWorkPreferences.isNotEmpty()
        }
    }

    LaunchedEffect(profileState) {
        when (profileState) {
            is ProfileState.EmployeeProfileCreated -> {
                onProfileCreated(selectedDistrict)
            }
            is ProfileState.Error -> {
                val errorMessage = (profileState as ProfileState.Error).message
                snackbarHostState.showSnackbar(errorMessage)
            }
            else -> {}
        }
    }

    // Validate name field
    LaunchedEffect(name) {
        nameError = when {
            name.isBlank() -> "Name is required"
            name.length > 20 -> "Name cannot exceed 20 characters"
            else -> null
        }
    }
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Complete Your Profile") }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
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

                GigWorkHeaderText(text = "Set Up Your Profile")

                Spacer(modifier = Modifier.height(8.dp))

                GigWorkSubtitleText(text = "Complete your profile to find the perfect gig")

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "↓ Scroll down to see all fields ↓",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )

                Text(
                    text = "Personal Information",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                )

                // Profile photo
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .border(BorderStroke(2.dp, MaterialTheme.colorScheme.primary), CircleShape)
                        .clickable { imagePickerLauncher.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    if (profilePhotoUri != null) {
                        Image(
                            painter = rememberAsyncImagePainter(profilePhotoUri),
                            contentDescription = "Profile Photo",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.AddAPhoto,
                            contentDescription = "Add Profile Photo",
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Text(
                    text = "Profile Photo (Required)",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Name field
                GigWorkTextField(
                    value = name,
                    onValueChange = {
                        if (it.length <= 20) name = it
                    },
                    label = "Name (Required, max 20 characters)",
                    isError = nameError != null,
                    errorText = nameError ?: ""
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Date of Birth picker
                OutlinedTextField(
                    value = formattedDate,
                    onValueChange = { },
                    label = { Text("Date of Birth (Required)") },
                    readOnly = true,
                    trailingIcon = {
                        IconButton(onClick = { showDatePicker = true }) {
                            Icon(
                                imageVector = Icons.Default.CalendarMonth,
                                contentDescription = "Select Date"
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    isError = selectedDate == null
                )

                if (showDatePicker) {
                    DatePickerDialog(
                        onDismissRequest = { showDatePicker = false },
                        confirmButton = {
                            TextButton(onClick = {
                                datePickerState.selectedDateMillis?.let { millis ->
                                    selectedDate = Instant.ofEpochMilli(millis)
                                        .atZone(ZoneId.systemDefault())
                                        .toLocalDate()

                                    // If you need formatted string directly
                                    val formatted = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                                        .format(Date(millis))
                                }
                                showDatePicker = false
                            }) {
                                Text("OK")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDatePicker = false }) {
                                Text("Cancel")
                            }
                        }
                    ) {
                        DatePicker(state = datePickerState)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Gender dropdown
                ExposedDropdownMenuBox(
                    expanded = isGenderExpanded,
                    onExpandedChange = { isGenderExpanded = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = selectedGender?.name?.lowercase()?.capitalize() ?: "Select Gender",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Gender (Required)") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = isGenderExpanded)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        isError = selectedGender == null
                    )

                    ExposedDropdownMenu(
                        expanded = isGenderExpanded,
                        onDismissRequest = { isGenderExpanded = false }
                    ) {
                        Gender.values().forEach { gender ->
                            DropdownMenuItem(
                                text = { Text(gender.name.lowercase().capitalize()) },
                                onClick = {
                                    selectedGender = gender
                                    isGenderExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Computer Knowledge dropdown
                ExposedDropdownMenuBox(
                    expanded = isComputerKnowledgeExpanded,
                    onExpandedChange = { isComputerKnowledgeExpanded = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = when(hasComputerKnowledge) {
                            true -> "Yes"
                            false -> "No"
                            null -> "Select Computer Knowledge"
                        },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Computer Knowledge (Required)") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = isComputerKnowledgeExpanded)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        isError = hasComputerKnowledge == null
                    )

                    ExposedDropdownMenu(
                        expanded = isComputerKnowledgeExpanded,
                        onDismissRequest = { isComputerKnowledgeExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Yes") },
                            onClick = {
                                hasComputerKnowledge = true
                                isComputerKnowledgeExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("No") },
                            onClick = {
                                hasComputerKnowledge = false
                                isComputerKnowledgeExpanded = false
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Location",
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
                                    profileViewModel.setSelectedState(state)
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
                                        profileViewModel.setSelectedDistrict(district)
                                        isDistrictExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Email field (optional)
                GigWorkTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = "Email (Optional)"
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Work Preferences
                Text(
                    text = "Interested to Work In (Required)",
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                )

                val workPreferenceOptions = listOf(
                    WorkPreference.FULL_TIME to "Full Time",
                    WorkPreference.PART_TIME to "Part Time",
                    WorkPreference.WEEKDAY to "Weekdays",
                    WorkPreference.WEEKEND to "Weekends"
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp)
                ) {
                    workPreferenceOptions.forEach { (preference, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Checkbox(
                                checked = selectedWorkPreferences.contains(preference),
                                onCheckedChange = { checked ->
                                    if (checked) {
                                        selectedWorkPreferences.add(preference)
                                    } else {
                                        selectedWorkPreferences.remove(preference)
                                    }
                                }
                            )
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                // Show error if no work preference is selected
                if (profileState is ProfileState.Error && selectedWorkPreferences.isEmpty()) {
                    Text(
                        text = "Please select at least one work preference",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = 16.dp, top = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                if (!isFormValid) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Please complete all required fields:",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.error
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // List all missing fields
                            if (name.isEmpty() || nameError != null) {
                                Text(
                                    text = "• Name: ${nameError ?: "Required"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }

                            if (selectedDate == null) {
                                Text(
                                    text = "• Date of Birth: Required",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }

                            if (selectedGender == null) {
                                Text(
                                    text = "• Gender: Required",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }

                            if (hasComputerKnowledge == null) {
                                Text(
                                    text = "• Computer Knowledge: Required",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }

                            if (selectedState.isEmpty()) {
                                Text(
                                    text = "• State: Required",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }

                            if (selectedState.isNotEmpty() && selectedDistrict.isEmpty()) {
                                Text(
                                    text = "• District: Required",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }

                            if (profilePhotoUri == null) {
                                Text(
                                    text = "• Profile Photo: Required",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }

                            if (selectedWorkPreferences.isEmpty()) {
                                Text(
                                    text = "• Work Preferences: Select at least one option",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Submit button
                GigWorkPrimaryButton(
                    text = "Complete Profile",
                    onClick = {
                        val photoInputStream = profilePhotoUri?.let { uri ->
                            context.contentResolver.openInputStream(uri)
                        }

                        coroutineScope.launch {
                            if (selectedDate != null &&
                                selectedGender != null &&
                                hasComputerKnowledge != null
                            ) {
                                profileViewModel.createEmployeeProfile(
                                    name = name,
                                    dob = selectedDate!!,
                                    gender = selectedGender!!,
                                    hasComputerKnowledge = hasComputerKnowledge!!,
                                    state = selectedState,
                                    district = selectedDistrict,
                                    email = if (email.isBlank()) null else email,
                                    profilePhotoInputStream = photoInputStream,
                                    workPreferences = selectedWorkPreferences.toList()
                                )
                            } else {
                                snackbarHostState.showSnackbar("Please fill all required fields")
                            }
                        }
                    },
                    enabled = isFormValid,
                    isLoading = profileState is ProfileState.Loading
                )

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Loading overlay
            if (profileState is ProfileState.Loading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f))
                        .clickable(enabled = false) { /* Prevent clicks through overlay */ },
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier
                            .padding(16.dp)
                            .size(200.dp),
                        elevation = CardDefaults.cardElevation(8.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(60.dp),
                                strokeWidth = 5.dp
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = "Creating profile...",
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "Please wait",
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

// Extension function to capitalize first letter
private fun String.capitalize() = this.replaceFirstChar {
    if (it.isLowerCase()) it.titlecase() else it.toString()
}