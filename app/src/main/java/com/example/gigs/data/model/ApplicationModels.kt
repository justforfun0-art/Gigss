package com.example.gigs.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Transient
import java.util.UUID

class ApplicationModels {

    @Serializable
    data class ApplicationStatusChange(
        val id: String = UUID.randomUUID().toString(),
        @SerialName("application_id") val applicationId: String,
        @SerialName("job_id") val jobId: String,
        @SerialName("employee_id") val employeeId: String,
        @SerialName("employer_id") val employerId: String,
        @SerialName("old_status") val oldStatus: String,
        @SerialName("new_status") val newStatus: String,
        @SerialName("change_reason") val changeReason: String,
        @SerialName("requires_admin_approval") val requiresAdminApproval: Boolean,
        @SerialName("admin_approved") val adminApproved: Boolean = false,
        @SerialName("admin_id") val adminId: String? = null,
        @SerialName("admin_notes") val adminNotes: String? = null,
        @SerialName("created_at") val createdAt: Long = System.currentTimeMillis(),
        @SerialName("approved_at") val approvedAt: Long? = null
    )

    @Serializable
    data class ApplicationWithJobLocked(
        @SerialName("id") val id: String = "",
        @SerialName("job_id") val jobId: String = "",
        @SerialName("employee_id") val employeeId: String = "",
        @SerialName("status") val status: ApplicationStatus = ApplicationStatus.APPLIED,
        @SerialName("applied_at") val appliedAt: String? = null,
        @SerialName("updated_at") val updatedAt: String? = null,
        @SerialName("is_status_locked") val isStatusLocked: Boolean = false,
        @SerialName("locked_at") val lockedAt: Long? = null,
        @SerialName("locked_by") val lockedBy: String? = null,
        val job: Job = Job(),
        @Transient val jobTitle: String = job.title,
        @Transient val jobState: String = job.state,
        @Transient val jobDistrict: String = job.district,
        @Transient val employerName: String? = null
    )

    // ================================
    // 2. UPDATED STATUS CHANGE VALIDATION LOGIC FOR NEW FLOW
    // ================================

    object ApplicationStatusRules {

        // 🚀 UPDATED: Statuses that lock the application (prevent further changes without admin approval)
        // Based on new simplified flow: APPLIED → SELECTED → ACCEPTED → WORK_IN_PROGRESS → COMPLETED
        val LOCKING_STATUSES = setOf(
            ApplicationStatus.ACCEPTED,          // Once accepted, employee is committed
            ApplicationStatus.WORK_IN_PROGRESS,  // Work in progress should be protected
            ApplicationStatus.COMPLETED         // Completed work is final
        )

        // 🚀 UPDATED: Status changes that require admin approval even if not locked
        val ADMIN_REQUIRED_CHANGES = mapOf(
            ApplicationStatus.ACCEPTED to setOf(
                ApplicationStatus.REJECTED,
                ApplicationStatus.APPLIED,
                ApplicationStatus.SELECTED
            ),
            ApplicationStatus.WORK_IN_PROGRESS to setOf(
                ApplicationStatus.REJECTED,
                ApplicationStatus.APPLIED,
                ApplicationStatus.SELECTED,
                ApplicationStatus.ACCEPTED
            ),
            ApplicationStatus.COMPLETED to setOf(
                ApplicationStatus.REJECTED,
                ApplicationStatus.APPLIED,
                ApplicationStatus.SELECTED,
                ApplicationStatus.ACCEPTED,
                ApplicationStatus.WORK_IN_PROGRESS
            )
        )

        // 🚀 UPDATED: Valid status transitions for the new simplified flow
        private val VALID_TRANSITIONS = mapOf(
            ApplicationStatus.APPLIED to setOf(
                ApplicationStatus.SELECTED,
                ApplicationStatus.REJECTED,
                ApplicationStatus.NOT_INTERESTED  // Employee can mark as not interested
            ),
            ApplicationStatus.SELECTED to setOf(
                ApplicationStatus.ACCEPTED,        // Employee accepts the job
                ApplicationStatus.DECLINED,        // Employee declines the job
                ApplicationStatus.REJECTED         // Employer can still reject
            ),
            ApplicationStatus.ACCEPTED to setOf(
                ApplicationStatus.WORK_IN_PROGRESS, // Start work with OTP
                ApplicationStatus.DECLINED          // Employee can still decline before work starts
            ),
            ApplicationStatus.WORK_IN_PROGRESS to setOf(
                ApplicationStatus.COMPLETED        // Complete the work
            ),
            ApplicationStatus.COMPLETED to emptySet(),        // Terminal status
            ApplicationStatus.REJECTED to emptySet(),         // Terminal status
            ApplicationStatus.DECLINED to emptySet(),         // Terminal status
            ApplicationStatus.NOT_INTERESTED to emptySet()    // Terminal status (can be reconsidered separately)
        )

