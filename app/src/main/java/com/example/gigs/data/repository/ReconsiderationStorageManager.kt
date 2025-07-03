// 🚀 ReconsiderationStorageManager - Persistent Storage for Reconsidered Jobs

package com.example.gigs.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages persistent storage of reconsidered job IDs to ensure
 * each rejected job is only shown once for reconsideration per lifetime
 */
@Singleton
class ReconsiderationStorageManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authRepository: AuthRepository
) {
    companion object {
        private const val TAG = "ReconsiderationStorage"
        private const val PREFS_NAME = "reconsideration_data"
        private const val KEY_RECONSIDERED_JOBS = "reconsidered_job_ids"
        private const val KEY_LAST_SYNC = "last_sync_timestamp"
    }

    private val sharedPreferences: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * 🚀 Load reconsidered job IDs for current user
     */
    suspend fun loadReconsideredJobIds(): Set<String> = withContext(Dispatchers.IO) {
        return@withContext try {
            val userId = authRepository.getCurrentUserId()
            if (userId == null) {
                Log.w(TAG, "No current user, returning empty reconsidered jobs set")
                return@withContext emptySet()
            }

            val userKey = "${KEY_RECONSIDERED_JOBS}_$userId"
            val jobIdsString = sharedPreferences.getString(userKey, "") ?: ""

            val jobIds = if (jobIdsString.isNotBlank()) {
                jobIdsString.split(",").filter { it.isNotBlank() }.toSet()
            } else {
                emptySet()
            }

            Log.d(TAG, "✅ Loaded ${jobIds.size} reconsidered job IDs for user: ${userId.take(8)}...")
            jobIds
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error loading reconsidered job IDs: ${e.message}")
            emptySet()
        }
    }

    /**
     * 🚀 Save reconsidered job IDs for current user
     */
    suspend fun saveReconsideredJobIds(jobIds: Set<String>) = withContext(Dispatchers.IO) {
        try {
            val userId = authRepository.getCurrentUserId()
            if (userId == null) {
                Log.w(TAG, "No current user, cannot save reconsidered jobs")
                return@withContext
            }

            val userKey = "${KEY_RECONSIDERED_JOBS}_$userId"
            val jobIdsString = jobIds.joinToString(",")

            sharedPreferences.edit()
                .putString(userKey, jobIdsString)
                .putLong("${KEY_LAST_SYNC}_$userId", System.currentTimeMillis())
                .apply()

            Log.d(TAG, "✅ Saved ${jobIds.size} reconsidered job IDs for user: ${userId.take(8)}...")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error saving reconsidered job IDs: ${e.message}")
        }
    }

    /**
     * 🚀 Add a single job ID to reconsidered list
     */
    suspend fun addReconsideredJobId(jobId: String) = withContext(Dispatchers.IO) {
        try {
            val currentJobIds = loadReconsideredJobIds().toMutableSet()
            currentJobIds.add(jobId)
            saveReconsideredJobIds(currentJobIds)

            Log.d(TAG, "✅ Added job $jobId to reconsidered list")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error adding reconsidered job ID: ${e.message}")
        }
    }

    /**
     * 🚀 Add multiple job IDs to reconsidered list
     */
    suspend fun addMultipleReconsideredJobIds(jobIds: Collection<String>) = withContext(Dispatchers.IO) {
        if (jobIds.isEmpty()) return@withContext

        try {
            val currentJobIds = loadReconsideredJobIds().toMutableSet()
            currentJobIds.addAll(jobIds)
            saveReconsideredJobIds(currentJobIds)

            Log.d(TAG, "✅ Added ${jobIds.size} jobs to reconsidered list")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error adding multiple reconsidered job IDs: ${e.message}")
        }
    }

    /**
     * 🚀 Check if a job has been reconsidered
     */
    suspend fun isJobReconsidered(jobId: String): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val reconsideredJobIds = loadReconsideredJobIds()
            reconsideredJobIds.contains(jobId)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error checking if job is reconsidered: ${e.message}")
            false
        }
    }

    /**
     * 🚀 Clear all reconsidered job IDs for current user (for testing/reset)
     */
    suspend fun clearReconsideredJobIds() = withContext(Dispatchers.IO) {
        try {
            val userId = authRepository.getCurrentUserId()
            if (userId == null) {
                Log.w(TAG, "No current user, cannot clear reconsidered jobs")
                return@withContext
            }

            val userKey = "${KEY_RECONSIDERED_JOBS}_$userId"
            sharedPreferences.edit()
                .remove(userKey)
                .remove("${KEY_LAST_SYNC}_$userId")
                .apply()

            Log.d(TAG, "✅ Cleared all reconsidered job IDs for user: ${userId.take(8)}...")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error clearing reconsidered job IDs: ${e.message}")
        }
    }

    /**
     * 🚀 Get last sync timestamp for debugging
     */
    suspend fun getLastSyncTimestamp(): Long = withContext(Dispatchers.IO) {
        return@withContext try {
            val userId = authRepository.getCurrentUserId() ?: return@withContext 0L
            sharedPreferences.getLong("${KEY_LAST_SYNC}_$userId", 0L)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting last sync timestamp: ${e.message}")
            0L
        }
    }

    /**
     * 🚀 Export reconsidered job data for backup/migration
     */
    suspend fun exportReconsiderationData(): ReconsiderationBackupData = withContext(Dispatchers.IO) {
        return@withContext try {
            val userId = authRepository.getCurrentUserId()
            if (userId == null) {
                return@withContext ReconsiderationBackupData(emptySet(), 0L)
            }

            val jobIds = loadReconsideredJobIds()
            val lastSync = getLastSyncTimestamp()

            ReconsiderationBackupData(jobIds, lastSync)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error exporting reconsideration data: ${e.message}")
            ReconsiderationBackupData(emptySet(), 0L)
        }
    }

    /**
     * 🚀 Import reconsidered job data from backup/migration
     */
    suspend fun importReconsiderationData(backupData: ReconsiderationBackupData) = withContext(Dispatchers.IO) {
        try {
            saveReconsideredJobIds(backupData.reconsideredJobIds)

            val userId = authRepository.getCurrentUserId()
            if (userId != null && backupData.lastSyncTimestamp > 0) {
                sharedPreferences.edit()
                    .putLong("${KEY_LAST_SYNC}_$userId", backupData.lastSyncTimestamp)
                    .apply()
            }

            Log.d(TAG, "✅ Imported reconsideration data: ${backupData.reconsideredJobIds.size} jobs")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error importing reconsideration data: ${e.message}")
        }
    }

    /**
     * 🚀 Get statistics for debugging
     */
    suspend fun getStorageStats(): ReconsiderationStorageStats = withContext(Dispatchers.IO) {
        return@withContext try {
            val userId = authRepository.getCurrentUserId()
            if (userId == null) {
                return@withContext ReconsiderationStorageStats(0, 0L, false)
            }

            val jobIds = loadReconsideredJobIds()
            val lastSync = getLastSyncTimestamp()
            val hasData = jobIds.isNotEmpty()

            ReconsiderationStorageStats(jobIds.size, lastSync, hasData)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting storage stats: ${e.message}")
            ReconsiderationStorageStats(0, 0L, false)
        }
    }

    /**
     * 🚀 Log current storage state for debugging
     */
    suspend fun logStorageState() = withContext(Dispatchers.IO) {
        try {
            val stats = getStorageStats()
            val userId = authRepository.getCurrentUserId()?.take(8) ?: "unknown"

            Log.d(TAG, "=== RECONSIDERATION STORAGE STATE ===")
            Log.d(TAG, "User: $userId...")
            Log.d(TAG, "Reconsidered jobs count: ${stats.reconsideredJobsCount}")
            Log.d(TAG, "Last sync: ${if (stats.lastSyncTimestamp > 0) java.util.Date(stats.lastSyncTimestamp) else "Never"}")
            Log.d(TAG, "Has data: ${stats.hasData}")
            Log.d(TAG, "====================================")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error logging storage state: ${e.message}")
        }
    }
}

