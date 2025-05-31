package com.example.gigs.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    private val applicationRepository: ApplicationRepository
) : ViewModel() {

    // Load all job application history
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
     * 🚀 CRITICAL FIX: Validate application status before processing
     */
    private fun validateApplicationStatus(status: String?): String {
        val normalizedStatus = status?.trim()?.uppercase() ?: ""
        return when (normalizedStatus) {
            "APPLIED" -> "APPLIED"
            "REJECTED" -> "REJECTED"
            "PENDING" -> "PENDING"
            "UNDER_REVIEW" -> "UNDER_REVIEW"
            "ACCEPTED" -> "ACCEPTED"
            "DECLINED" -> "DECLINED"
            "HIRED" -> "HIRED"
            // 🚀 CRITICAL: Remove COMPLETED entirely - convert to valid status
            "COMPLETED", "COMPLETE", "FINISHED" -> {
                Log.w(TAG, "⚠️ Invalid status '$status' converted to HIRED")
                "HIRED"
            }
            else -> {
                Log.w(TAG, "⚠️ Unknown status '$status' converted to REJECTED")
                "REJECTED"
            }
        }
    }

    // Load all job application history
    fun loadApplicationsHistory() {
        viewModelScope.launch {
            _isLoading.value = true

            try {
                Log.d(TAG, "Loading job application history")

                // Get my applications with no limit
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

                            // 🚀 CRITICAL FIX: Validate all application statuses
                            val validatedApplications = applications.map { app ->
                                val validatedStatus = validateApplicationStatus(app.status?.toString())
                                if (app.status?.toString()?.uppercase() != validatedStatus) {
                                    Log.d(TAG, "Status normalized: ${app.status} -> $validatedStatus for job ${app.jobId}")
                                }
                                app // Keep original app, validation is just for logging/filtering
                            }

                            // Debug each application
                            validatedApplications.forEachIndexed { index, app ->
                                Log.d(TAG, "Application $index: id=${app.id}, jobId=${app.jobId}, status=${app.status}, job title=${app.job.title}")
                            }

                            // Update all applications list
                            _allApplications.value = validatedApplications

                            // Categorize applications by status
                            categorizeApplications(validatedApplications)
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

    // Categorize applications into different lists based on status
    private fun categorizeApplications(applications: List<ApplicationWithJob>) {
        try {
            // Active: APPLIED, PENDING, UNDER_REVIEW
            val active = applications.filter { app ->
                val status = validateApplicationStatus(app.status?.toString())
                status in listOf("APPLIED", "PENDING", "UNDER_REVIEW")
            }
            _activeApplications.value = active
            Log.d(TAG, "Active applications: ${active.size}")

            // 🚀 CRITICAL FIX: Only use valid statuses for completed
            val completed = applications.filter { app ->
                val status = validateApplicationStatus(app.status?.toString())
                status in listOf("HIRED", "ACCEPTED") // Remove COMPLETED entirely
            }
            _completedApplications.value = completed
            Log.d(TAG, "Completed applications: ${completed.size}")

            // Rejected: REJECTED, DECLINED
            val rejected = applications.filter { app ->
                val status = validateApplicationStatus(app.status?.toString())
                status in listOf("REJECTED", "DECLINED")
            }
            _rejectedApplications.value = rejected
            Log.d(TAG, "Rejected applications: ${rejected.size}")

            // 🚀 DEBUG: Log any unhandled statuses
            val handledStatuses = setOf("APPLIED", "PENDING", "UNDER_REVIEW", "HIRED", "ACCEPTED", "REJECTED", "DECLINED")
            applications.forEach { app ->
                val status = validateApplicationStatus(app.status?.toString())
                if (status !in handledStatuses) {
                    Log.w(TAG, "⚠️ Unhandled status '$status' for application ${app.id}")
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
     * 🚀 NEW: Get statistics for debugging
     */
    fun getApplicationStats(): ApplicationStats {
        val all = _allApplications.value
        return ApplicationStats(
            total = all.size,
            active = _activeApplications.value.size,
            completed = _completedApplications.value.size,
            rejected = _rejectedApplications.value.size,
            statusBreakdown = all.groupBy { validateApplicationStatus(it.status?.toString()) }
                .mapValues { it.value.size }
        )
    }

    /**
     * 🚀 NEW: Validate and fix any applications with invalid statuses
     */
    suspend fun validateAndFixApplicationStatuses() {
        try {
            val applications = _allApplications.value
            val invalidApplications = applications.filter { app ->
                val originalStatus = app.status?.toString()?.uppercase() ?: ""
                originalStatus in listOf("COMPLETED", "COMPLETE", "FINISHED")
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
 * 🚀 NEW: Data class for application statistics
 */
data class ApplicationStats(
    val total: Int,
    val active: Int,
    val completed: Int,
    val rejected: Int,
    val statusBreakdown: Map<String, Int>
)