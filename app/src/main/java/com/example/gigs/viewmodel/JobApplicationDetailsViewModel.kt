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
class JobApplicationDetailsViewModel @Inject constructor(
    private val applicationRepository: ApplicationRepository
) : ViewModel() {

    private val _application = MutableStateFlow<ApplicationWithJob?>(null)
    val application: StateFlow<ApplicationWithJob?> = _application

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    // In a real application, you would need a way to get application by ID
    // Here's a simulation of that functionality
    fun loadApplicationDetails(applicationId: String) {
        viewModelScope.launch {
            _isLoading.value = true

            try {
                // Get all applications
                applicationRepository.getMyApplications(0).collect { result ->
                    if (result.isSuccess) {
                        val applications = result.getOrNull() ?: emptyList()

                        // Find the application with the matching ID
                        val application = applications.find { it.id == applicationId }

                        _application.value = application
                        Log.d("JobAppDetailsVM", "Loaded application: ${application?.id}")
                    } else {
                        Log.e("JobAppDetailsVM", "Error loading application: ${result.exceptionOrNull()?.message}")
                    }

                    _isLoading.value = false
                }
            } catch (e: Exception) {
                Log.e("JobAppDetailsVM", "Exception loading application: ${e.message}", e)
                _isLoading.value = false
            }
        }
    }
}