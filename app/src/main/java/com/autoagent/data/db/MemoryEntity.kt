package com.autoagent.personal.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "memory",
    indices = [Index(value = ["category", "key"], unique = true)]
)
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: String,
    val key: String,
    val value: String,
    val usageCount: Int = 0,
    val lastUsed: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memory WHERE category = :category ORDER BY usageCount DESC")
    fun getAllInCategory(category: String): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memory WHERE category = :category AND LOWER(key) LIKE :query ORDER BY usageCount DESC")
    suspend fun search(category: String, query: String): List<MemoryEntity>

    @Query("SELECT * FROM memory WHERE category = :category AND key = :key LIMIT 1")
    suspend fun getByKey(category: String, key: String): MemoryEntity?

    @Query("SELECT * FROM memory WHERE category = :category ORDER BY usageCount DESC LIMIT :limit")
    suspend fun getTopUsed(category: String, limit: Int): List<MemoryEntity>

    @Upsert
    suspend fun upsert(entry: MemoryEntity)

    @Query("DELETE FROM memory WHERE category = :category AND key = :key")
    suspend fun delete(category: String, key: String)

    @Query("DELETE FROM memory WHERE category = :category")
    suspend fun deleteCategory(category: String)

    @Query("DELETE FROM memory")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM memory")
    suspend fun getCount(): Int
}
