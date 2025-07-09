package com.example.gigs.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

@Serializable
enum class JobStatus {
    PENDING_APPROVAL,
    APPROVED,
    REJECTED,
    CLOSED
}

@Serializable
enum class WorkType {
    FULL_TIME,
    PART_TIME,
    CONTRACT,
    TEMPORARY,
    INTERNSHIP,
    FREELANCE,
    OTHER
}

// 🚀 UPDATED Job.kt - Complete ApplicationStatus enum
@Serializable
enum class ApplicationStatus {
    APPLIED,              // Employee applied for the job
    SELECTED,             // Employer selected employee for the job
    ACCEPTED,             // Employee accepted the job offer (committed to show up)
    WORK_IN_PROGRESS,     // Employee started work
    COMPLETION_PENDING,   // 🚀 NEW: Employee initiated completion, waiting for employer verification
    COMPLETED,            // Work successfully completed
    REJECTED,             // Employer rejected the application
    NOT_INTERESTED,       // User marked as not interested (handled separately from main flow)
    DECLINED              // Employee declined after being selected
}

/**
 * 🚀 HELPER: Extension function to check if status represents rejection
 */
fun ApplicationStatus.isRejected(): Boolean {
    return this in listOf(ApplicationStatus.REJECTED, ApplicationStatus.DECLINED, ApplicationStatus.NOT_INTERESTED)
}

/**
 * 🚀 HELPER: Extension function to check if status represents active application
 */
fun ApplicationStatus.isActive(): Boolean {
    return this in listOf(
        ApplicationStatus.APPLIED,
        ApplicationStatus.SELECTED,
        ApplicationStatus.ACCEPTED,
        ApplicationStatus.COMPLETION_PENDING,
        ApplicationStatus.WORK_IN_PROGRESS
    )
}

/**
 * Check if this is an active work status
 */
fun ApplicationStatus.isActiveWork(): Boolean {
    return this in listOf(ApplicationStatus.SELECTED, ApplicationStatus.ACCEPTED, ApplicationStatus.WORK_IN_PROGRESS,ApplicationStatus.COMPLETION_PENDING)
}

/**
 * Check if job is completed
 */
fun ApplicationStatus.isWorkCompleted(): Boolean {
    return this == ApplicationStatus.COMPLETED
}

/**
 * Add these extension functions for better status handling
 */
fun ApplicationStatus.isActiveJob(): Boolean {
    return this in listOf(
        ApplicationStatus.SELECTED,
        ApplicationStatus.WORK_IN_PROGRESS
    )
}



fun ApplicationStatus.canStartWork(): Boolean {
    return this == ApplicationStatus.SELECTED
}

fun ApplicationStatus.isWorkInProgress(): Boolean {
    return this == ApplicationStatus.WORK_IN_PROGRESS
}

/**
 * 🚀 NEW: Check if status should be included in the main application flow stepper
 */
fun ApplicationStatus.shouldShowInStepper(): Boolean {
    return this != ApplicationStatus.NOT_INTERESTED
}

/**
 * 🚀 HELPER: Get display text for status
 */
fun ApplicationStatus.getDisplayText(): String {
    return when (this) {
        ApplicationStatus.APPLIED -> "Applied"
        ApplicationStatus.SELECTED -> "Selected"
        ApplicationStatus.ACCEPTED -> "Accepted"
        ApplicationStatus.WORK_IN_PROGRESS -> "Work In Progress"
        ApplicationStatus.COMPLETED -> "Completed"
        ApplicationStatus.REJECTED -> "Rejected"
        ApplicationStatus.NOT_INTERESTED -> "Not Interested"
        ApplicationStatus.COMPLETION_PENDING -> "Completion Pending"  // 🚀 Added
        ApplicationStatus.DECLINED -> "Declined"
    }
}

/**
 * Get next possible statuses from current status
 */