        fun validateStatusChange(
            currentStatus: ApplicationStatus,
            newStatus: ApplicationStatus,
            isCurrentlyLocked: Boolean,
            isAdmin: Boolean = false
        ): StatusChangeValidation {

            // Admin can change any status
            if (isAdmin) {
                return StatusChangeValidation.Allowed
            }

            // Check if trying to change a terminal status
            if (isTerminalStatus(currentStatus) && currentStatus != newStatus) {
                return StatusChangeValidation.RequiresAdminApproval(
                    reason = "Status '${currentStatus.getDisplayText()}' is terminal and requires admin approval to change."
                )
            }

            // If currently locked and trying to change from a locking status
            if (isCurrentlyLocked && currentStatus in LOCKING_STATUSES) {
                return StatusChangeValidation.RequiresAdminApproval(
                    reason = "Application status is locked. Changes from '${currentStatus.getDisplayText()}' require admin approval."
                )
            }

            // Check if this specific change requires admin approval
            val requiredApprovalChanges = ADMIN_REQUIRED_CHANGES[currentStatus]
            if (requiredApprovalChanges != null && newStatus in requiredApprovalChanges) {
                return StatusChangeValidation.RequiresAdminApproval(
                    reason = "Changing from '${currentStatus.getDisplayText()}' to '${newStatus.getDisplayText()}' requires admin approval."
                )
            }

            // Check for invalid transitions
            if (!isValidStatusTransition(currentStatus, newStatus)) {
                return StatusChangeValidation.Invalid(
                    reason = "Invalid status transition from '${currentStatus.getDisplayText()}' to '${newStatus.getDisplayText()}'"
                )
            }

            return StatusChangeValidation.Allowed
        }

        private fun isValidStatusTransition(from: ApplicationStatus, to: ApplicationStatus): Boolean {
            // Same status is always allowed (no-op)
            if (from == to) return true

            return VALID_TRANSITIONS[from]?.contains(to) == true
        }

        fun shouldLockStatus(status: ApplicationStatus): Boolean {
            return status in LOCKING_STATUSES
        }

        // 🚀 NEW: Check if status is terminal (cannot progress further without admin intervention)
        fun isTerminalStatus(status: ApplicationStatus): Boolean {
            return status in setOf(
                ApplicationStatus.COMPLETED,
                ApplicationStatus.REJECTED,
                ApplicationStatus.DECLINED,
                ApplicationStatus.NOT_INTERESTED
            )
        }

        // 🚀 NEW: Get all possible next statuses for a given status
        fun getPossibleNextStatuses(currentStatus: ApplicationStatus): Set<ApplicationStatus> {
            return VALID_TRANSITIONS[currentStatus] ?: emptySet()
        }

        // 🚀 NEW: Check if a status change requires OTP verification
        fun requiresOtpVerification(from: ApplicationStatus, to: ApplicationStatus): Boolean {
            return from == ApplicationStatus.ACCEPTED && to == ApplicationStatus.WORK_IN_PROGRESS
        }

        // 🚀 NEW: Check if status represents active work
        fun isActiveWorkStatus(status: ApplicationStatus): Boolean {
            return status in setOf(
                ApplicationStatus.SELECTED,
                ApplicationStatus.ACCEPTED,
                ApplicationStatus.WORK_IN_PROGRESS
            )
        }

        // 🚀 NEW: Get status priority for sorting (higher number = higher priority)
        fun getStatusPriority(status: ApplicationStatus): Int {
            return when (status) {
                ApplicationStatus.WORK_IN_PROGRESS -> 5  // Highest priority
                ApplicationStatus.ACCEPTED -> 4
                ApplicationStatus.SELECTED -> 3
                ApplicationStatus.APPLIED -> 2
                ApplicationStatus.COMPLETED -> 1
                ApplicationStatus.REJECTED, ApplicationStatus.DECLINED, ApplicationStatus.NOT_INTERESTED -> 0
            }
        }

        // 🚀 NEW: Check if status can be displayed in job history
        fun shouldShowInHistory(status: ApplicationStatus): Boolean {
            // All statuses should be shown in history
            return true
        }

