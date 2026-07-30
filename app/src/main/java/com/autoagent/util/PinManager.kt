package com.autoagent.util

import com.autoagent.data.db.PinDao
import com.autoagent.data.db.PinEntity
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PinManager @Inject constructor(
    private val pinDao: PinDao
) {
    companion object {
        const val PIN_LENGTH = 10
    }

    // =========================================
    // CHECK IF PIN IS SET
    // =========================================
    suspend fun isPinSetup(): Boolean {
        return pinDao.getPin()?.setupComplete == true
    }

    // =========================================
    // SET PIN (first time or change)
    // =========================================
    suspend fun setupPin(pin: String): Result<Unit> {
        if (pin.length != PIN_LENGTH) {
            return Result.failure(Exception("PIN exactly $PIN_LENGTH digits hona chahiye"))
        }
        if (!pin.all { it.isDigit() }) {
            return Result.failure(Exception("PIN sirf numbers hone chahiye"))
        }
        val hash = hashPin(pin)
        pinDao.savePin(PinEntity(
            pinHash = hash,
            setupComplete = true,
            lastChangedAt = System.currentTimeMillis()
        ))
        return Result.success(Unit)
    }

    // =========================================
    // VERIFY PIN
    // =========================================
    suspend fun verifyPin(pin: String): Boolean {
        if (pin.length != PIN_LENGTH || !pin.all { it.isDigit() }) return false
        val stored = pinDao.getPin() ?: return false
        return hashPin(pin) == stored.pinHash
    }

    // =========================================
    // CHANGE PIN (requires old PIN)
    // =========================================
    suspend fun changePin(oldPin: String, newPin: String): Result<Unit> {
        if (!verifyPin(oldPin)) {
            return Result.failure(Exception("Galat purana PIN"))
        }
        return setupPin(newPin)
    }

    // =========================================
    // SHA-256 HASH
    // =========================================
    private fun hashPin(pin: String): String {
        val salt = "AutoAgent_Shield_2024"  // App-specific salt
        val input = "$salt:$pin"
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    suspend fun isBiometricEnabled(): Boolean {
        return pinDao.getPin()?.biometricEnabled == true
    }

    suspend fun setBiometric(enabled: Boolean) {
        pinDao.setBiometric(enabled)
    }
}
