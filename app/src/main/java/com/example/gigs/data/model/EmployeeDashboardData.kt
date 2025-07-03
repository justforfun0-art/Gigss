package com.example.gigs.data.model

import kotlinx.serialization.Serializable

data class EmployeeDashboardData(
    val userId: String,
    val totalApplications: Int = 0,
    val hiredCount: Int = 0,
    val rejectedCount: Int = 0,
    val averageRating: Float = 0f,
    val reviewCount: Int = 0
)

data class EmployerDashboardData(
    val userId: String,
    val totalJobs: Int = 0,
    val totalApplicationsReceived: Int = 0,
    val activeJobs: Int = 0,
    val averageRating: Float = 0f,
    val reviewCount: Int = 0
)

@Serializable
data class LocationStat(
    val location: String,
    val applicationCount: Int
)

@Serializable
data class CategoryStat(
    val category: String,
    val applicationCount: Int
)


@Serializable
data class Activity(
    val activityType: String,
    val activityId: String,
    val relatedId: String?,
    val userId: String,
    val targetUserId: String?,
    val title: String,
    val action: String,
    val activityTime: String,

    // Transient fields for UI
    @Transient val userName: String = "",
    @Transient val targetUserName: String = ""
)