package com.example.gigs.data.remote

import android.content.Context
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.storage
import io.github.jan.supabase.postgrest.postgrest
import javax.inject.Inject
import javax.inject.Singleton
import android.util.Log
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.serialization.json.Json
import io.github.jan.supabase.serializer.KotlinXSerializer

@Singleton
class SupabaseClient @Inject constructor(
    private val context: Context
) {
    // Replace with your Supabase project URL and anonymous key
    val supabaseUrl = "https://cvurevujwmmpgjafnyoa.supabase.co"
    val supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImN2dXJldnVqd21tcGdqYWZueW9hIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NDM5MjQ2MTEsImV4cCI6MjA1OTUwMDYxMX0.aUqFdP-GBgIR1TeFxbloNg0TkCs4On2OK_sys6ktUpQ"

    // Store the current auth token in memory
    private var currentAuthToken: String? = null

    // Define custom Json for serialization
    val customJson = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
        explicitNulls = false // ✅ Ensures nulls are excluded instead of written
        encodeDefaults = true // ✅ Required to write default values like FULL_TIME
    }
    val client = createSupabaseClient(
        supabaseUrl = supabaseUrl,
        supabaseKey = supabaseKey,
    ) {
        install(Postgrest)
        install(Storage)
        install(Auth) // Important: Install the Auth plugin
    }

    // Helper functions for Supabase 3.0.3

    // Get postgrest instance
    fun getPostgrest() = client.postgrest

    // Get storage instance
    fun getStorage() = client.storage

    // Get auth instance
    fun getAuth() = client.auth

    // Access a specific table
    fun table(name: String) = client.postgrest[name].also {
        Log.d("SupabaseClient", "Accessing table: $name${if(currentAuthToken != null) " with auth" else ""}")
    }

    // Access a specific bucket
    fun bucket(name: String) = client.storage[name]

    // Generic safe API call handler
    suspend fun <T> safeApiCall(apiCall: suspend () -> T): Result<T> {
        return try {
            Result.success(apiCall())
        } catch (e: Exception) {
            Log.e("SupabaseClient", "API call failed", e)
            Result.failure(e)
        }
    }

    // Method to set/store authentication token
    fun setAuthToken(token: String) {
        try {
            // Store the token for later use
            currentAuthToken = token
            Log.d("SupabaseClient", "Auth token stored: ${token.take(10)}...")
        } catch (e: Exception) {
            Log.e("SupabaseClient", "Failed to store auth token", e)
        }
    }

    // Method to get the current session token
    suspend fun getCurrentSessionToken(): String? {
        return try {
            when (val status = client.auth.sessionStatus.value) {
                is SessionStatus.Authenticated -> status.session.accessToken
                else -> {
                    Log.d("SupabaseClient", "No authenticated session found")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e("SupabaseClient", "Error getting session token", e)
            null
        }
    }

    // Get the stored auth token
    fun getStoredAuthToken(): String? = currentAuthToken

    // New method: Safe decode for lists with fallback to Map parsing
    inline fun <reified T> safeDecodeList(jsonString: String): List<T> {
        return try {
            customJson.decodeFromString<List<T>>(jsonString)
        } catch (e: Exception) {
            Log.e("SupabaseClient", "Error in safeDecodeList: ${e.message}")
            throw e
        }
    }

    // New method: Safe decode for single object with fallback
    inline fun <reified T> safeDecodeSingle(jsonString: String): T {
        return try {
            customJson.decodeFromString<T>(jsonString)
        } catch (e: Exception) {
            Log.e("SupabaseClient", "Error in safeDecodeSingle: ${e.message}")
            throw e
        }
    }
}