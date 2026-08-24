package com.autoagent.personal.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PinDao {
    @Query("SELECT * FROM pin WHERE id = 1")
    suspend fun getPin(): PinEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePin(pin: PinEntity)

    @Query("UPDATE pin SET biometricEnabled = :enabled WHERE id = 1")
    suspend fun setBiometric(enabled: Boolean)
}
