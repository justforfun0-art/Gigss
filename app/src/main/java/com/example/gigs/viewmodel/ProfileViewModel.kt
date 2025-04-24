package com.example.gigs.viewmodel

// Updated ProfileViewModel.kt
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gigs.data.model.EmployeeProfile
import com.example.gigs.data.model.EmployerProfile
import com.example.gigs.data.model.Gender
import com.example.gigs.data.model.Profile
import com.example.gigs.data.model.UserType
import com.example.gigs.data.model.WorkPreference
import com.example.gigs.data.repository.AuthRepository
import com.example.gigs.data.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.io.InputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _profileState = MutableStateFlow<ProfileState>(ProfileState.Initial)
    val profileState: StateFlow<ProfileState> = _profileState

    private val _userType = MutableStateFlow<UserType>(UserType.UNDEFINED)
    val userType: StateFlow<UserType> = _userType

    private val _selectedState = MutableStateFlow<String>("")
    val selectedState: StateFlow<String> = _selectedState

    private val _selectedDistrict = MutableStateFlow<String>("")
    val selectedDistrict: StateFlow<String> = _selectedDistrict

    private val _profilePhotoUri = MutableStateFlow<Uri?>(null)
    val profilePhotoUri: StateFlow<Uri?> = _profilePhotoUri
    private val _employeeProfile = MutableStateFlow<EmployeeProfile?>(null)
    val employeeProfile: StateFlow<EmployeeProfile?> = _employeeProfile

    fun getEmployeeProfile() {
        viewModelScope.launch {
            val userId = authRepository.getCurrentUserId() ?: return@launch

            profileRepository.getEmployeeProfileByUserId(userId).collect { result ->
                if (result.isSuccess) {
                    _employeeProfile.value = result.getOrNull()
                }
            }
        }
    }

    fun setUserType(type: UserType) {
        _userType.value = type
    }

    fun setSelectedState(state: String) {
        _selectedState.value = state
        // Clear district when state changes
        _selectedDistrict.value = ""
    }

    fun setSelectedDistrict(district: String) {
        _selectedDistrict.value = district
    }

    fun setProfilePhotoUri(uri: Uri?) {
        _profilePhotoUri.value = uri
    }

    fun createBasicProfile(fullName: String, email: String?) {
        if (fullName.isBlank() || _userType.value == UserType.UNDEFINED) {
            _profileState.value = ProfileState.Error("Name is required")
            return
        }

        viewModelScope.launch {
            _profileState.value = ProfileState.Loading

            // Get the current user ID from the auth repository
            val userId = authRepository.getCurrentUserId()

            // Check if userId is null
            if (userId == null) {
                _profileState.value = ProfileState.Error("User not authenticated")
                return@launch
            }

            val profile = Profile(
                userId = userId, // Add this line with the appropriate user ID
                fullName = fullName,
                email = email ?: "", // Handle nullable email
                userType = _userType.value,
                isProfileComplete = false
            )

            profileRepository.createOrUpdateProfile(profile).collect { result ->
                if (result.isSuccess) {
                    _profileState.value = ProfileState.BasicProfileCreated(result.getOrNull()!!)
                } else {
                    _profileState.value = ProfileState.Error(
                        result.exceptionOrNull()?.message ?: "Failed to create profile"
                    )
                }
            }
        }
    }

    fun createEmployeeProfile(
        name: String,
        dob: LocalDate,
        gender: Gender,
        hasComputerKnowledge: Boolean,
        state: String,
        district: String,
        email: String?,
        profilePhotoInputStream: InputStream?,
        workPreferences: List<WorkPreference>
    ) {
        if (name.isBlank() || state.isBlank() || district.isBlank() || workPreferences.isEmpty()) {
            _profileState.value = ProfileState.Error("All required fields must be filled")
            return
        }

        if (name.length > 20) {
            _profileState.value = ProfileState.Error("Name cannot exceed 20 characters")
            return
        }

        viewModelScope.launch {
            _profileState.value = ProfileState.Loading

            // First, upload profile photo if provided
            var profilePhotoUrl: String? = null

            if (profilePhotoInputStream != null) {
                val userId = authRepository.getCurrentUserId() ?: return@launch

                try {
                    val photoBytes = profilePhotoInputStream.readBytes()
                    val photoUrlResult = profileRepository.uploadProfilePhoto(userId, photoBytes)

                    photoUrlResult.collect { result ->
                        if (result.isSuccess) {
                            profilePhotoUrl = result.getOrNull()
                        } else {
                            _profileState.value = ProfileState.Error("Failed to upload profile photo")
                            return@collect
                        }
                    }
                } catch (e: Exception) {
                    _profileState.value = ProfileState.Error("Failed to process profile photo")
                    return@launch
                }
            }

            // Format date as ISO string
            val formattedDate = dob.format(DateTimeFormatter.ISO_DATE)

            // Now create the employee profile
            val employeeProfile = EmployeeProfile(
                name = name,
                dob = formattedDate,
                gender = gender,
                hasComputerKnowledge = hasComputerKnowledge,
                state = state,
                district = district,
                email = email,
                profilePhotoUrl = profilePhotoUrl,
                workPreferences = workPreferences
            )

            profileRepository.createOrUpdateEmployeeProfile(employeeProfile).collect { result ->
                if (result.isSuccess) {
                    _profileState.value = ProfileState.EmployeeProfileCreated(result.getOrNull()!!)
                } else {
                    _profileState.value = ProfileState.Error(
                        result.exceptionOrNull()?.message ?: "Failed to create employee profile"
                    )
                }
            }
        }
    }

    fun createEmployerProfile(
        companyName: String,
        industry: String,
        companySize: String,
        state: String,
        district: String,
        website: String?,
        description: String
    ) {
        if (companyName.isBlank() || industry.isBlank() || state.isBlank() || district.isBlank()) {
            _profileState.value = ProfileState.Error("All required fields must be filled")
            return
        }

        viewModelScope.launch {
            _profileState.value = ProfileState.Loading

            val employerProfile = EmployerProfile(
                companyName = companyName,
                industry = industry,
                companySize = companySize,
                state = state,
                district = district,
                website = website,
                description = description
            )

            profileRepository.createOrUpdateEmployerProfile(employerProfile).collect { result ->
                if (result.isSuccess) {
                    _profileState.value = ProfileState.EmployerProfileCreated(result.getOrNull()!!)
                } else {
                    _profileState.value = ProfileState.Error(
                        result.exceptionOrNull()?.message ?: "Failed to create employer profile"
                    )
                }
            }
        }
    }

    fun resetState() {
        _profileState.value = ProfileState.Initial
    }
}

// ProfileState.kt remains largely the same