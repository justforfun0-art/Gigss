package com.example.gigs.utils

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Utility functions for formatting dates and timestamps throughout the app.
 * Updated to handle microsecond precision timestamps and provide simple, readable formats.
 */
object DateUtils {

    /**
     * Formats a date string to a simple, readable date format (MMM dd, yyyy)
     * Handles multiple input formats including microseconds
     *
     * @param dateString The ISO date string (e.g., "2025-05-03T18:04:11.724176+00:00")
     * @return Formatted date string (e.g., "May 03, 2025")
     */
    fun formatDate(dateString: String): String {
        return try {
            val date = parseDate(dateString)
            if (date != null) {
                val outputFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                outputFormat.format(date)
            } else {
                // Fallback to simple format if parsing fails
                formatFallbackDate(dateString)
            }
        } catch (e: Exception) {
            formatFallbackDate(dateString)
        }
    }

    /**
     * Formats a date string to show date and time in a simple format
     *
     * @param dateString The ISO date string
     * @return Formatted date and time string (e.g., "May 03, 2025 at 6:04 PM")
     */
    fun formatDateTime(dateString: String): String {
        return try {
            val date = parseDate(dateString)
            if (date != null) {
                val outputFormat = SimpleDateFormat("MMM dd, yyyy 'at' h:mm a", Locale.getDefault())
                outputFormat.format(date)
            } else {
                formatFallbackDate(dateString)
            }
        } catch (e: Exception) {
            formatFallbackDate(dateString)
        }
    }

    /**
     * Formats a date string to show only time
     *
     * @param dateString The ISO date string
     * @return Formatted time string (e.g., "6:04 PM")
     */
    fun formatTime(dateString: String): String {
        return try {
            val date = parseDate(dateString)
            if (date != null) {
                val outputFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
                outputFormat.format(date)
            } else {
                "Unknown time"
            }
        } catch (e: Exception) {
            "Unknown time"
        }
    }

    /**
     * Formats a date string as a relative time (e.g., "2 hours ago", "3 days ago")
     * Falls back to absolute date for older dates
     *
     * @param dateString The ISO date string
     * @return Human-readable relative time or absolute date
     */
    fun formatTimeAgo(dateString: String): String {
        return try {
            val date = parseDate(dateString)
            if (date == null) return formatFallbackDate(dateString)

            val now = Calendar.getInstance().time
            val diffInMillis = now.time - date.time
            val diffInMinutes = diffInMillis / (60 * 1000)
            val diffInHours = diffInMillis / (60 * 60 * 1000)
            val diffInDays = diffInMillis / (24 * 60 * 60 * 1000)

            when {
                diffInMinutes < 1 -> "Just now"
                diffInMinutes < 60 -> "${diffInMinutes}m ago"
                diffInHours < 24 -> "${diffInHours}h ago"
                diffInDays < 7 -> "${diffInDays}d ago"
                diffInDays < 30 -> "${diffInDays / 7}w ago"
                else -> DateUtils.formatDate(dateString) // Show absolute date for old items
            }
        } catch (e: Exception) {
            formatFallbackDate(dateString)
        }
    }

    /**
     * Formats a date for application timeline display
     *
     * @param dateString The ISO date string
     * @return Formatted string for timeline (e.g., "Applied on May 03, 2025")
     */
    fun formatApplicationDate(dateString: String): String {
        return try {
            val date = parseDate(dateString)
            if (date != null) {
                val outputFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                "Applied on ${outputFormat.format(date)}"
            } else {
                "Applied on ${formatFallbackDate(dateString)}"
            }
        } catch (e: Exception) {
            "Applied recently"
        }
    }

    /**
     * Simple format for job posting dates
     *
     * @param dateString The ISO date string
     * @return Formatted string (e.g., "Posted May 03")
     */
    fun formatJobPostDate(dateString: String): String {
        return try {
            val date = parseDate(dateString)
            if (date != null) {
                val outputFormat = SimpleDateFormat("MMM dd", Locale.getDefault())
                "Posted ${outputFormat.format(date)}"
            } else {
                "Posted recently"
            }
        } catch (e: Exception) {
            "Posted recently"
        }
    }

    /**
     * Attempts to parse a date string using multiple possible formats
     * Updated to handle microsecond precision and various timezone formats
     *
     * @param dateString The date string to parse
     * @return The parsed Date or null if parsing fails
     */
    fun parseDate(dateString: String): Date? {
        // Clean the input string first
        val cleanDateString = dateString.trim()

        val formats = listOf(
            // Handle microsecond precision with timezone
            "yyyy-MM-dd'T'HH:mm:ss.SSSSSS'+00:00'",
            "yyyy-MM-dd'T'HH:mm:ss.SSSSSSX",
            "yyyy-MM-dd'T'HH:mm:ss.SSSSSSXXX",

            // Handle millisecond precision
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSSX",

            // Handle without milliseconds
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ssX",
            "yyyy-MM-dd'T'HH:mm:ss",

            // Handle date only
            "yyyy-MM-dd"
        )

        for (format in formats) {
            try {
                val sdf = SimpleDateFormat(format, Locale.getDefault())
                sdf.timeZone = TimeZone.getTimeZone("UTC")

                // Special handling for microseconds - truncate to milliseconds
                val processedDateString = if (format.contains("SSSSSS")) {
                    truncateMicroseconds(cleanDateString)
                } else {
                    cleanDateString
                }

                return sdf.parse(processedDateString)
            } catch (e: Exception) {
                // Try next format
                continue
            }
        }

        return null
    }

    /**
     * Truncates microseconds to milliseconds for parsing
     * Converts "2025-05-03T18:04:11.724176+00:00" to "2025-05-03T18:04:11.724+00:00"
     */
    private fun truncateMicroseconds(dateString: String): String {
        return try {
            // Find the pattern with microseconds
            val regex = """(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.)(\d{6})(.*)""".toRegex()
            val matchResult = regex.find(dateString)

            if (matchResult != null) {
                val (prefix, microseconds, suffix) = matchResult.destructured
                val milliseconds = microseconds.take(3) // Take only first 3 digits
                "$prefix$milliseconds$suffix"
            } else {
                dateString
            }
        } catch (e: Exception) {
            dateString
        }
    }

    /**
     * Fallback formatting for when parsing fails
     * Extracts date parts manually if possible
     */
    private fun formatFallbackDate(dateString: String): String {
        return try {
            // Try to extract date parts manually: "2025-05-03T..."
            val datePart = dateString.split("T")[0]
            val parts = datePart.split("-")

            if (parts.size == 3) {
                val year = parts[0]
                val month = parts[1].toInt()
                val day = parts[2].toInt()

                val monthNames = arrayOf(
                    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
                    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
                )

                val monthName = monthNames.getOrNull(month - 1) ?: month.toString()
                "$monthName $day, $year"
            } else {
                dateString.split("T")[0] // Return just the date part
            }
        } catch (e: Exception) {
            dateString.split("T").getOrNull(0) ?: dateString
        }
    }

    /**
     * Legacy method - kept for backward compatibility
     * Use formatDateTime() for new implementations
     */
    @Deprecated("Use formatDateTime() instead", ReplaceWith("formatDateTime(dateString)"))
    fun formatTimestamp(dateString: String): String {
        return formatDateTime(dateString)
    }
}