package com.example.gigs.data.model

import com.google.firebase.auth.PhoneAuthCredential

sealed class OtpState {
    object Initial : OtpState()
    data class Sent(val verificationId: String) : OtpState()
    data class AutoVerified(val credential: PhoneAuthCredential) : OtpState()
    object Verified : OtpState()
    data class Error(val message: String) : OtpState()
}
