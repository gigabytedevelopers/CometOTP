@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Activities

import com.gigabytedevelopersinc.app.cometOTP.Utilities.Constants.AuthMethod
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Constants.EncryptionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A password or PIN lock without a stored credential used to unlock without asking for anything.
 */
class UnlockActionTest {

    @Test
    fun asksForAStoredCredential() {
        for (method in listOf(AuthMethod.PASSWORD, AuthMethod.PIN))
            for (encryption in EncryptionType.entries)
                for (databaseExists in listOf(true, false))
                    assertEquals(UnlockAction.ASK_CREDENTIAL, unlockActionFor(method, encryption, true, databaseExists))
    }

    @Test
    fun neverUnlocksAPasswordOrPinLockWithoutACredential() {
        for (method in listOf(AuthMethod.PASSWORD, AuthMethod.PIN))
            for (encryption in EncryptionType.entries)
                for (databaseExists in listOf(true, false)) {
                    val action = unlockActionFor(method, encryption, false, databaseExists)
                    assertTrue("$method / $encryption / database $databaseExists: $action",
                        action == UnlockAction.SET_UP_CREDENTIAL || action == UnlockAction.LOCKED_OUT)
                }
    }

    @Test
    fun keyStoreEncryptionSetsUpANewCredential() {
        assertEquals(UnlockAction.SET_UP_CREDENTIAL, unlockActionFor(AuthMethod.PASSWORD, EncryptionType.KEYSTORE, false, true))
        assertEquals(UnlockAction.SET_UP_CREDENTIAL, unlockActionFor(AuthMethod.PIN, EncryptionType.KEYSTORE, false, true))
    }

    @Test
    fun passwordEncryptionKeepsAnExistingDatabaseLocked() {
        // A new credential would derive a different key than the one the database is encrypted with.
        assertEquals(UnlockAction.LOCKED_OUT, unlockActionFor(AuthMethod.PASSWORD, EncryptionType.PASSWORD, false, true))
        assertEquals(UnlockAction.LOCKED_OUT, unlockActionFor(AuthMethod.PIN, EncryptionType.PASSWORD, false, true))
    }

    @Test
    fun passwordEncryptionWithoutADatabaseSetsUpANewCredential() {
        assertEquals(UnlockAction.SET_UP_CREDENTIAL, unlockActionFor(AuthMethod.PASSWORD, EncryptionType.PASSWORD, false, false))
    }

    @Test
    fun otherLockMethodsHaveNothingToUnlockHere() {
        for (method in listOf(AuthMethod.NONE, AuthMethod.DEVICE))
            for (encryption in EncryptionType.entries)
                for (hasCredential in listOf(true, false))
                    assertEquals(UnlockAction.NOTHING_TO_UNLOCK, unlockActionFor(method, encryption, hasCredential, true))
    }
}