fun ApplicationStatus.getNextPossibleStatuses(): List<ApplicationStatus> {
    return when (this) {
        ApplicationStatus.APPLIED -> listOf(ApplicationStatus.SELECTED, ApplicationStatus.REJECTED, ApplicationStatus.NOT_INTERESTED)
        ApplicationStatus.SELECTED -> listOf(ApplicationStatus.ACCEPTED, ApplicationStatus.DECLINED)
        ApplicationStatus.ACCEPTED -> listOf(ApplicationStatus.WORK_IN_PROGRESS, ApplicationStatus.DECLINED)
        ApplicationStatus.COMPLETION_PENDING -> listOf(ApplicationStatus.COMPLETED)  // 🚀 Added
        ApplicationStatus.WORK_IN_PROGRESS -> listOf(ApplicationStatus.COMPLETED)
        ApplicationStatus.COMPLETED -> emptyList() // Terminal status
        ApplicationStatus.REJECTED -> emptyList() // Terminal status
        ApplicationStatus.NOT_INTERESTED -> emptyList() // Terminal status
        ApplicationStatus.DECLINED -> emptyList() // Terminal status
    }
}

/**
 * 🚀 HELPER: Get status color for UI
 */
fun ApplicationStatus.getStatusColor(): androidx.compose.ui.graphics.Color {
    return when (this) {
        ApplicationStatus.APPLIED -> androidx.compose.ui.graphics.Color(0xFF2196F3)
        ApplicationStatus.SELECTED -> androidx.compose.ui.graphics.Color(0xFF00BCD4)
        ApplicationStatus.ACCEPTED -> androidx.compose.ui.graphics.Color(0xFF4CAF50)
        ApplicationStatus.WORK_IN_PROGRESS -> androidx.compose.ui.graphics.Color(0xFF009688)
        ApplicationStatus.COMPLETED -> androidx.compose.ui.graphics.Color(0xFF8BC34A)
        ApplicationStatus.REJECTED, ApplicationStatus.DECLINED, ApplicationStatus.NOT_INTERESTED -> androidx.compose.ui.graphics.Color(0xFFF44336)
        else -> androidx.compose.ui.graphics.Color(0xFF757575) // Grey for any unknown status
    }
}

/**
 * You might also want to add a function to get status background color with alpha
 */
fun ApplicationStatus.getStatusBackgroundColor(): androidx.compose.ui.graphics.Color {
    return getStatusColor().copy(alpha = 0.1f)
}

/**
 * And a function to determine if the status should have emphasis (for important statuses)
 */
fun ApplicationStatus.shouldEmphasize(): Boolean {
    return this in listOf(
        ApplicationStatus.SELECTED,        // Needs action to accept/decline
        ApplicationStatus.ACCEPTED,        // Needs OTP to start
        ApplicationStatus.WORK_IN_PROGRESS // Currently working
    )
}

/**
 * Get user-friendly display text with job context
 */
fun ApplicationStatus.getDisplayTextWithJob(jobName: String): String {
    return when (this) {
        ApplicationStatus.APPLIED -> "You have applied for the job: $jobName"
        ApplicationStatus.SELECTED -> "You are selected for the job: $jobName"
        ApplicationStatus.ACCEPTED -> "You accepted the job: $jobName and committed to show up on time"
        ApplicationStatus.WORK_IN_PROGRESS -> "You are currently doing the assigned work: $jobName"
        ApplicationStatus.COMPLETED -> "You have successfully completed the assigned job: $jobName"
        ApplicationStatus.REJECTED -> "You are rejected for the job: $jobName"
        ApplicationStatus.NOT_INTERESTED -> "You marked this job as not interested: $jobName"
        ApplicationStatus.COMPLETION_PENDING -> "Work completion pending employer verification for: $jobName"  // 🚀 Added
        ApplicationStatus.DECLINED -> "You rejected the job: $jobName after being selected"
    }
}

/**
 * 🚀 NEW: Check if OTP input should be enabled for this status
 */
fun ApplicationStatus.shouldEnableOtpInput(): Boolean {
    return this == ApplicationStatus.ACCEPTED
}

