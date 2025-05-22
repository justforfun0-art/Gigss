package com.example.gigs.ui.screens.jobs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.layout.Column
import com.example.gigs.ui.theme.Divider
import com.example.gigs.viewmodel.SortOption


@Composable
fun JobSortFilterDialog(
    currentSortOption: com.example.gigs.viewmodel.SortOption,
    currentFilters: JobFilters,
    onDismiss: () -> Unit,
    onApplyFilters: (SortOption, JobFilters) -> Unit
) {
    var selectedSortOption by remember { mutableStateOf(currentSortOption) }
    var filters by remember { mutableStateOf(currentFilters) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text("Sort & Filter", style = MaterialTheme.typography.titleLarge)

                Spacer(modifier = Modifier.height(16.dp))

                // Sort options section
                Text("Sort by", style = MaterialTheme.typography.titleMedium)

                // Radio buttons for sort options
                Column(modifier = Modifier.selectableGroup()) {
                    SortOption.values().forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = selectedSortOption == option,
                                    onClick = { selectedSortOption = option }
                                )
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedSortOption == option,
                                onClick = null
                            )
                            Text(
                                text = when(option) {
                                    SortOption.DATE_NEWEST -> "Date (newest first)"
                                    SortOption.DATE_OLDEST -> "Date (oldest first)"
                                    SortOption.SALARY_HIGH_LOW -> "Salary (high to low)"
                                    SortOption.SALARY_LOW_HIGH -> "Salary (low to high)"
                                    SortOption.ALPHABETICAL -> "Title (A-Z)"
                                },
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }

                Divider(modifier = Modifier.padding(vertical = 16.dp))

                // Filter options
                // (add your filter UI components here)

                Spacer(modifier = Modifier.height(24.dp))

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(onClick = {
                        onApplyFilters(selectedSortOption, filters)
                        onDismiss()
                    }) {
                        Text("Apply")
                    }
                }
            }
        }
    }
}