package com.ollie.iptv.data

import android.content.Context
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Stores a salted PBKDF2 hash only. The PIN itself is never persisted.
 * This is a local parental-control barrier, not a replacement for DRM.
 */
class ParentalPinVault(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "ollie_parental_control", Context.MODE_PRIVATE
    )

    fun hasPin(): Boolean =
        preferences.contains(KEY_SALT) && preferences.contains(KEY_HASH)

    fun setPin(pin: String) {
        require(pin.matches(Regex("""\d{4,8}"""))) { "PIN must contain 4 to 8 digits." }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = ParentalPinHasher.hash(pin, salt)
        preferences.edit()
            .putString(KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(KEY_HASH, Base64.encodeToString(hash, Base64.NO_WRAP))
            .apply()
    }

    fun verify(pin: String): Boolean {
        val saltText = preferences.getString(KEY_SALT, null) ?: return false
        val hashText = preferences.getString(KEY_HASH, null) ?: return false
        return runCatching {
            val salt = Base64.decode(saltText, Base64.NO_WRAP)
            val expected = Base64.decode(hashText, Base64.NO_WRAP)
            MessageDigest.isEqual(expected, ParentalPinHasher.hash(pin, salt))
        }.getOrDefault(false)
    }

    private companion object {
        const val KEY_SALT = "pin_salt"
        const val KEY_HASH = "pin_hash"
    }
}

object ParentalPinHasher {
    private const val ITERATIONS = 120_000
    private const val OUTPUT_BYTES = 32

    /** PBKDF2-HMAC-SHA256 implemented with the Android-supported HmacSHA256 primitive. */
    fun hash(pin: String, salt: ByteArray, iterations: Int = ITERATIONS): ByteArray {
        require(iterations > 0)
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(pin.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val firstBlock = ByteArray(salt.size + 4)
        salt.copyInto(firstBlock)
        firstBlock[firstBlock.lastIndex] = 1
        var u = mac.doFinal(firstBlock)
        val result = u.copyOf(OUTPUT_BYTES)
        repeat(iterations - 1) {
            u = mac.doFinal(u)
            for (index in result.indices) {
                result[index] = (result[index].toInt() xor u[index].toInt()).toByte()
            }
        }
        return result
    }
}
