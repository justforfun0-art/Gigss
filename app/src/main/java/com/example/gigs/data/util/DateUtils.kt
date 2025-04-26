package com.example.gigs.utils

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Utility functions for formatting dates and timestamps throughout the app.
 */
object DateUtils {

    /**
     * Formats a date string from ISO format to a readable date format (MMM dd, yyyy)
     *
     * @param dateString The ISO date string (e.g., "2023-10-15T14:30:00.000Z")
     * @return Formatted date string (e.g., "Oct 15, 2023")
     */
    fun formatDate(dateString: String): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
            inputFormat.timeZone = TimeZone.getTimeZone("UTC")
            val date = inputFormat.parse(dateString)

            val outputFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            outputFormat.format(date)
        } catch (e: Exception) {
            dateString
        }
    }

    /**
     * Formats a date string to a full date and time format
     * Tries multiple date formats for robustness
     *
     * @param dateString The ISO date string (e.g., "2023-10-15T14:30:00.000Z")
     * @return Formatted date and time string (e.g., "Oct 15, 2023 2:30 PM")
     */
    fun formatTimestamp(dateString: String): String {
        return try {
            // First try the full ISO format with milliseconds
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
            inputFormat.timeZone = TimeZone.getTimeZone("UTC")

            var date: Date? = null

            try {
                date = inputFormat.parse(dateString)
            } catch (e: Exception) {
                // If that fails, try without milliseconds
                val simpleFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
                simpleFormat.timeZone = TimeZone.getTimeZone("UTC")

                try {
                    date = simpleFormat.parse(dateString)
                } catch (e: Exception) {
                    // If that fails too, try just the date
                    val dateOnlyFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    date = dateOnlyFormat.parse(dateString)
                }
            }

            if (date != null) {
                val outputFormat = SimpleDateFormat("MMM dd, yyyy h:mm a", Locale.getDefault())
                outputFormat.timeZone = TimeZone.getDefault() // Convert to local time
                outputFormat.format(date)
            } else {
                // If all parsing attempts fail, return the original string
                dateString
            }
        } catch (e: Exception) {
            // If any exception occurs, return the original string
            dateString
        }
    }

    /**
     * Formats a date string as a relative time (e.g., "5 min ago", "2 days ago")
     *
     * @param dateString The ISO date string (e.g., "2023-10-15T14:30:00.000Z")
     * @return Human-readable relative time
     */
    fun formatTimeAgo(dateString: String): String {
        try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
            inputFormat.timeZone = TimeZone.getTimeZone("UTC")
            val date = inputFormat.parse(dateString)

            val now = Calendar.getInstance().time
            val diffInMillis = now.time - date.time
            val diffInMinutes = diffInMillis / (60 * 1000)
            val diffInHours = diffInMillis / (60 * 60 * 1000)
            val diffInDays = diffInMillis / (24 * 60 * 60 * 1000)

            return when {
                diffInMinutes < 1 -> "Just now"
                diffInMinutes < 60 -> "$diffInMinutes min ago"
                diffInHours < 24 -> "$diffInHours hr ago"
                diffInDays < 7 -> "$diffInDays days ago"
                else -> formatDate(dateString)
            }
        } catch (e: Exception) {
            return dateString
        }
    }

    /**
     * Attempts to parse a date string using multiple possible formats
     * Useful for handling inconsistent date formats from APIs
     *
     * @param dateString The date string to parse
     * @return The parsed Date or null if parsing fails
     */
    fun parseDate(dateString: String): Date? {
        val formats = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd"
        )

        for (format in formats) {
            try {
                val sdf = SimpleDateFormat(format, Locale.getDefault())
                sdf.timeZone = TimeZone.getTimeZone("UTC")
                return sdf.parse(dateString)
            } catch (e: Exception) {
                // Try next format
            }
        }

        return null
    }
}