@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Utilities

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.gigabytedevelopersinc.app.cometOTP.R
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Constants.AuthMethod
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Constants.EncryptionType

/**
 * Unlocking with a strong biometric (Android's Class 3: fingerprints, and face unlock only where
 * it is secure hardware) instead of typing the app's PIN or password, which stays the way in when
 * the biometric is cancelled or fails.
 *
 * Only while the accounts are encrypted with the Android KeyStore: then the PIN or password only
 * guards the screen, and a biometric can stand in for it. With PIN or password encryption the
 * database key is derived from the credential itself and is never stored, so only the
 * credential can open it.
 */
object BiometricUnlock {

    /** Why biometric unlock can or cannot be used now. */
    enum class Availability {
        AVAILABLE,
        /** There is no PIN or password to stand in for. */
        NEEDS_CREDENTIAL,
        /** The accounts are encrypted with the PIN or password itself. */
        NEEDS_KEYSTORE_ENCRYPTION,
        /** The phone can, but no strong biometric is enrolled. */
        NONE_ENROLLED,
        /** The phone has no strong biometric, or it cannot be used now. */
        UNSUPPORTED
    }

    /**
     * [biometricStatus] is BiometricManager.canAuthenticate(BIOMETRIC_STRONG). The app's own
     * reasons come first: they are what the user can do something about in CometOTP.
     */
    fun availabilityFor(method: AuthMethod, encryption: EncryptionType, biometricStatus: Int): Availability {
        return when {
            method != AuthMethod.PIN && method != AuthMethod.PASSWORD -> Availability.NEEDS_CREDENTIAL
            encryption != EncryptionType.KEYSTORE -> Availability.NEEDS_KEYSTORE_ENCRYPTION
            biometricStatus == BiometricManager.BIOMETRIC_SUCCESS -> Availability.AVAILABLE
            biometricStatus == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> Availability.NONE_ENROLLED
            else -> Availability.UNSUPPORTED
        }
    }

    fun availability(context: Context, settings: Settings): Availability {
        return availabilityFor(settings.authMethod, settings.encryption,
                BiometricManager.from(context).canAuthenticate(BIOMETRIC_STRONG))
    }

    /** Whether the unlock screen offers a biometric: switched on, and possible now. */
    fun isOffered(context: Context, settings: Settings): Boolean {
        return settings.biometricUnlock && availability(context, settings) == Availability.AVAILABLE
    }

    /**
     * Asks for a strong biometric, with [negativeText] (e.g. "Use PIN") to go back to the
     * credential. [onSuccess] runs once it is recognised; [onError] when the prompt ends without,
     * cancelled or locked out by too many tries (a single unrecognised try keeps it open).
     */
    fun prompt(activity: FragmentActivity, negativeText: CharSequence, onSuccess: () -> Unit,
               onError: (code: Int, message: CharSequence) -> Unit = { _, _ -> }) {
        val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onError(errorCode, errString)
                })
        val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle(activity.getString(R.string.security_biometric_title))
                .setSubtitle(activity.getString(R.string.auth_biometric_subtitle))
                .setAllowedAuthenticators(BIOMETRIC_STRONG)
                .setNegativeButtonText(negativeText)
                .build()
        prompt.authenticate(info)
    }
}
