package com.example.gigs.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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

@Serializable
enum class ApplicationStatus {
    APPLIED,
    REVIEWING,
    INTERVIEW,
    SHORTLISTED,  // Add this missing status
    INTERVIEW_SCHEDULED, // Add this if it exists in your data
    REJECTED,
    ACCEPTED,
    HIRED, // Add this if needed
    COMPLETED, // Add this if needed
    WITHDRAWN
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

    // Additional fields extracted from job for efficient list displays
    // These fields don't need to be included in serialization when the full job object is present
    // Use @Transient if these are only used for UI and shouldn't be serialized
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
val employerName: String? = null
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

    @SerialName("created_at")
    val createdAt: String? = null,

    @SerialName("updated_at")
    val updatedAt: String? = null
)