/**
 * 🚀 Data class for backup/export functionality
 */
data class ReconsiderationBackupData(
    val reconsideredJobIds: Set<String>,
    val lastSyncTimestamp: Long
)

/**
 * 🚀 Data class for storage statistics
 */
data class ReconsiderationStorageStats(
    val reconsideredJobsCount: Int,
    val lastSyncTimestamp: Long,
    val hasData: Boolean
)

/**
 * 🚀 ENHANCED ProcessedJobsRepository Integration
 *
 * Add this to your existing ProcessedJobsRepository:
 */

/*
// Add this to ProcessedJobsRepository.kt:

@Inject
lateinit var reconsiderationStorage: ReconsiderationStorageManager

// Add this method:
suspend fun initializeFromStorage() {
    try {
        val storedReconsideredIds = reconsiderationStorage.loadReconsideredJobIds()
        
        mutex.withLock {
            reconsideredJobsSet.clear()
            reconsideredJobsSet.addAll(storedReconsideredIds)
            _reconsideredJobIds.value = reconsideredJobsSet.toSet()
        }
        
        Log.d(TAG, "✅ Initialized from storage: ${storedReconsideredIds.size} reconsidered jobs")
    } catch (e: Exception) {
        Log.e(TAG, "❌ Error initializing from storage: ${e.message}")
    }
}

// Modify markJobAsReconsidered to save to storage:
suspend fun markJobAsReconsidered(jobId: String) {
    try {
        withTimeoutOrNull(1000) {
            mutex.withLock {
                reconsideredJobsSet.add(jobId)
                _reconsideredJobIds.value = reconsideredJobsSet.toSet()
                
                Log.d(TAG, "✅ Marked job $jobId as reconsidered - will not appear again")
            }
        } ?: Log.w(TAG, "markJobAsReconsidered timed out for job: $jobId")
        
        // Save to persistent storage
        reconsiderationStorage.addReconsideredJobId(jobId)
        
    } catch (e: Exception) {
        Log.e(TAG, "Error marking job as reconsidered $jobId: ${e.message}")
    }
}

// Modify markMultipleJobsAsReconsidered to save to storage:
suspend fun markMultipleJobsAsReconsidered(jobIds: Collection<String>) {
    if (jobIds.isEmpty()) return

    try {
        withTimeoutOrNull(2000) {
            mutex.withLock {
                reconsideredJobsSet.addAll(jobIds)
                _reconsideredJobIds.value = reconsideredJobsSet.toSet()
                
                Log.d(TAG, "✅ Marked ${jobIds.size} jobs as reconsidered")
            }
        } ?: Log.w(TAG, "markMultipleJobsAsReconsidered timed out")
        
        // Save to persistent storage
        reconsiderationStorage.addMultipleReconsideredJobIds(jobIds)
        
    } catch (e: Exception) {
        Log.e(TAG, "Error marking multiple jobs as reconsidered: ${e.message}")
    }
}
*/