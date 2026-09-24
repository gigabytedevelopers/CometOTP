@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Tasks

import com.gigabytedevelopersinc.app.cometOTP.Utilities.Settings
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Upgrading an old-style (SHA-256) credential hash must never leave the user without any stored
 * credential: the old hash may only go once the new credentials are stored.
 */
class AuthUpgradeTest {

    private val key = byteArrayOf(1, 2, 3, 4)
    private val newCredentials = Settings.NewAuthCredentials(key, "hash", 150000)

    /** What happened to the stored credentials, in order. */
    private val events = ArrayList<String>()

    private fun upgrade(derived: Settings.NewAuthCredentials?, saveSucceeds: Boolean) =
        upgradeAuthCredentials(
            "password",
            { derived },
            { events.add("save"); saveSucceeds },
            { events.add("removeOldHash") }
        )

    @Test
    fun keepsTheOldHashWhenTheNewCredentialsCannotBeDerived() {
        val result = upgrade(null, true)

        assertEquals(emptyList<String>(), events)
        assertTrue(result.authUpgradeFailed)
        assertNull(result.encryptionKey)
    }

    @Test
    fun keepsTheOldHashWhenTheNewCredentialsCannotBeStored() {
        val result = upgrade(newCredentials, false)

        assertEquals(listOf("save"), events)
        assertTrue(result.authUpgradeFailed)
        assertNull(result.encryptionKey)
    }

    @Test
    fun removesTheOldHashOnlyAfterStoringTheNewCredentials() {
        val result = upgrade(newCredentials, true)

        assertEquals(listOf("save", "removeOldHash"), events)
        assertFalse(result.authUpgradeFailed)
        assertArrayEquals(key, result.encryptionKey)
    }
}
