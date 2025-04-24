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
data class Job(
    @Transient
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

// For job applications tracking
@Serializable
data class ApplicationWithJob(
    val id: String = "",

    @SerialName("job_id")
    val jobId: String = "",

    @SerialName("employee_id")
    val employeeId: String = "",

    val status: String = "APPLIED",

    @SerialName("applied_at")
    val appliedAt: String? = null,

    @SerialName("updated_at")
    val updatedAt: String? = null,

    val job: Job = Job()
)