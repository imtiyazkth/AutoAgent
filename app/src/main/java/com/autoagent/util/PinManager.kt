package com.autoagent.util

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.autoagent.data.db.PinDao
import com.autoagent.data.db.PinEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PinManager @Inject constructor(
    private val pinDao: PinDao,
    @ApplicationContext private val context: Context
) {
    companion object {
        const val PIN_MIN = 4
        const val PIN_MAX = 10
        private const val PREFS_NAME = "autoagent_secure_prefs"
        private const val KEY_SALT = "pin_salt"
    }

    // =========================================
    // PER-DEVICE RANDOM SALT (generated once, stored in private prefs)
    // =========================================
    private fun getOrCreateSalt(): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var salt: String = prefs.getString(KEY_SALT, null) ?: ""
        if (salt.isEmpty()) {
            val bytes = ByteArray(32)
            SecureRandom().nextBytes(bytes)
            salt = Base64.encodeToString(bytes, Base64.NO_WRAP)
            prefs.edit().putString(KEY_SALT, salt).apply()
        }
        return salt
    }

    private fun hashPin(pin: String): String {
        val salt = getOrCreateSalt()
        val input = "$salt:AutoAgent:$pin"
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    suspend fun isPinSetup(): Boolean = pinDao.getPin()?.setupComplete == true

    suspend fun setupPin(pin: String): Result<Unit> {
        if (pin.length !in PIN_MIN..PIN_MAX)
            return Result.failure(Exception("PIN 4 se 10 digits ka hona chahiye"))
        if (!pin.all { it.isDigit() })
            return Result.failure(Exception("PIN sirf numbers hone chahiye"))
        val hash = hashPin(pin)
        pinDao.savePin(PinEntity(
            pinHash = hash,
            setupComplete = true,
            lastChangedAt = System.currentTimeMillis()
        ))
        return Result.success(Unit)
    }

    suspend fun verifyPin(pin: String): Boolean {
        if (pin.length !in PIN_MIN..PIN_MAX || !pin.all { it.isDigit() }) return false
        val stored = pinDao.getPin() ?: return false
        return hashPin(pin) == stored.pinHash
    }

    suspend fun changePin(oldPin: String, newPin: String): Result<Unit> {
        if (!verifyPin(oldPin)) return Result.failure(Exception("Galat purana PIN"))
        return setupPin(newPin)
    }

    suspend fun isBiometricEnabled(): Boolean = pinDao.getPin()?.biometricEnabled == true
    suspend fun setBiometric(enabled: Boolean) = pinDao.setBiometric(enabled)
}
