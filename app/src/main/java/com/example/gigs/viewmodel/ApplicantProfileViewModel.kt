package com.example.gigs.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gigs.data.model.EmployeeProfile
import com.example.gigs.data.model.EmployeeProfileWithUserInfo
import com.example.gigs.data.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ApplicantProfileViewModel @Inject constructor(
    private val profileRepository: ProfileRepository
) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _applicantProfile = MutableStateFlow<EmployeeProfileWithUserInfo?>(null)
    val applicantProfile: StateFlow<EmployeeProfileWithUserInfo?> = _applicantProfile.asStateFlow()

    /**
     * Load applicant profile by ID
     * Uses existing repository method to fetch the employee profile
     */
    fun loadApplicantProfile(applicantId: String) {
        viewModelScope.launch {
            _isLoading.value = true

            // Use the existing repository method to fetch the profile
            profileRepository.getEmployeeProfileWithUserInfo(applicantId).collect { result ->
                if (result.isSuccess) {
                    _applicantProfile.value = result.getOrNull()
                } else {
                    // Handle error
                    _applicantProfile.value = null
                }
                _isLoading.value = false
            }
        }
    }
}