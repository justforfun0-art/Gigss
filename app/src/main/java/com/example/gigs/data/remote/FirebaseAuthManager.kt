package com.example.gigs.data.remote

import android.app.Activity
import com.example.gigs.data.model.OtpState
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseAuthManager @Inject constructor() {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private var verificationId: String = ""

    fun sendOtp(phoneNumber: String, activity: Activity): Flow<OtpState> = callbackFlow {
        trySend(OtpState.Initial)

        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                // This callback will be invoked in two situations:
                // 1 - Instant verification. In some cases, the phone number can be instantly
                //     verified without needing to send or enter an OTP.
                // 2 - Auto-retrieval. On some devices, Google Play services can automatically
                //     detect the incoming verification SMS and perform verification without
                //     user action.
                trySend(OtpState.AutoVerified(credential))
            }

            override fun onVerificationFailed(e: FirebaseException) {
                trySend(OtpState.Error(e.message ?: "Verification failed"))
            }

            override fun onCodeSent(
                vId: String,
                token: PhoneAuthProvider.ForceResendingToken
            ) {
                // The SMS verification code has been sent to the provided phone number
                verificationId = vId
                trySend(OtpState.Sent(vId))
            }
        }

        val formattedNumber = if (!phoneNumber.startsWith("+")) "+$phoneNumber" else phoneNumber

        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(formattedNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(callbacks)
            .build()

        try {
            PhoneAuthProvider.verifyPhoneNumber(options)
        } catch (e: Exception) {
            trySend(OtpState.Error(e.message ?: "Failed to initiate verification"))
        }

        awaitClose {}
    }

    fun getCurrentUserPhone(): String? {
        return auth.currentUser?.phoneNumber
    }

    fun verifyOtp(otp: String): Flow<OtpState> = callbackFlow {
        trySend(OtpState.Initial)

        if (verificationId.isBlank()) {
            trySend(OtpState.Error("Verification ID is empty. Please resend OTP."))
            close()
            return@callbackFlow
        }

        try {
            val credential = PhoneAuthProvider.getCredential(verificationId, otp)
            auth.signInWithCredential(credential)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        trySend(OtpState.Verified)
                    } else {
                        trySend(OtpState.Error(task.exception?.message ?: "Verification failed"))
                    }
                }
        } catch (e: Exception) {
            trySend(OtpState.Error(e.message ?: "Invalid verification code"))
        }

        awaitClose {}
    }

    fun signOut() {
        auth.signOut()
    }

    fun getCurrentUserId(): String? {
        return auth.currentUser?.uid
    }

    fun isAuthenticated(): Boolean {
        return auth.currentUser != null
    }
}