package com.autoagent.personal.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pin")
data class PinEntity(
    @PrimaryKey
    val id: Int = 1,
    val pinHash: String = "",
    val setupComplete: Boolean = false,
    val biometricEnabled: Boolean = false,
    val lastChangedAt: Long = System.currentTimeMillis()
)