        // 🚀 NEW: Check if status can be displayed in active jobs
        fun shouldShowInActiveJobs(status: ApplicationStatus): Boolean {
            return status in setOf(
                ApplicationStatus.APPLIED,
                ApplicationStatus.SELECTED,
                ApplicationStatus.ACCEPTED,
                ApplicationStatus.WORK_IN_PROGRESS
            )
        }

        // 🚀 NEW: Get status category for filtering
        fun getStatusCategory(status: ApplicationStatus): StatusCategory {
            return when (status) {
                ApplicationStatus.APPLIED -> StatusCategory.PENDING
                ApplicationStatus.SELECTED -> StatusCategory.ACTIVE
                ApplicationStatus.ACCEPTED -> StatusCategory.ACTIVE
                ApplicationStatus.WORK_IN_PROGRESS -> StatusCategory.ACTIVE
                ApplicationStatus.COMPLETED -> StatusCategory.COMPLETED
                ApplicationStatus.REJECTED, ApplicationStatus.DECLINED, ApplicationStatus.NOT_INTERESTED -> StatusCategory.REJECTED
            }
        }
    }

    // 🚀 NEW: Status categories for filtering and organization
    enum class StatusCategory {
        PENDING,    // Applications that are waiting for response
        ACTIVE,     // Applications that require action or are in progress
        COMPLETED,  // Successfully completed applications
        REJECTED    // Rejected, declined, or not interested applications
    }

    sealed class StatusChangeValidation {
        object Allowed : StatusChangeValidation()
        data class RequiresAdminApproval(val reason: String) : StatusChangeValidation()
        data class Invalid(val reason: String) : StatusChangeValidation()
    }

    // 🚀 NEW: Work session validation rules
    object WorkSessionRules {

        // Check if OTP is required for status change
        fun requiresOtp(from: ApplicationStatus, to: ApplicationStatus): Boolean {
            return ApplicationStatusRules.requiresOtpVerification(from, to)
        }

        // Check if work session can be started
        fun canStartWork(currentStatus: ApplicationStatus): Boolean {
            return currentStatus == ApplicationStatus.ACCEPTED
        }

        // Check if work session can be completed
        fun canCompleteWork(currentStatus: ApplicationStatus): Boolean {
            return currentStatus == ApplicationStatus.WORK_IN_PROGRESS
        }

        // Validate OTP format
        fun isValidOtp(otp: String): Boolean {
            return otp.length == 6 && otp.all { it.isDigit() }
        }

        // Check if work session is active
        fun isWorkSessionActive(status: ApplicationStatus): Boolean {
            return status == ApplicationStatus.WORK_IN_PROGRESS
        }
    }

    // 🚀 NEW: Employee action validation
    object EmployeeActionRules {

        // Check if employee can accept a job
        fun canAcceptJob(currentStatus: ApplicationStatus): Boolean {
            return currentStatus == ApplicationStatus.SELECTED
        }

        // Check if employee can decline a job
        fun canDeclineJob(currentStatus: ApplicationStatus): Boolean {
            return currentStatus in setOf(
                ApplicationStatus.SELECTED,
                ApplicationStatus.ACCEPTED  // Can decline before work starts
            )
        }

        // Check if employee can mark as not interested
        fun canMarkNotInterested(currentStatus: ApplicationStatus): Boolean {
            return currentStatus == ApplicationStatus.APPLIED
        }

        // Check if employee can start work
        fun canStartWork(currentStatus: ApplicationStatus): Boolean {
            return currentStatus == ApplicationStatus.ACCEPTED
        }

        // Check if employee can complete work
        fun canCompleteWork(currentStatus: ApplicationStatus): Boolean {
            return currentStatus == ApplicationStatus.WORK_IN_PROGRESS
        }
    }

    // 🚀 NEW: Employer action validation
    object EmployerActionRules {

        // Check if employer can select an employee
        fun canSelectEmployee(currentStatus: ApplicationStatus): Boolean {
            return currentStatus == ApplicationStatus.APPLIED
        }

        // Check if employer can reject an application
        fun canRejectApplication(currentStatus: ApplicationStatus): Boolean {
            return currentStatus in setOf(
                ApplicationStatus.APPLIED,
                ApplicationStatus.SELECTED  // Can reject even after selection
            )
        }

        // Check if employer can generate OTP
        fun canGenerateOtp(currentStatus: ApplicationStatus): Boolean {
            return currentStatus == ApplicationStatus.ACCEPTED
        }

        // Check if employer can mark work as completed
        fun canMarkWorkCompleted(currentStatus: ApplicationStatus): Boolean {
            return currentStatus == ApplicationStatus.WORK_IN_PROGRESS
        }
    }
}