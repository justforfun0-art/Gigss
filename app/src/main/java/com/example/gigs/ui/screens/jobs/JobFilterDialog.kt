package com.example.gigs.ui.screens.jobs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.gigs.data.model.WorkPreference
import java.text.NumberFormat
import java.util.*

// First, let's update JobFilters to include new parameters
data class JobFilters(
    val jobType: WorkPreference? = null,
    val location: String = "",
    val minSalary: Int = 0,
    val maxSalary: Int = 100000,  // Updated to include max salary
    val categories: List<String> = emptyList(),
    val skills: List<String> = emptyList(),
    val datePosted: DatePostedFilter = DatePostedFilter.ANY // New field
)

enum class DatePostedFilter {
    ANY, TODAY, LAST_WEEK, LAST_MONTH
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobFilterDialog(
    currentFilters: JobFilters,
    onDismiss: () -> Unit,
    onApplyFilters: (JobFilters) -> Unit
) {
    var jobType by remember { mutableStateOf(currentFilters.jobType) }
    var location by remember { mutableStateOf(currentFilters.location) }
    var selectedCategories by remember { mutableStateOf(currentFilters.categories.toMutableList()) }
    var datePosted by remember { mutableStateOf(currentFilters.datePosted) }

    // Salary range slider state
    var salaryRange by remember {
        mutableStateOf(currentFilters.minSalary.toFloat()..currentFilters.maxSalary.toFloat())
    }

    // Format salary numbers
    val currencyFormatter = remember { NumberFormat.getCurrencyInstance(Locale.getDefault()) }

    // Job categories - you can expand this list based on your app's needs
    val availableCategories = remember {
        listOf(
            "Technology", "Healthcare", "Education", "Finance", "Retail",
            "Hospitality", "Construction", "Manufacturing", "Transportation",
            "Entertainment", "Real Estate", "Other"
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Filter Jobs",
                    style = MaterialTheme.typography.headlineSmall
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Job Type filter
                Text(
                    text = "Job Type",
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(modifier = Modifier.height(8.dp))

                Column(
                    modifier = Modifier.selectableGroup()
                ) {
                    WorkPreference.values().forEach { preference ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = jobType == preference,
                                    onClick = { jobType = preference },
                                    role = Role.RadioButton
                                )
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = jobType == preference,
                                onClick = null
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            Text(
                                text = preference.toString()
                                    .replace("_", " ")
                                    .lowercase()
                                    .capitalize(),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }

                    // Option to clear job type filter
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = jobType == null,
                                onClick = { jobType = null },
                                role = Role.RadioButton
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = jobType == null,
                            onClick = null
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Text(
                            text = "Any type",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }

                Divider(modifier = Modifier.padding(vertical = 16.dp))

                // Location filter
                Text(
                    text = "Location",
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("Enter location") },
                    modifier = Modifier.fillMaxWidth()
                )

                Divider(modifier = Modifier.padding(vertical = 16.dp))

                // Salary Range slider
                Text(
                    text = "Salary Range",
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(modifier = Modifier.height(8.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Display the selected salary range
                    Text(
                        text = "${currencyFormatter.format(salaryRange.start.toInt())} - ${currencyFormatter.format(salaryRange.endInclusive.toInt())}",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Salary range slider
                    RangeSlider(
                        value = salaryRange,
                        onValueChange = { salaryRange = it },
                        valueRange = 0f..150000f,
                        steps = 29,  // Gives steps of 5000
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Labels for the slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = currencyFormatter.format(0),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = currencyFormatter.format(150000),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Divider(modifier = Modifier.padding(vertical = 16.dp))

                // Date posted filter
                Text(
                    text = "Date Posted",
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(modifier = Modifier.height(8.dp))

                Column(
                    modifier = Modifier.selectableGroup()
                ) {
                    DatePostedFilter.values().forEach { filter ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = datePosted == filter,
                                    onClick = { datePosted = filter },
                                    role = Role.RadioButton
                                )
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = datePosted == filter,
                                onClick = null
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            Text(
                                text = when(filter) {
                                    DatePostedFilter.ANY -> "Any time"
                                    DatePostedFilter.TODAY -> "Today"
                                    DatePostedFilter.LAST_WEEK -> "Past week"
                                    DatePostedFilter.LAST_MONTH -> "Past month"
                                },
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }

                Divider(modifier = Modifier.padding(vertical = 16.dp))

                // Job categories
                Text(
                    text = "Job Categories",
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(modifier = Modifier.height(8.dp))

                Column {
                    availableCategories.forEach { category ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = selectedCategories.contains(category),
                                onCheckedChange = { checked ->
                                    if (checked) {
                                        selectedCategories.add(category)
                                    } else {
                                        selectedCategories.remove(category)
                                    }
                                }
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            Text(
                                text = category,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(
                        onClick = {
                            // Reset all filters to defaults
                            jobType = null
                            location = ""
                            salaryRange = 0f..100000f
                            selectedCategories.clear()
                            datePosted = DatePostedFilter.ANY
                        }
                    ) {
                        Text("Reset")
                    }

                    Row {
                        TextButton(onClick = onDismiss) {
                            Text("Cancel")
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = {
                                onApplyFilters(
                                    JobFilters(
                                        jobType = jobType,
                                        location = location,
                                        minSalary = salaryRange.start.toInt(),
                                        maxSalary = salaryRange.endInclusive.toInt(),
                                        categories = selectedCategories,
                                        datePosted = datePosted
                                    )
                                )
                            }
                        ) {
                            Text("Apply Filters")
                        }
                    }
                }
            }
        }
    }
}

// Helper extension function
private fun String.capitalize(): String {
    return this.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
}