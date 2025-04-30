package com.example.gigs.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gigs.data.model.ApplicationWithJob
import com.example.gigs.data.repository.ApplicationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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

    fun loadApplicationsHistory() {
        viewModelScope.launch {
            _isLoading.value = true

            try {
                // Get all applications without limit
                applicationRepository.getMyApplications(0).collect { result ->
                    if (result.isSuccess) {
                        val applications = result.getOrNull() ?: emptyList()

                        // Store all applications
                        _allApplications.value = applications.sortedByDescending { it.appliedAt }

                        // Filter applications by status
                        _activeApplications.value = applications.filter {
                            it.status.uppercase() in listOf("APPLIED", "SHORTLISTED")
                        }.sortedByDescending { it.appliedAt }

                        _completedApplications.value = applications.filter {
                            it.status.uppercase() == "HIRED"
                        }.sortedByDescending { it.appliedAt }

                        _rejectedApplications.value = applications.filter {
                            it.status.uppercase() == "REJECTED"
                        }.sortedByDescending { it.appliedAt }

                        Log.d("JobHistoryViewModel", "Loaded ${applications.size} applications")

                        // Log counts for debugging
                        Log.d("JobHistoryViewModel", "Active: ${_activeApplications.value.size}")
                        Log.d("JobHistoryViewModel", "Completed: ${_completedApplications.value.size}")
                        Log.d("JobHistoryViewModel", "Rejected: ${_rejectedApplications.value.size}")
                    } else {
                        Log.e("JobHistoryViewModel", "Error loading applications: ${result.exceptionOrNull()?.message}")
                    }

                    _isLoading.value = false
                }
            } catch (e: Exception) {
                Log.e("JobHistoryViewModel", "Exception loading applications: ${e.message}", e)
                _isLoading.value = false
            }
        }
    }

    fun refreshApplications() {
        loadApplicationsHistory()
    }
}