package com.autoagent.personal.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memory WHERE category = :category AND key = :key LIMIT 1")
    suspend fun getByKey(category: String, key: String): MemoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: MemoryEntity)

    @Query("SELECT * FROM memory WHERE category = :category AND key LIKE :query")
    suspend fun search(category: String, query: String): List<MemoryEntity>

    @Query("SELECT * FROM memory WHERE category = :category ORDER BY lastUsed DESC")
    fun getAllInCategory(category: String): Flow<List<MemoryEntity>>

    @Query("DELETE FROM memory WHERE category = :category AND key = :key")
    suspend fun delete(category: String, key: String)

    @Query("DELETE FROM memory WHERE category = :category")
    suspend fun deleteCategory(category: String)

    @Query("SELECT * FROM memory WHERE category = :category ORDER BY usageCount DESC LIMIT :limit")
    suspend fun getTopUsed(category: String, limit: Int): List<MemoryEntity>
}
