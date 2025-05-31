package com.example.gigs.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.gigs.data.model.JobAlert
import com.example.gigs.data.model.WorkPreference

@Composable
fun JobAlertDialog(
    currentDistrict: String,
    onDismiss: () -> Unit,
    onCreateAlert: (JobAlert) -> Unit
) {
    var selectedDistrict by remember { mutableStateOf(currentDistrict) }
    var selectedWorkTypes by remember { mutableStateOf(setOf<WorkPreference>()) }
    var keywords by remember { mutableStateOf("") }
    var minWage by remember { mutableStateOf("") }
    var maxWage by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Create Job Alert",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // District selection
                OutlinedTextField(
                    value = selectedDistrict,
                    onValueChange = { selectedDistrict = it },
                    label = { Text("District") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Work type selection
                Column {
                    Text(
                        text = "Work Types",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    WorkPreference.entries.forEach { workType ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = selectedWorkTypes.contains(workType),
                                onCheckedChange = { checked ->
                                    selectedWorkTypes = if (checked) {
                                        selectedWorkTypes + workType
                                    } else {
                                        selectedWorkTypes - workType
                                    }
                                }
                            )
                            Text(
                                text = workType.toString().replace("_", " "),
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }

                // Keywords
                OutlinedTextField(
                    value = keywords,
                    onValueChange = { keywords = it },
                    label = { Text("Keywords (comma separated)") },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("e.g., delivery, driver, cleaner") }
                )

                // Wage range
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = minWage,
                        onValueChange = { minWage = it },
                        label = { Text("Min Wage") },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("₹") }
                    )

                    OutlinedTextField(
                        value = maxWage,
                        onValueChange = { maxWage = it },
                        label = { Text("Max Wage") },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("₹") }
                    )
                }

                // Info text
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Text(
                        text = "You'll be notified when new jobs matching your criteria are posted.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val alert = JobAlert(
                        district = selectedDistrict,
                        workTypes = selectedWorkTypes.toList(),
                        keywords = keywords.split(",")
                            .map { it.trim() }
                            .filter { it.isNotBlank() },
                        minWage = minWage.toDoubleOrNull(),
                        maxWage = maxWage.toDoubleOrNull()
                    )
                    onCreateAlert(alert)
                },
                enabled = selectedDistrict.isNotBlank()
            ) {
                Text("Create Alert")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}