@Serializable
data class Job(
    @SerialName("id")
    val id: String = "",

    @SerialName("employer_id")
    val employerId: String = "",

    val title: String = "",

    val description: String = "",

    val location: String = "",

    @SerialName("salary_range")
    val salaryRange: String? = null,

    @SerialName("job_type")
    val jobType: WorkPreference = WorkPreference.FULL_TIME,

    @SerialName("work_type")
    val workType: WorkType = WorkType.FULL_TIME,

    @SerialName("skills_required")
    val skillsRequired: List<String> = emptyList(),

    @SerialName("preferred_skills")
    val preferredSkills: List<String> = emptyList(),

    val requirements: List<String> = emptyList(),

    @SerialName("application_deadline")
    val applicationDeadline: String? = null,

    @SerialName("work_duration")
    val workDuration: String? = null,

    val tags: List<String> = emptyList(),

    @SerialName("job_category")
    val jobCategory: String? = null,

    @SerialName("is_remote")
    val isRemote: Boolean = false,

    @SerialName("status")
    val status: JobStatus = JobStatus.PENDING_APPROVAL,

    @SerialName("district")
    val district: String = "",

    @SerialName("state")
    val state: String = "",

    @SerialName("is_active")
    val isActive: Boolean = true,

    @SerialName("created_at")
    val createdAt: String? = null,

    @SerialName("updated_at")
    val updatedAt: String? = null
)

/**
 * Comprehensive ApplicationWithJob class that combines both previous implementations
 * - Includes the complete Job object for detailed views
 * - Contains extracted job fields for efficient list displays
 * - Properly annotated for serialization
 */
@Serializable
data class ApplicationWithJob(
    @SerialName("id")
    val id: String = "",

    @SerialName("job_id")
    val jobId: String = "",

    @SerialName("employee_id")
    val employeeId: String = "",

    @SerialName("status")
    val status: ApplicationStatus = ApplicationStatus.APPLIED,

    @SerialName("applied_at")
    val appliedAt: String? = null,

    @SerialName("updated_at")
    val updatedAt: String? = null,

    // Complete job object for detailed views
    val job: Job = Job(),

    // Work session data (when available)
    val workSession: WorkSession? = null,

    // Additional fields extracted from job for efficient list displays
    @Transient
    val jobTitle: String = job.title,

    @Transient
    val jobState: String = job.state,

    @Transient
    val jobDistrict: String = job.district,

    @Transient
    val jobStatus: String = job.status.toString(),

    @Transient
    val employerId: String = job.employerId,

    @Transient
    val employerName: String? = null,

    // Work timing (from work session if available)
    @SerialName("work_start_time")
    val workStartTime: String? = workSession?.workStartTime,

    @SerialName("work_end_time")
    val workEndTime: String? = workSession?.workEndTime
)
/**
 * Data class for dashboard statistics and recent items
 */
@Serializable
data class DashboardData(
    @SerialName("active_jobs")
    val activeJobs: Int = 0,

    @SerialName("total_applications_received")
    val totalApplicationsReceived: Int = 0,

    @SerialName("recent_applications")
    val recentApplications: List<ApplicationWithJob> = emptyList()
)


@Serializable
data class Application(
    val id: String = "",

    @SerialName("job_id")
    val jobId: String = "",

    @SerialName("employee_id")
    val employeeId: String = "",

    val status: String = "APPLIED",

    // Add missing appliedAt field
    @SerialName("applied_at")
    val appliedAt: String? = null,

    @SerialName("created_at")
    val createdAt: String? = null,

    @SerialName("updated_at")
    val updatedAt: String? = null,

)


