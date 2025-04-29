package com.example.gigs.viewmodel
// Updated AuthViewModel.kt

import android.app.Activity
import kotlinx.coroutines.TimeoutCancellationException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gigs.data.model.AuthState
import com.example.gigs.data.model.OtpState
import com.example.gigs.data.model.UserType
import com.example.gigs.data.repository.AuthRepository
import com.example.gigs.data.repository.ProfileRepository
import com.google.android.play.core.integrity.e
import com.google.firebase.auth.PhoneAuthCredential
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlinx.coroutines.withTimeout


@HiltViewModel
class AuthViewModel @Inject constructor(
    val authRepository: AuthRepository,
    private val profileRepository: ProfileRepository
) : ViewModel() {

     private val _authState = MutableStateFlow<AuthState>(AuthState.Initial)
    val authState: StateFlow<AuthState> = _authState

    private val _otpState = MutableStateFlow<OtpState>(OtpState.Initial)
    val otpState: StateFlow<OtpState> = _otpState

    private val _phoneNumber = MutableStateFlow<String>("")
    val phoneNumber: StateFlow<String> = _phoneNumber

    private val _verificationId = MutableStateFlow<String>("")
    val verificationId: StateFlow<String> = _verificationId

    init {
        signOut()
        checkAuthState()
    }

    fun checkAuthState() {
        viewModelScope.launch {
            authRepository.getAuthState().collect { state ->
                _authState.value = state
            }
        }
    }

    fun setPhoneNumber(phone: String) {
        _phoneNumber.value = phone
    }

    // Add this method to AuthViewModel
    fun getCurrentUserId(): String? {
        return authRepository.getCurrentUserId()
    }

    fun sendOtp(activity: Activity) {
        if (_phoneNumber.value.isBlank()) {
            _otpState.value = OtpState.Error("Phone number cannot be empty")
            return
        }

        viewModelScope.launch {
            try {
                withTimeout(15000L) {
                    authRepository.sendOtp(_phoneNumber.value, activity).collect { state ->
                        _otpState.value = state

                        if (state is OtpState.Sent) {
                            _verificationId.value = state.verificationId
                        } else if (state is OtpState.AutoVerified) {
                            // Handle auto verification (instant verification)
                            signInWithCredential(state.credential)
                        }
                    }
                }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                // Handle timeout specially
                _otpState.value = OtpState.Error("Verification timed out. Please try again.")
            } catch (e: Exception) {
                // Handle other exceptions
                _otpState.value = OtpState.Error("Verification failed: ${e.message}")
            }
        }
    }

    private fun signInWithCredential(credential: PhoneAuthCredential) {
        viewModelScope.launch {
            // For auto-verification, we need to handle the credential sign-in
            _otpState.value = OtpState.Verified
            // Create user in database if not exists
            profileRepository.createUserIfNotExists(_phoneNumber.value)
            checkAuthState()
        }
    }

    fun verifyOtp(otp: String) {
        if (otp.isBlank()) {
            _otpState.value = OtpState.Error("OTP cannot be empty")
            return
        }

        viewModelScope.launch {
            authRepository.verifyOtp(otp).collect { state ->
                _otpState.value = state

                if (state is OtpState.Verified) {
                    // Create user in database if not exists
                    profileRepository.createUserIfNotExists(_phoneNumber.value)
                    checkAuthState()
                }
            }
        }
    }

    fun signOut(): Job {
        // Set the auth state to Unauthenticated FIRST to ensure UI updates immediately
        _authState.value = AuthState.Unauthenticated

        return viewModelScope.launch {
            try {
                // Then perform the repository sign out
                authRepository.signOut()
            } catch (e: Exception) {
                println("Error during sign out: ${e.message}")
            }
        }
    }

    fun resetOtpState() {
        _otpState.value = OtpState.Initial
    }
    /**
     * Check if the user can register/login as the requested user type
     * If they're already registered as a different type, they will be
     * restricted and logged out
     */
    suspend fun checkUserType(requestedType: UserType): Result<Boolean> {
        // If user is not authenticated yet, return success to allow them to continue
        val userId = getCurrentUserId() ?: return Result.success(true)

        // Check if user can register as the requested type
        val result = authRepository.checkExistingUserType("", requestedType)

        if (result.isFailure) {
            // IMMEDIATELY set auth state to unauthenticated to prevent any race conditions
            _authState.value = AuthState.Unauthenticated

            // Then sign out in the background and wait for completion
            val signOutJob = signOut()
            signOutJob.join() // Wait for signout to complete

            // Reset other state values
            _otpState.value = OtpState.Initial
            _phoneNumber.value = ""
            _verificationId.value = ""
        }

        return result
    }

    // In AuthViewModel.kt
    // Add this method to AuthViewModel
    fun forceLogout() {
        // Set auth state to Unauthenticated immediately
        _authState.value = AuthState.Unauthenticated

        // Then perform the repository sign out
        viewModelScope.launch {
            try {
                authRepository.signOut()
                // Reset all auth-related state to initial values
                _otpState.value = OtpState.Initial
                _phoneNumber.value = ""
                _verificationId.value = ""
            } catch (e: Exception) {
                println("Error during sign out: ${e.message}")
            }
        }
    }
}
