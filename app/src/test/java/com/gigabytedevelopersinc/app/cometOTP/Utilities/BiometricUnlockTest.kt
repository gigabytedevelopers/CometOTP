@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Utilities

import androidx.biometric.BiometricManager
import com.gigabytedevelopersinc.app.cometOTP.Utilities.BiometricUnlock.Availability
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Constants.AuthMethod
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Constants.EncryptionType
import org.junit.Assert.assertEquals
import org.junit.Test

class BiometricUnlockTest {

    private val ready = BiometricManager.BIOMETRIC_SUCCESS

    @Test
    fun aStrongBiometricCanStandInForAPinOrPasswordWithKeyStoreEncryption() {
        for (method in listOf(AuthMethod.PIN, AuthMethod.PASSWORD))
            assertEquals(method.name, Availability.AVAILABLE,
                    BiometricUnlock.availabilityFor(method, EncryptionType.KEYSTORE, ready))
    }

    @Test
    fun withoutAPinOrPasswordThereIsNothingToStandInFor() {
        // The device lock already is the system's own biometric or screen lock.
        for (method in listOf(AuthMethod.NONE, AuthMethod.DEVICE))
            assertEquals(method.name, Availability.NEEDS_CREDENTIAL,
                    BiometricUnlock.availabilityFor(method, EncryptionType.KEYSTORE, ready))
    }

    /** The database key is made from the PIN or password then, and is never stored. */
    @Test
    fun notWhileTheAccountsAreEncryptedWithThePinOrPassword() {
        for (method in listOf(AuthMethod.PIN, AuthMethod.PASSWORD))
            assertEquals(method.name, Availability.NEEDS_KEYSTORE_ENCRYPTION,
                    BiometricUnlock.availabilityFor(method, EncryptionType.PASSWORD, ready))
    }

    @Test
    fun thePhoneMustHaveAStrongBiometricEnrolled() {
        assertEquals(Availability.NONE_ENROLLED, BiometricUnlock.availabilityFor(AuthMethod.PIN, EncryptionType.KEYSTORE,
                BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED))
        for (status in listOf(BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE, BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE,
                BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED, BiometricManager.BIOMETRIC_STATUS_UNKNOWN))
            assertEquals("status $status", Availability.UNSUPPORTED,
                    BiometricUnlock.availabilityFor(AuthMethod.PIN, EncryptionType.KEYSTORE, status))
    }

    @Test
    fun theAppsOwnReasonsComeFirst() {
        // What can be changed in CometOTP is said before what needs the phone's settings.
        assertEquals(Availability.NEEDS_CREDENTIAL, BiometricUnlock.availabilityFor(AuthMethod.NONE, EncryptionType.KEYSTORE,
                BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED))
        assertEquals(Availability.NEEDS_KEYSTORE_ENCRYPTION, BiometricUnlock.availabilityFor(AuthMethod.PIN, EncryptionType.PASSWORD,
                BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE))
    }
}
