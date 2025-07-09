package com.example.gigs.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gigs.data.model.ApplicationStatus
import com.example.gigs.data.model.ApplicationWithJob
import com.example.gigs.data.model.WorkSession
import com.example.gigs.data.repository.ApplicationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import javax.inject.Inject

@HiltViewModel
class JobHistoryViewModel @Inject constructor(
    internal val applicationRepository: ApplicationRepository
) : ViewModel() {

    companion object {
        private const val TAG = "JobHistoryViewModel"
    }

    // 🚀 Main application lists
    private val _allApplications = MutableStateFlow<List<ApplicationWithJob>>(emptyList())
    val allApplications: StateFlow<List<ApplicationWithJob>> = _allApplications

    private val _activeApplications = MutableStateFlow<List<ApplicationWithJob>>(emptyList())
    val activeApplications: StateFlow<List<ApplicationWithJob>> = _activeApplications

    private val _completedApplications = MutableStateFlow<List<ApplicationWithJob>>(emptyList())
    val completedApplications: StateFlow<List<ApplicationWithJob>> = _completedApplications

    private val _rejectedApplications = MutableStateFlow<List<ApplicationWithJob>>(emptyList())
    val rejectedApplications: StateFlow<List<ApplicationWithJob>> = _rejectedApplications

    // 🚀 UI state management
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    // 🚀 Work completion state
    private val _completionOtp = MutableStateFlow<String?>(null)
    val completionOtp: StateFlow<String?> = _completionOtp

    private val _isCompletingWork = MutableStateFlow(false)
    val isCompletingWork: StateFlow<Boolean> = _isCompletingWork

    private val _completionMessage = MutableStateFlow<String?>(null)
    val completionMessage: StateFlow<String?> = _completionMessage

    /**
     * 🚀 ENHANCED: Priority-based sorting - FINAL STATUS HIERARCHY
     */
    private fun sortApplicationsByPriority(applications: List<ApplicationWithJob>): List<ApplicationWithJob> {
        return applications.sortedWith(compareBy<ApplicationWithJob> { app ->
            when (app.status) {
                ApplicationStatus.COMPLETION_PENDING -> 0    // Highest priority - needs employer verification
                ApplicationStatus.WORK_IN_PROGRESS -> 1      // High priority - currently working
                ApplicationStatus.SELECTED -> 2              // High priority - needs action to accept
                ApplicationStatus.ACCEPTED -> 3              // High priority - needs OTP to start work
                ApplicationStatus.APPLIED -> 4               // Medium priority - waiting for response
                ApplicationStatus.COMPLETED -> 5             // Medium-low priority - work finished
                ApplicationStatus.REJECTED -> 6              // Low priority - employer rejected
                ApplicationStatus.DECLINED -> 7              // Low priority - user declined
                ApplicationStatus.NOT_INTERESTED -> 8        // Lowest priority - user not interested
                else -> 9                                     // Unknown statuses
            }
        }.thenByDescending { app ->
            app.updatedAt ?: app.appliedAt ?: ""
        })
    }

    /**
     * 🚀 FINAL: Status validation - REMOVED HIRED completely
     */
    private fun validateApplicationStatus(status: String?): String {
        val normalizedStatus = status?.trim()?.uppercase() ?: ""
        return when (normalizedStatus) {
            "APPLIED" -> "APPLIED"
            "REJECTED" -> "REJECTED"
            "NOT_INTERESTED" -> "NOT_INTERESTED"
            "PENDING" -> "PENDING"
            "UNDER_REVIEW" -> "UNDER_REVIEW"
            "SELECTED" -> "SELECTED"                    // Employee selected by employer
            "ACCEPTED" -> "ACCEPTED"                    // Employee accepted the job
            "DECLINED" -> "DECLINED"
            "SHORTLISTED" -> "SHORTLISTED"
            "INTERVIEW" -> "INTERVIEW"
            "INTERVIEW_SCHEDULED" -> "INTERVIEW_SCHEDULED"
            "REVIEWING" -> "REVIEWING"
            "WORK_IN_PROGRESS" -> "WORK_IN_PROGRESS"   // Employee is currently working
            "COMPLETION_PENDING" -> "COMPLETION_PENDING" // Employee completed, waiting for employer verification
            "COMPLETED" -> "COMPLETED"                  // Work fully completed and verified
            "COMPLETE", "FINISHED" -> {
                Log.w(TAG, "⚠️ Converting legacy status '$status' to COMPLETED")
                "COMPLETED"
            }
            // 🚀 CRITICAL: Convert deprecated HIRED to SELECTED
            "HIRED" -> {
                Log.w(TAG, "⚠️ Converting deprecated status 'HIRED' to 'SELECTED'")
                "SELECTED"
            }
            else -> {
                Log.w(TAG, "⚠️ Unknown status '$status' converted to APPLIED")
                "APPLIED"
            }
        }
    }

    /**
     * 🚀 ENHANCED: Load application history with work session data
     */
    fun loadApplicationsHistory() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                Log.d(TAG, "🔍 Loading comprehensive job application history...")

                applicationRepository.getMyApplications(0)
                    .catch { e ->
                        Log.e(TAG, "❌ Error loading application history: ${e.message}")
                        handleLoadingError(e as Exception)
                    }
                    .collect { result ->
                        if (result.isSuccess) {
                            val applications = result.getOrNull() ?: emptyList()
                            Log.d(TAG, "✅ Found ${applications.size} total applications")

                            // Apply priority-based sorting
                            val sortedApplications = sortApplicationsByPriority(applications)
                            _allApplications.value = sortedApplications

                            // Categorize applications
                            categorizeApplications(sortedApplications)

                            // Load work sessions for relevant applications
                            loadWorkSessionsForApps(sortedApplications)

                        } else {
                            val error = result.exceptionOrNull()?.message ?: "Unknown error"
                            Log.e(TAG, "❌ Failed to load applications: $error")
                            _error.value = error
                            setEmptyLists()
                        }

                        _isLoading.value = false
                    }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Exception in loadApplicationsHistory: ${e.message}")
                handleLoadingError(e)
            }
        }
    }

    /**
     * 🚀 ENHANCED: Categorize applications with completion support
     */
    private fun categorizeApplications(applications: List<ApplicationWithJob>) {
        try {
            // Active applications (need user attention)
            val active = applications.filter { app ->
                val status = validateApplicationStatus(app.status?.toString())
                status in listOf(
                    "COMPLETION_PENDING",    // Highest priority - needs employer verification
                    "WORK_IN_PROGRESS",      // Currently working
                    "SELECTED",              // Ready to accept job
                    "ACCEPTED",              // Waiting for OTP to start work
                    "APPLIED",               // Applied, waiting for response
                    "PENDING",
                    "UNDER_REVIEW",
                    "REVIEWING",
                    "INTERVIEW",
                    "INTERVIEW_SCHEDULED",
                    "SHORTLISTED"
                )
            }.let { activeList ->
                activeList.sortedWith(compareBy<ApplicationWithJob> { app ->
                    when (validateApplicationStatus(app.status?.toString())) {
                        "COMPLETION_PENDING" -> 0  // Needs immediate attention
                        "WORK_IN_PROGRESS" -> 1    // Currently working
                        "SELECTED" -> 2            // Needs decision
                        "ACCEPTED" -> 3            // Waiting to start
                        "APPLIED" -> 4             // Recently applied
                        else -> 5                  // Other active statuses
                    }
                }.thenByDescending { it.updatedAt ?: it.appliedAt ?: "" })
            }
            _activeApplications.value = active
            Log.d(TAG, "📊 Active applications: ${active.size}")

            // Completed applications
            val completed = applications.filter { app ->
                validateApplicationStatus(app.status?.toString()) == "COMPLETED"
            }.sortedByDescending { it.updatedAt ?: it.appliedAt ?: "" }
            _completedApplications.value = completed
            Log.d(TAG, "📊 Completed applications: ${completed.size}")

            // Rejected applications
            val rejected = applications.filter { app ->
                val status = validateApplicationStatus(app.status?.toString())
                status in listOf("REJECTED", "DECLINED", "NOT_INTERESTED")
            }.let { rejectedList ->
                rejectedList.sortedWith(compareBy<ApplicationWithJob> { app ->
                    when (validateApplicationStatus(app.status?.toString())) {
                        "REJECTED" -> 0
                        "DECLINED" -> 1
                        "NOT_INTERESTED" -> 2
                        else -> 3
                    }
                }.thenByDescending { it.updatedAt ?: it.appliedAt ?: "" })
            }
            _rejectedApplications.value = rejected
            Log.d(TAG, "📊 Rejected applications: ${rejected.size}")

            // Enhanced status breakdown
            logStatusBreakdown(applications)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error categorizing applications: ${e.message}", e)
        }
    }

    /**
     * 🚀 NEW: Load work sessions for applications that might have them
     */
    private suspend fun loadWorkSessionsForApps(applications: List<ApplicationWithJob>) {
        try {
            applications.forEach { app ->
                if (app.status in listOf(
                        ApplicationStatus.SELECTED,
                        ApplicationStatus.ACCEPTED,
                        ApplicationStatus.WORK_IN_PROGRESS,
                        ApplicationStatus.COMPLETION_PENDING,
                        ApplicationStatus.COMPLETED
                    )) {
                    // Work sessions are already loaded by ApplicationRepository
                    // This is just for any additional processing if needed
                    app.workSession?.let { workSession ->
                        Log.d(TAG, "📄 Application ${app.id}: WorkSession status = ${workSession.status}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error processing work sessions: ${e.message}")
        }
    }

    /**
     * 🚀 ENHANCED: Get selected jobs (SELECTED, ACCEPTED, WORK_IN_PROGRESS, COMPLETION_PENDING)
     */
    fun getSelectedJobs(): List<ApplicationWithJob> {
        return try {
            val selectedJobs = allApplications.value.filter { application ->
                val status = validateApplicationStatus(application.status?.toString())
                status in listOf("SELECTED", "ACCEPTED", "WORK_IN_PROGRESS", "COMPLETION_PENDING")
            }.sortedWith(compareBy<ApplicationWithJob> { app ->
                when (validateApplicationStatus(app.status?.toString())) {
                    "COMPLETION_PENDING" -> 0  // Highest priority
                    "WORK_IN_PROGRESS" -> 1
                    "ACCEPTED" -> 2
                    "SELECTED" -> 3
                    else -> 4
                }
            }.thenByDescending { it.updatedAt })

            Log.d(TAG, "🚀 Found ${selectedJobs.size} selected/active jobs")
            selectedJobs.forEach { job ->
                Log.d(TAG, "   📋 ${job.job.title} - Status: ${job.status}")
            }

            selectedJobs
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting selected jobs: ${e.message}")
            emptyList()
        }
    }

    /**
     * 🚀 ENHANCED: Get active job (WORK_IN_PROGRESS only)
     */
    suspend fun getActiveJob(): ApplicationWithJob? {
        return try {
            val workInProgressJobs = allApplications.value.filter { application ->
                validateApplicationStatus(application.status?.toString()) == "WORK_IN_PROGRESS"
            }

            val activeJob = workInProgressJobs.find { app ->
                app.workSession?.status in listOf("WORK_IN_PROGRESS", "OTP_GENERATED")
            } ?: workInProgressJobs.maxByOrNull { it.updatedAt ?: "" }

            if (activeJob != null) {
                Log.d(TAG, "🚀 Active job: ${activeJob.job.title} - Status: ${activeJob.status}")
            } else {
                Log.d(TAG, "🚀 No active job found")
            }

            activeJob
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting active job: ${e.message}")
            null
        }
    }

    /**
     * 🚀 NEW: Complete work - Employee initiates completion
     */
    fun completeWork(applicationId: String) {
        viewModelScope.launch {
            _isCompletingWork.value = true
            _error.value = null
            _completionMessage.value = null

            try {
                Log.d(TAG, "🚀 Employee initiating work completion for: $applicationId")

                val result = applicationRepository.initiateWorkCompletion(applicationId)

                if (result.isSuccess) {
                    val message = result.getOrNull() ?: "Work completion initiated"
                    _completionMessage.value = message

                    // Extract completion OTP from message if present
                    val otpRegex = "completion code[:\\s]+(\\d{6})".toRegex(RegexOption.IGNORE_CASE)
                    val otpMatch = otpRegex.find(message)
                    if (otpMatch != null) {
                        val otp = otpMatch.groupValues[1]
                        _completionOtp.value = otp
                        Log.d(TAG, "🔐 Extracted completion OTP: $otp")
                    }

                    Log.d(TAG, "✅ Work completion initiated successfully")

                    // Refresh applications to update status
                    delay(1000)
                    refreshApplicationHistory()

                } else {
                    val error = result.exceptionOrNull()?.message ?: "Failed to complete work"
                    Log.e(TAG, "❌ Work completion failed: $error")
                    _error.value = error
                }
            } catch (e: Exception) {
                val error = "Unexpected error: ${e.message}"
                Log.e(TAG, "❌ Exception during work completion: $error")
                _error.value = error
            } finally {
                _isCompletingWork.value = false
            }
        }
    }

    /**
     * 🚀 NEW: Get completion OTP for display
     */
    fun getCompletionOtpForApplication(applicationId: String) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "🔍 Getting completion OTP for application: $applicationId")

                val result = applicationRepository.getCompletionOtpForApplication(applicationId)

                if (result.isSuccess) {
                    val otp = result.getOrNull() ?: ""
                    _completionOtp.value = otp
                    Log.d(TAG, "✅ Retrieved completion OTP: $otp")
                } else {
                    Log.e(TAG, "❌ Failed to get completion OTP: ${result.exceptionOrNull()?.message}")
                    _completionOtp.value = null
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error getting completion OTP: ${e.message}")
                _completionOtp.value = null
            }
        }
    }

    /**
     * 🚀 NEW: Check if work can be completed
     */
    fun canCompleteWork(application: ApplicationWithJob): Boolean {
        val status = validateApplicationStatus(application.status?.toString())
        val canComplete = status == "WORK_IN_PROGRESS" &&
                application.workSession?.status == "WORK_IN_PROGRESS"

        Log.d(TAG, "🔍 Can complete work for ${application.job.title}: $canComplete")
        return canComplete
    }

    /**
     * 🚀 NEW: Check if completion is pending employer verification
     */
    fun isCompletionPending(application: ApplicationWithJob): Boolean {
        val status = validateApplicationStatus(application.status?.toString())
        val isPending = status == "COMPLETION_PENDING" ||
                application.workSession?.status == "COMPLETION_PENDING"

        Log.d(TAG, "🔍 Completion pending for ${application.job.title}: $isPending")
        return isPending
    }

    /**
     * 🚀 ENHANCED: Application statistics with completion support
     */
    fun getApplicationStats(): ApplicationStats {
        val all = _allApplications.value

        val stats = ApplicationStats(
            total = all.size,
            active = _activeApplications.value.size,
            completed = _completedApplications.value.size,
            rejected = _rejectedApplications.value.size,
            workInProgress = all.count { validateApplicationStatus(it.status?.toString()) == "WORK_IN_PROGRESS" },
            selected = all.count { validateApplicationStatus(it.status?.toString()) == "SELECTED" },
            accepted = all.count { validateApplicationStatus(it.status?.toString()) == "ACCEPTED" },
            completionPending = all.count { validateApplicationStatus(it.status?.toString()) == "COMPLETION_PENDING" },
            notInterested = all.count { validateApplicationStatus(it.status?.toString()) == "NOT_INTERESTED" },
            statusBreakdown = all.groupBy { validateApplicationStatus(it.status?.toString()) }
                .mapValues { it.value.size }
        )

        logApplicationStats(stats)
        return stats
    }

    /**
     * 🚀 NEW: Get total earnings from completed jobs
     */
    fun getTotalEarnings(): Double {
        return try {
            _completedApplications.value.sumOf { application ->
                application.workSession?.let { session ->
                    session.totalWagesCalculated?.toDoubleOrNull() ?:
                    session.calculatedWages?.toDouble()
                } ?: 0.0
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error calculating total earnings: ${e.message}")
            0.0
        }
    }

    /**
     * Clear completion state
     */
    fun clearCompletionState() {
        _completionOtp.value = null
        _completionMessage.value = null
        _error.value = null
    }

    /**
     * Force refresh application history
     */
    fun refreshApplicationHistory() {
        Log.d(TAG, "🔄 Refreshing application history...")
        loadApplicationsHistory()
    }

    /**
     * Helper methods
     */
    private fun handleLoadingError(e: Exception) {
        _error.value = e.message ?: "Unknown error occurred"
        setEmptyLists()
        _isLoading.value = false
    }

    private fun setEmptyLists() {
        _allApplications.value = emptyList()
        _activeApplications.value = emptyList()
        _completedApplications.value = emptyList()
        _rejectedApplications.value = emptyList()
    }

    private fun logStatusBreakdown(applications: List<ApplicationWithJob>) {
        val statusBreakdown = applications.groupBy {
            validateApplicationStatus(it.status?.toString())
        }.mapValues { it.value.size }

        Log.d(TAG, "📊 Status breakdown (by priority):")
        statusBreakdown.toList().sortedBy { (status, _) ->
            when (status) {
                "COMPLETION_PENDING" -> 0
                "WORK_IN_PROGRESS" -> 1
                "SELECTED" -> 2
                "ACCEPTED" -> 3
                "APPLIED" -> 4
                "COMPLETED" -> 5
                "REJECTED" -> 6
                "DECLINED" -> 7
                "NOT_INTERESTED" -> 8
                else -> 9
            }
        }.forEach { (status, count) ->
            Log.d(TAG, "   $status: $count applications")
        }

        // Log priority jobs
        val priorityJobs = applications.filter { app ->
            val status = validateApplicationStatus(app.status?.toString())
            status in listOf("COMPLETION_PENDING", "WORK_IN_PROGRESS", "SELECTED", "ACCEPTED")
        }

        if (priorityJobs.isNotEmpty()) {
            Log.d(TAG, "🔥 High Priority Jobs (need attention):")
            priorityJobs.forEach { app ->
                val workSessionStatus = app.workSession?.status ?: "none"
                Log.d(TAG, "   📋 ${app.job.title} - App: ${app.status}, Session: $workSessionStatus")
            }
        }
    }

    private fun logApplicationStats(stats: ApplicationStats) {
        Log.d(TAG, "📊 Application Statistics:")
        Log.d(TAG, "   Total: ${stats.total}")
        Log.d(TAG, "   Completion Pending: ${stats.completionPending} (Highest Priority)")
        Log.d(TAG, "   Work in Progress: ${stats.workInProgress}")
        Log.d(TAG, "   Selected: ${stats.selected}")
        Log.d(TAG, "   Accepted: ${stats.accepted}")
        Log.d(TAG, "   Active: ${stats.active}")
        Log.d(TAG, "   Completed: ${stats.completed}")
        Log.d(TAG, "   Rejected: ${stats.rejected}")
        Log.d(TAG, "   Not Interested: ${stats.notInterested}")
    }
}

/**
 * 🚀 ENHANCED: Application statistics with completion support
 */
data class ApplicationStats(
    val total: Int,
    val active: Int,
    val completed: Int,
    val rejected: Int,
    val workInProgress: Int = 0,
    val selected: Int = 0,
    val accepted: Int = 0,
    val completionPending: Int = 0,  // 🚀 NEW: Track completion pending
    val notInterested: Int = 0,
    val statusBreakdown: Map<String, Int>
)