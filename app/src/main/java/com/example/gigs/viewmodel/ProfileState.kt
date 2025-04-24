package com.example.gigs.viewmodel
import com.example.gigs.data.model.EmployeeProfile
import com.example.gigs.data.model.EmployerProfile
import com.example.gigs.data.model.Profile

sealed class ProfileState {
    object Initial : ProfileState()
    object Loading : ProfileState()
    data class BasicProfileCreated(val profile: Profile) : ProfileState()
    data class EmployeeProfileCreated(val profile: EmployeeProfile) : ProfileState()
    data class EmployerProfileCreated(val profile: EmployerProfile) : ProfileState()
    data class Error(val message: String) : ProfileState()
}