// 🚀 SIMPLE FIX: Replace your WorkSession data class in Job.kt with this version
// 🚀 UPDATED WorkSession.kt - Replace existing WorkSession in Job.kt with this version
@Serializable
data class WorkSession(
    val id: String = "",
    @SerialName("application_id")
    val applicationId: String = "",
    @SerialName("job_id")
    val jobId: String = "",
    @SerialName("employee_id")
    val employeeId: String = "",
    @SerialName("employer_id")
    val employerId: String = "",

    // 🚀 Start Work OTP (generated by employer when selecting candidate)
    // Note: Your DB stores this as character varying(10), so handling both String and Int
    @SerialName("otp")
    private val _otp: JsonElement = JsonPrimitive(""),

    @SerialName("otp_expiry")
    val otpExpiry: String = "",
    @SerialName("otp_used_at")
    val otpUsedAt: String? = null,

    // Work timing
    @SerialName("work_start_time")
    val workStartTime: String? = null,
    @SerialName("work_end_time")
    val workEndTime: String? = null,
    @SerialName("work_duration_minutes")
    val workDurationMinutes: Int? = null,

    // 🚀 NEW: Completion OTP (generated by employee when finishing work)
    @SerialName("completion_otp")
    val completionOtp: String? = null,

    @SerialName("completion_otp_expiry")
    val completionOtpExpiry: String? = null,
    @SerialName("completion_otp_used_at")
    val completionOtpUsedAt: String? = null,

    // 🚀 NEW: Wage calculation (stored as decimal in DB)
    @SerialName("hourly_rate_used")
    val hourlyRateUsed: String? = null,
    @SerialName("total_wages_calculated")
    val totalWagesCalculated: String? = null,

    // Status tracking - Updated to use WORK_IN_PROGRESS instead of WORK_STARTED
    val status: String = "OTP_GENERATED", // OTP_GENERATED, WORK_IN_PROGRESS, COMPLETION_PENDING, WORK_COMPLETED
    @SerialName("created_at")
    val createdAt: String? = null,
    @SerialName("updated_at")
    val updatedAt: String? = null
) {
    // 🚀 Start Work OTP property (handles both String and Int from database)
    val otp: String
        get() = try {
            when {
                _otp.jsonPrimitive.intOrNull != null -> {
                    val intValue = _otp.jsonPrimitive.intOrNull!!
                    intValue.toString()
                }
                _otp.jsonPrimitive.contentOrNull != null -> {
                    _otp.jsonPrimitive.contentOrNull!!
                }
                else -> {
                    _otp.toString().replace("\"", "")
                }
            }
        } catch (e: Exception) {
            _otp.toString().replace("\"", "").filter { it.isDigit() }
        }

    // 🚀 Helper properties for UI
    val isWorkStarted: Boolean
        get() = status in listOf("WORK_IN_PROGRESS", "COMPLETION_PENDING", "WORK_COMPLETED")

    val isCompletionPending: Boolean
        get() = status == "COMPLETION_PENDING"

    val isWorkCompleted: Boolean
        get() = status == "WORK_COMPLETED"

    val hasCompletionOtp: Boolean
        get() = !completionOtp.isNullOrBlank()

    // 🚀 Calculate wages if not already calculated
    val calculatedWages: Double?
        get() = try {
            totalWagesCalculated?.toDoubleOrNull() ?: run {
                val duration = workDurationMinutes ?: return@run null
                val rate = hourlyRateUsed?.toDoubleOrNull() ?: return@run null
                (duration / 60.0) * rate
            }
        } catch (e: Exception) {
            null
        }

    // 🚀 Format work duration for display
    val formattedWorkDuration: String
        get() = workDurationMinutes?.let { minutes ->
            val hours = minutes / 60
            val remainingMinutes = minutes % 60
            when {
                hours > 0 && remainingMinutes > 0 -> "${hours}h ${remainingMinutes}m"
                hours > 0 -> "${hours}h"
                else -> "${remainingMinutes}m"
            }
        } ?: "Unknown"

    // 🚀 Helper to check if OTP is expired
    val isOtpExpired: Boolean
        get() = try {
            if (otpExpiry.isBlank()) false
            else {
                val expiry = java.time.Instant.parse(otpExpiry)
                java.time.Instant.now().isAfter(expiry)
            }
        } catch (e: Exception) {
            false
        }

    // 🚀 Helper to check if completion OTP is expired
    val isCompletionOtpExpired: Boolean
        get() = try {
            if (completionOtpExpiry.isNullOrBlank()) false
            else {
                val expiry = java.time.Instant.parse(completionOtpExpiry)
                java.time.Instant.now().isAfter(expiry)
            }
        } catch (e: Exception) {
            false
        }
}
