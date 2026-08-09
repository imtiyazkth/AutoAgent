package com.autoagent.personal.memory

import android.content.Context
import android.util.Log
import com.autoagent.personal.data.db.MemoryDao
import com.autoagent.personal.data.db.MemoryEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * MemoryEngine — local-first preference and pattern memory.
 *
 * Categories:
 *   CONTACT_ALIAS      — "Sipun" → "+91XXXXXXXXXX"
 *   MESSAGE_TEMPLATE   — reusable messages
 *   APP_PREFERENCE     — preferred app for a task
 *   WORKFLOW_TEMPLATE  — saved multi-step flows
 *   ERROR_PATTERN      — known failure patterns
 *   TRUSTED_APP        — user-verified safe apps
 *   LAST_COMMAND       — most recent command text
 */
@Singleton
class MemoryEngine @Inject constructor(
    private val memoryDao: MemoryDao,
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "MemoryEngine"

        const val CAT_CONTACT   = "CONTACT_ALIAS"
        const val CAT_MESSAGE   = "MESSAGE_TEMPLATE"
        const val CAT_APP_PREF  = "APP_PREFERENCE"
        const val CAT_WORKFLOW  = "WORKFLOW_TEMPLATE"
        const val CAT_ERROR     = "ERROR_PATTERN"
        const val CAT_TRUSTED   = "TRUSTED_APP"
        const val CAT_LAST_CMD  = "LAST_COMMAND"
    }

    /** Store or update a memory entry */
    suspend fun remember(category: String, key: String, value: String) {
        try {
            val existing = memoryDao.getByKey(category, key)
            if (existing != null) {
                memoryDao.upsert(existing.copy(
                    value = value,
                    usageCount = existing.usageCount + 1,
                    lastUsed = System.currentTimeMillis()
                ))
            } else {
                memoryDao.upsert(MemoryEntity(
                    category = category, key = key, value = value
                ))
            }
            Log.d(TAG, "Remembered [$category] $key")
        } catch (e: Exception) {
            Log.e(TAG, "remember error: ${e.message}")
        }
    }

    /** Recall exact key */
    suspend fun recall(category: String, key: String): String? {
        return try {
            val entry = memoryDao.getByKey(category, key) ?: return null
            memoryDao.upsert(entry.copy(
                usageCount = entry.usageCount + 1,
                lastUsed = System.currentTimeMillis()
            ))
            entry.value
        } catch (e: Exception) { null }
    }

    /** Search all keys in a category by partial match */
    suspend fun search(category: String, query: String): List<MemoryEntity> {
        return try {
            memoryDao.search(category, "%${query.lowercase()}%")
        } catch (e: Exception) { emptyList() }
    }

    /** Get all entries in a category */
    fun getAll(category: String): Flow<List<MemoryEntity>> =
        memoryDao.getAllInCategory(category)

    /** Forget a specific memory */
    suspend fun forget(category: String, key: String) {
        try {
            memoryDao.delete(category, key)
            Log.d(TAG, "Forgot [$category] $key")
        } catch (e: Exception) { Log.e(TAG, "forget error: ${e.message}") }
    }

    /** Forget all memories in a category */
    suspend fun forgetAll(category: String) {
        try { memoryDao.deleteCategory(category) } catch (e: Exception) {}
    }

    /** Resolve a contact name to phone or contact info */
    suspend fun resolveContact(name: String): String? =
        recall(CAT_CONTACT, name.trim().lowercase())

    /** Save a contact alias */
    suspend fun saveContactAlias(name: String, phoneOrId: String) =
        remember(CAT_CONTACT, name.trim().lowercase(), phoneOrId)

    /** Save last command for history */
    suspend fun saveLastCommand(command: String) =
        remember(CAT_LAST_CMD, "latest", command)

    /** Get most-used entries (for suggestions) */
    suspend fun getTopEntries(category: String, limit: Int = 5): List<MemoryEntity> =
        try { memoryDao.getTopUsed(category, limit) } catch (e: Exception) { emptyList() }
}
