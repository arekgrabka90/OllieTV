package com.ollie.iptv.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ParentalPinVaultTest {
    @Test fun storesOnlySaltedHashAndVerifiesPin() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences("ollie_parental_control", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()

        val vault = ParentalPinVault(context)
        vault.setPin("4826")

        assertTrue(vault.hasPin())
        assertTrue(vault.verify("4826"))
        assertFalse(vault.verify("1111"))
        assertFalse(preferences.all.values.any { it.toString().contains("4826") })
    }
}
