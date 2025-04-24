package com.example.gigs.data.model

import kotlinx.datetime.LocalDate

// JobCreationData.kt
data class JobCreationData(
    val title: String,
    val description: String,
    val location: String,
    val salaryRange: String,
    val jobType: WorkPreference,
    val skillsRequired: List<String>,
    val requirements: List<String>,
    val applicationDeadline: LocalDate?,
    val tags: List<String>,
    val jobCategory: String
)