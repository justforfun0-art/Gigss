package com.example.gigs.viewmodel

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

    // Load all job application history
    fun loadApplicationsHistory() {
        viewModelScope.launch {
            _isLoading.value = true

            try {
                println("Loading job application history")

                // Get my applications with no limit
                applicationRepository.getMyApplications(0)
                    .catch { e ->
                        println("Error loading application history: ${e.message}")
                        e.printStackTrace() // Add stack trace for more details
                        _allApplications.value = emptyList()
                        _activeApplications.value = emptyList()
                        _completedApplications.value = emptyList()
                        _rejectedApplications.value = emptyList()
                        _isLoading.value = false
                    }
                    .collect { result ->
                        if (result.isSuccess) {
                            val applications = result.getOrNull() ?: emptyList()
                            println("Found ${applications.size} total applications in history")

                            // Debug each application
                            applications.forEachIndexed { index, app ->
                                println("Application $index: id=${app.id}, jobId=${app.jobId}, status=${app.status}, " +
                                        "job title=${app.job.title}")
                            }

                            // Update all applications list
                            _allApplications.value = applications

                            // Categorize applications by status
                            categorizeApplications(applications)
                        } else {
                            println("Failed to load application history: ${result.exceptionOrNull()?.message}")
                            result.exceptionOrNull()?.printStackTrace() // Add stack trace
                            _allApplications.value = emptyList()
                            _activeApplications.value = emptyList()
                            _completedApplications.value = emptyList()
                            _rejectedApplications.value = emptyList()
                        }

                        _isLoading.value = false
                    }
            } catch (e: Exception) {
                println("Exception in loadApplicationsHistory: ${e.message}")
                e.printStackTrace()
                _isLoading.value = false
            }
        }
    }

    // Categorize applications into different lists based on status
    private fun categorizeApplications(applications: List<ApplicationWithJob>) {
        try {
            // Active: APPLIED, SHORTLISTED, INTERVIEW_SCHEDULED
            val active = applications.filter { app ->
                val status = app.status?.toString()?.uppercase() ?: ""
                status == "APPLIED" || status == "SHORTLISTED" || status == "INTERVIEW_SCHEDULED"
            }
            _activeApplications.value = active
            println("Active applications: ${active.size}")

            // Completed: HIRED
            val completed = applications.filter { app ->
                val status = app.status?.toString()?.uppercase() ?: ""
                status == "HIRED" || status == "COMPLETED"
            }
            _completedApplications.value = completed
            println("Completed applications: ${completed.size}")

            // Rejected: REJECTED
            val rejected = applications.filter { app ->
                val status = app.status?.toString()?.uppercase() ?: ""
                status == "REJECTED"
            }
            _rejectedApplications.value = rejected
            println("Rejected applications: ${rejected.size}")

        } catch (e: Exception) {
            println("Error categorizing applications: ${e.message}")
        }
    }

    // Force refresh
    fun refreshApplicationHistory() {
        loadApplicationsHistory()
    }

    // Helper method to get a specific application by ID
    fun getApplicationById(applicationId: String): ApplicationWithJob? {
        return _allApplications.value.find { it.id == applicationId }
    }
}