package com.autoagent.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "app_cache")
data class AppCacheEntity(
    @PrimaryKey val packageName: String,
    val appName: String,
    val versionName: String,
    val category: String,
    val canLaunch: Boolean,
    val installDate: Long,
    val lastUpdated: Long,
    val launchActivity: String?,
    val scannedAt: Long = System.currentTimeMillis()
)

@Dao
interface AppCacheDao {
    @Query("SELECT * FROM app_cache ORDER BY appName ASC")
    fun getAllApps(): Flow<List<AppCacheEntity>>

    @Query("SELECT * FROM app_cache ORDER BY appName ASC")
    suspend fun getAllAppsOnce(): List<AppCacheEntity>

    @Query("SELECT COUNT(*) FROM app_cache")
    suspend fun getCount(): Int

    @Query("SELECT MAX(scannedAt) FROM app_cache")
    suspend fun getLastScanTime(): Long?

    @Upsert
    suspend fun upsertAll(apps: List<AppCacheEntity>)

    @Query("DELETE FROM app_cache")
    suspend fun deleteAll()
}
