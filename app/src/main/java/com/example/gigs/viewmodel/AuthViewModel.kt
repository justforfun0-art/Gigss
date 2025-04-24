package com.example.gigs.viewmodel
// Updated AuthViewModel.kt

import android.app.Activity
import kotlinx.coroutines.TimeoutCancellationException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gigs.data.model.AuthState
import com.example.gigs.data.model.OtpState
import com.example.gigs.data.repository.AuthRepository
import com.example.gigs.data.repository.ProfileRepository
import com.google.android.play.core.integrity.e
import com.google.firebase.auth.PhoneAuthCredential
import dagger.hilt.android.lifecycle.HiltViewModel
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

    fun signOut() {
        viewModelScope.launch {
            authRepository.signOut()
            _authState.value = AuthState.Unauthenticated
        }
    }

    fun resetOtpState() {
        _otpState.value = OtpState.Initial
    }
}