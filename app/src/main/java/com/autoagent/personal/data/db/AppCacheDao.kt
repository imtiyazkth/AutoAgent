package com.autoagent.personal.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AppCacheDao {
    @Query("SELECT * FROM app_cache ORDER BY appName ASC")
    fun observeAll(): Flow<List<AppCacheEntity>>

    @Query("SELECT * FROM app_cache ORDER BY appName ASC")
    suspend fun getAll(): List<AppCacheEntity>

    @Query("SELECT * FROM app_cache WHERE packageName = :pkg LIMIT 1")
    suspend fun getByPackage(pkg: String): AppCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(apps: List<AppCacheEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(app: AppCacheEntity)

    @Query("DELETE FROM app_cache")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM app_cache")
    suspend fun count(): Int
}
