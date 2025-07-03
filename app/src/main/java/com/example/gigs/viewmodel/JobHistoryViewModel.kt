// 🚀 UPDATED JobHistoryViewModel.kt - Remove HIRED, Use SELECTED/ACCEPTED Throughout

package com.example.gigs.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gigs.data.model.ApplicationStatus
import com.example.gigs.data.model.ApplicationWithJob
import com.example.gigs.data.repository.ApplicationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class JobHistoryViewModel @Inject constructor(
    internal val applicationRepository: ApplicationRepository
) : ViewModel() {

    companion object {
        private const val TAG = "JobHistoryViewModel"
    }

    private val _allApplications = MutableStateFlow<List<ApplicationWithJob>>(emptyList())
    val allApplications: StateFlow<List<ApplicationWithJob>> = _allApplications

    private val _activeApplications = MutableStateFlow<List<ApplicationWithJob>>(emptyList())
    val activeApplications: StateFlow<List<ApplicationWithJob>> = _activeApplications

    private val _completedApplications = MutableStateFlow<List<ApplicationWithJob>>(emptyList())
    val completedApplications: StateFlow<List<ApplicationWithJob>> = _completedApplications

    private val _rejectedApplications = MutableStateFlow<List<ApplicationWithJob>>(emptyList())
    val rejectedApplications: StateFlow<List<ApplicationWithJob>> = _rejectedApplications

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    /**
     * 🚀 UPDATED: Priority-based sorting for applications - Removed HIRED
     */
    private fun sortApplicationsByPriority(applications: List<ApplicationWithJob>): List<ApplicationWithJob> {
        return applications.sortedWith(compareBy<ApplicationWithJob> { app ->
            // Primary sort: by status priority (active first, not interested last)
            when (app.status) {
                ApplicationStatus.WORK_IN_PROGRESS -> 0      // Highest priority - currently working
                ApplicationStatus.SELECTED -> 1              // High priority - needs action to accept/decline
                ApplicationStatus.ACCEPTED -> 2              // High priority - needs OTP to start work
                ApplicationStatus.APPLIED -> 3               // Medium priority - waiting for response
                ApplicationStatus.COMPLETED -> 4             // Medium-low priority - work finished
                ApplicationStatus.REJECTED -> 5              // Low priority - employer rejected
                ApplicationStatus.DECLINED -> 6              // Low priority - user declined
                ApplicationStatus.NOT_INTERESTED -> 7        // Lowest priority - user not interested
                else -> 8                                     // Unknown statuses
            }
        }.thenByDescending { app ->
            // Secondary sort: by most recent activity within same priority
            app.updatedAt ?: app.appliedAt ?: ""
        })
    }

    /**
     * 🚀 UPDATED: Validate application status - Removed HIRED references
     */
    private fun validateApplicationStatus(status: String?): String {
        val normalizedStatus = status?.trim()?.uppercase() ?: ""
        return when (normalizedStatus) {
            "APPLIED" -> "APPLIED"
            "REJECTED" -> "REJECTED"
            "NOT_INTERESTED" -> "NOT_INTERESTED"
            "PENDING" -> "PENDING"
            "UNDER_REVIEW" -> "UNDER_REVIEW"
            "ACCEPTED" -> "ACCEPTED"  // 🚀 Keep ACCEPTED
            "DECLINED" -> "DECLINED"
            "SHORTLISTED" -> "SHORTLISTED"
            "INTERVIEW" -> "INTERVIEW"
            "INTERVIEW_SCHEDULED" -> "INTERVIEW_SCHEDULED"
            "REVIEWING" -> "REVIEWING"
            // 🚀 Work-related statuses
            "SELECTED" -> "SELECTED"
            "WORK_IN_PROGRESS" -> "WORK_IN_PROGRESS"
            "COMPLETED" -> "COMPLETED"
            "COMPLETE", "FINISHED" -> {
                Log.w(TAG, "⚠️ Invalid status '$status' converted to COMPLETED")
                "COMPLETED"
            }
            // 🚀 REMOVED: HIRED status - no longer valid
            else -> {
                Log.w(TAG, "⚠️ Unknown status '$status' converted to APPLIED")
                "APPLIED"
            }
        }
    }

    // Load all job application history
    fun loadApplicationsHistory() {
        viewModelScope.launch {
            _isLoading.value = true

            try {
                Log.d(TAG, "Loading job application history")

                applicationRepository.getMyApplications(0)
                    .catch { e ->
                        Log.e(TAG, "Error loading application history: ${e.message}")
                        e.printStackTrace()
                        _allApplications.value = emptyList()
                        _activeApplications.value = emptyList()
                        _completedApplications.value = emptyList()
                        _rejectedApplications.value = emptyList()
                        _isLoading.value = false
                    }
                    .collect { result ->
                        if (result.isSuccess) {
                            val applications = result.getOrNull() ?: emptyList()
                            Log.d(TAG, "Found ${applications.size} total applications in history")

                            // 🚀 UPDATED: Apply priority-based sorting to all applications
                            val sortedApplications = sortApplicationsByPriority(applications)

                            // Update all applications list with sorted order
                            _allApplications.value = sortedApplications

                            // Categorize applications by status (but keep individual lists sorted too)
                            categorizeApplications(sortedApplications)
                        } else {
                            Log.e(TAG, "Failed to load application history: ${result.exceptionOrNull()?.message}")
                            result.exceptionOrNull()?.printStackTrace()
                            _allApplications.value = emptyList()
                            _activeApplications.value = emptyList()
                            _completedApplications.value = emptyList()
                            _rejectedApplications.value = emptyList()
                        }

                        _isLoading.value = false
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Exception in loadApplicationsHistory: ${e.message}")
                e.printStackTrace()
                _isLoading.value = false
            }
        }
    }

    /**
     * 🚀 UPDATED: Categorize applications - Removed HIRED references
     */
    private fun categorizeApplications(applications: List<ApplicationWithJob>) {
        try {
            // 🚀 UPDATED: Active applications with priority sorting - No HIRED
            val active = applications.filter { app ->
                val status = validateApplicationStatus(app.status?.toString())
                status in listOf(
                    "SELECTED",              // Ready to start work (highest priority in active)
                    "WORK_IN_PROGRESS",      // Currently working
                    "ACCEPTED",              // Accepted, waiting for OTP
                    "APPLIED",               // Applied, waiting for response
                    "PENDING",
                    "UNDER_REVIEW",
                    "REVIEWING",
                    "INTERVIEW",
                    "INTERVIEW_SCHEDULED",
                    "SHORTLISTED"
                )
            }.let { activeList ->
                // Sort active applications with work-related statuses first
                activeList.sortedWith(compareBy<ApplicationWithJob> { app ->
                    when (validateApplicationStatus(app.status?.toString())) {
                        "WORK_IN_PROGRESS" -> 0  // Currently working - highest priority
                        "SELECTED" -> 1           // Needs decision - high priority
                        "ACCEPTED" -> 2           // Waiting to start - high priority
                        "APPLIED" -> 3            // Recently applied
                        else -> 4                 // Other active statuses
                    }
                }.thenByDescending { it.updatedAt ?: it.appliedAt ?: "" })
            }
            _activeApplications.value = active
            Log.d(TAG, "Active applications: ${active.size}")

            // 🚀 UPDATED: Completed applications - Removed HIRED references
            val completed = applications.filter { app ->
                val status = validateApplicationStatus(app.status?.toString())
                status in listOf("COMPLETED", "ACCEPTED")  // ACCEPTED jobs are also considered completed
            }.sortedByDescending { it.updatedAt ?: it.appliedAt ?: "" }
            _completedApplications.value = completed
            Log.d(TAG, "Completed applications: ${completed.size}")

            // 🚀 UPDATED: Rejected applications with NOT_INTERESTED at the end
            val rejected = applications.filter { app ->
                val status = validateApplicationStatus(app.status?.toString())
                status in listOf("REJECTED", "DECLINED", "NOT_INTERESTED")
            }.let { rejectedList ->
                // Sort rejected with NOT_INTERESTED last
                rejectedList.sortedWith(compareBy<ApplicationWithJob> { app ->
                    when (validateApplicationStatus(app.status?.toString())) {
                        "REJECTED" -> 0     // Employer rejected
                        "DECLINED" -> 1     // User declined after selection
                        "NOT_INTERESTED" -> 2  // User marked as not interested (lowest priority)
                        else -> 3
                    }
                }.thenByDescending { it.updatedAt ?: it.appliedAt ?: "" })
            }
            _rejectedApplications.value = rejected
            Log.d(TAG, "Rejected applications: ${rejected.size}")

            // 🚀 Enhanced status breakdown logging
            val statusBreakdown = applications.groupBy {
                validateApplicationStatus(it.status?.toString())
            }.mapValues { it.value.size }

            Log.d(TAG, "📊 Status breakdown (sorted by priority):")
            statusBreakdown.toList().sortedBy { (status, _) ->
                when (status) {
                    "WORK_IN_PROGRESS" -> 0
                    "SELECTED" -> 1
                    "ACCEPTED" -> 2
                    "APPLIED" -> 3
                    "COMPLETED" -> 4
                    "REJECTED" -> 5
                    "DECLINED" -> 6
                    "NOT_INTERESTED" -> 7
                    else -> 8
                }
            }.forEach { (status, count) ->
                Log.d(TAG, "   $status: $count applications")
            }

            // 🚀 Log priority jobs specifically
            val priorityJobs = applications.filter { app ->
                val status = validateApplicationStatus(app.status?.toString())
                status in listOf("WORK_IN_PROGRESS", "SELECTED", "ACCEPTED")
            }

            if (priorityJobs.isNotEmpty()) {
                Log.d(TAG, "🔥 High Priority Jobs (need attention):")
                priorityJobs.forEach { app ->
                    Log.d(TAG, "   Job: ${app.job.title} - Status: ${app.status}")
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error categorizing applications: ${e.message}", e)
        }
    }

    // Force refresh
    fun refreshApplicationHistory() {
        Log.d(TAG, "Refreshing application history")
        loadApplicationsHistory()
    }

    // Helper method to get a specific application by ID
    fun getApplicationById(applicationId: String): ApplicationWithJob? {
        return _allApplications.value.find { it.id == applicationId }
    }

    /**
     * 🚀 NEW: Check if user has an active job
     */
    suspend fun hasActiveJob(): Boolean {
        return getActiveJob() != null
    }

    /**
     * 🚀 UPDATED: Get jobs with SELECTED, ACCEPTED, or WORK_IN_PROGRESS status (removed HIRED)
     */
    fun getSelectedJobs(): List<ApplicationWithJob> {
        return try {
            val selectedJobs = allApplications.value.filter { application ->
                val status = validateApplicationStatus(application.status?.toString())
                status in listOf("SELECTED", "ACCEPTED", "WORK_IN_PROGRESS")
            }.sortedWith(compareBy<ApplicationWithJob> { app ->
                // Sort priority: WORK_IN_PROGRESS first, then ACCEPTED, then SELECTED
                when (validateApplicationStatus(app.status?.toString())) {
                    "WORK_IN_PROGRESS" -> 0
                    "ACCEPTED" -> 1
                    "SELECTED" -> 2
                    else -> 3
                }
            }.thenByDescending { it.updatedAt })

            Log.d(TAG, "🚀 Found ${selectedJobs.size} selected/accepted/working jobs")
            selectedJobs.forEach { job ->
                Log.d(TAG, "   Job: ${job.job.title} - Status: ${job.status} - Updated: ${job.updatedAt}")
            }

            selectedJobs
        } catch (e: Exception) {
            Log.e(TAG, "Error getting selected jobs: ${e.message}")
            emptyList()
        }
    }

    /**
     * 🚀 UPDATED: Get active job - now only returns WORK_IN_PROGRESS status
     */
    suspend fun getActiveJob(): ApplicationWithJob? {
        return try {
            val workInProgressJobs = allApplications.value.filter { application ->
                val status = validateApplicationStatus(application.status?.toString())
                status == "WORK_IN_PROGRESS"
            }

            val activeJob = workInProgressJobs.find { app ->
                // Check if has active work session
                app.workSession?.status in listOf("OTP_GENERATED", "WORK_STARTED")
            } ?: workInProgressJobs.maxByOrNull { it.updatedAt ?: "" }

            if (activeJob != null) {
                Log.d(TAG, "🚀 Active job found: ${activeJob.job.title} - Status: ${activeJob.status}")
            } else {
                Log.d(TAG, "🚀 No active job found")
            }

            activeJob
        } catch (e: Exception) {
            Log.e(TAG, "Error getting active job: ${e.message}")
            null
        }
    }

    /**
     * 🚀 NEW: Get applications sorted by priority (for UI components)
     */
    fun getApplicationsSortedByPriority(): List<ApplicationWithJob> {
        return sortApplicationsByPriority(_allApplications.value)
    }

    /**
     * 🚀 NEW: Get applications by specific status with proper sorting
     */
    fun getApplicationsByStatus(status: ApplicationStatus): List<ApplicationWithJob> {
        return try {
            val targetStatus = validateApplicationStatus(status.toString())
            val applications = allApplications.value.filter { application ->
                val appStatus = validateApplicationStatus(application.status?.toString())
                appStatus == targetStatus
            }

            // Sort by most recent first within the same status
            val sortedApplications = applications.sortedByDescending {
                it.updatedAt ?: it.appliedAt ?: ""
            }

            Log.d(TAG, "🚀 Found ${sortedApplications.size} applications with status: $targetStatus")
            sortedApplications
        } catch (e: Exception) {
            Log.e(TAG, "Error getting applications by status: ${e.message}")
            emptyList()
        }
    }

    /**
     * 🚀 NEW: Check if user has any selected jobs that need attention
     */
    fun hasSelectedJobs(): Boolean {
        return try {
            val selectedCount = getSelectedJobs().size
            Log.d(TAG, "🚀 Has selected jobs: $selectedCount")
            selectedCount > 0
        } catch (e: Exception) {
            Log.e(TAG, "Error checking selected jobs: ${e.message}")
            false
        }
    }

    /**
     * 🚀 NEW: Check if user has work in progress
     */
    fun hasWorkInProgress(): Boolean {
        return try {
            val workInProgress = allApplications.value.any { application ->
                val status = validateApplicationStatus(application.status?.toString())
                status == "WORK_IN_PROGRESS"
            }
            Log.d(TAG, "🚀 Has work in progress: $workInProgress")
            workInProgress
        } catch (e: Exception) {
            Log.e(TAG, "Error checking work in progress: ${e.message}")
            false
        }
    }

    /**
     * 🚀 NEW: Get job by ID from current applications
     */
    fun getJobById(jobId: String): ApplicationWithJob? {
        return try {
            val job = allApplications.value.find { it.jobId == jobId }
            if (job != null) {
                Log.d(TAG, "🚀 Found job: ${job.job.title} - Status: ${job.status}")
            } else {
                Log.d(TAG, "🚀 Job not found: $jobId")
            }
            job
        } catch (e: Exception) {
            Log.e(TAG, "Error getting job by ID: ${e.message}")
            null
        }
    }

    /**
     * 🚀 UPDATED: Get statistics - Removed HIRED references
     */
    fun getApplicationStats(): ApplicationStats {
        val all = _allApplications.value
        val workInProgress = all.count {
            validateApplicationStatus(it.status?.toString()) == "WORK_IN_PROGRESS"
        }
        val selected = all.count {
            validateApplicationStatus(it.status?.toString()) == "SELECTED"
        }
        val accepted = all.count {
            validateApplicationStatus(it.status?.toString()) == "ACCEPTED"
        }
        val notInterested = all.count {
            validateApplicationStatus(it.status?.toString()) == "NOT_INTERESTED"
        }

        val stats = ApplicationStats(
            total = all.size,
            active = _activeApplications.value.size,
            completed = _completedApplications.value.size,
            rejected = _rejectedApplications.value.size,
            workInProgress = workInProgress,
            selected = selected,
            accepted = accepted,  // 🚀 Changed from hired to accepted
            notInterested = notInterested,
            statusBreakdown = all.groupBy { validateApplicationStatus(it.status?.toString()) }
                .mapValues { it.value.size }
        )

        Log.d(TAG, "📊 Application Statistics (Priority Order):")
        Log.d(TAG, "   Total: ${stats.total}")
        Log.d(TAG, "   Work in Progress: ${stats.workInProgress} (Highest Priority)")
        Log.d(TAG, "   Selected: ${stats.selected} (High Priority)")
        Log.d(TAG, "   Accepted: ${stats.accepted} (High Priority)")
        Log.d(TAG, "   Active: ${stats.active}")
        Log.d(TAG, "   Completed: ${stats.completed}")
        Log.d(TAG, "   Rejected: ${stats.rejected}")
        Log.d(TAG, "   Not Interested: ${stats.notInterested} (Lowest Priority)")

        return stats
    }

    /**
     * 🚀 UPDATED: Check for date conflicts - Remove HIRED references
     */
    fun checkDateConflict(newJobId: String): Boolean {
        return try {
            val acceptedJobs = allApplications.value.filter { application ->
                val status = validateApplicationStatus(application.status?.toString())
                status in listOf("ACCEPTED", "WORK_IN_PROGRESS") && application.jobId != newJobId
            }

            // For now, simplified conflict check
            val hasConflict = acceptedJobs.any { existingJob ->
                val newJobDate = allApplications.value.find { it.jobId == newJobId }?.job?.createdAt?.substring(0, 10)
                val existingJobDate = existingJob.job.createdAt?.substring(0, 10)
                newJobDate == existingJobDate
            }

            if (hasConflict) {
                Log.w(TAG, "⚠️ Date conflict detected for job: $newJobId")
            }

            hasConflict
        } catch (e: Exception) {
            Log.e(TAG, "Error checking date conflict: ${e.message}")
            false
        }
    }

    /**
     * 🚀 NEW: Force refresh specific job status
     */
    fun refreshJobStatus(jobId: String) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "🚀 Refreshing status for job: $jobId")
                refreshApplicationHistory()
            } catch (e: Exception) {
                Log.e(TAG, "Error refreshing job status: ${e.message}")
            }
        }
    }

    suspend fun validateAndFixApplicationStatuses() {
        try {
            val applications = _allApplications.value
            val invalidApplications = applications.filter { app ->
                val originalStatus = app.status?.toString()?.uppercase() ?: ""
                // 🚀 UPDATED: COMPLETED is now valid, HIRED is no longer valid
                originalStatus in listOf("COMPLETE", "FINISHED", "HIRED")  // HIRED is now invalid
            }

            if (invalidApplications.isNotEmpty()) {
                Log.w(TAG, "Found ${invalidApplications.size} applications with invalid statuses")
                invalidApplications.forEach { app ->
                    Log.w(TAG, "Invalid application: ${app.id} with status ${app.status}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error validating application statuses: ${e.message}")
        }
    }
}

/**
 * 🚀 UPDATED: ApplicationStats data class - Removed hired, added accepted
 */
data class ApplicationStats(
    val total: Int,
    val active: Int,
    val completed: Int,
    val rejected: Int,
    val workInProgress: Int = 0,
    val selected: Int = 0,
    val accepted: Int = 0,  // 🚀 Changed from hired to accepted
    val notInterested: Int = 0,
    val statusBreakdown: Map<String, Int>
)