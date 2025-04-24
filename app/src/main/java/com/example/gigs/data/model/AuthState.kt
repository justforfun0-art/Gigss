package com.example.gigs.data.model

sealed class AuthState {
    object Initial : AuthState()
    object Authenticated : AuthState()
    object Unauthenticated : AuthState()
    object ProfileIncomplete : AuthState()
    data class Error(val message: String) : AuthState()
}