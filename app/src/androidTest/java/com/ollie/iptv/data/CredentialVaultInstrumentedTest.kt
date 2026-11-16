package com.ollie.iptv.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import android.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CredentialVaultInstrumentedTest {
    @Test fun encryptsAndDecryptsCredentialOnAndroidKeystore() {
        val secret = "user@example.test:p@ssword&token"
        val encrypted = CredentialVault.encrypt(secret)
        assertFalse(encrypted.contains(secret))
        assertEquals(secret, CredentialVault.decrypt(encrypted))
    }

    @Test fun leavesEmptyAndLegacyPlaintextValuesReadable() {
        assertEquals("", CredentialVault.encrypt(""))
        assertEquals("", CredentialVault.decrypt(""))
        assertEquals("legacy-user", CredentialVault.decrypt("legacy-user"))
    }

    @Test fun rejectsTamperedCiphertext() {
        val encrypted = CredentialVault.encrypt("sensitive")
        val payload = encrypted.removePrefix("keystore:v1:")
        val bytes = Base64.decode(payload, Base64.NO_WRAP)
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
        val tampered = "keystore:v1:" + Base64.encodeToString(bytes, Base64.NO_WRAP)
        assertThrows(Exception::class.java) { CredentialVault.decrypt(tampered) }
    }
}
