package com.example.gigs.data.util

import kotlinx.serialization.json.Json

object CustomSerializer {
    val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true  // Handles nulls by using default values
        isLenient = true          // More forgiving parsing
        explicitNulls = false     // Don't include nulls in serialization
    }
}