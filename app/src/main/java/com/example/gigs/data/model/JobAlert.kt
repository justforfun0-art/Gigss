package com.example.gigs.data.model

import kotlinx.serialization.Serializable

/**
 * Data class representing a job alert/notification preference
 */
@Serializable
data class JobAlert(
    val id: String = "",
    val userId: String = "",
    val district: String = "",
    val workTypes: List<WorkPreference> = emptyList(),
    val keywords: List<String> = emptyList(),
    val minWage: Double? = null,
    val maxWage: Double? = null,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)