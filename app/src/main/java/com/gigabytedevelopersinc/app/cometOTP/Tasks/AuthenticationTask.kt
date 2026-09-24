@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Tasks

import android.content.Context
import android.util.Base64
import android.util.Log
import com.gigabytedevelopersinc.app.cometOTP.Tasks.AuthenticationTask.Result
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Constants.AuthMethod
import com.gigabytedevelopersinc.app.cometOTP.Utilities.EncryptionHelper
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Settings
import org.apache.commons.codec.binary.Hex
import org.apache.commons.codec.digest.DigestUtils
import java.security.NoSuchAlgorithmException
import java.security.spec.InvalidKeySpecException

/**
 * Project - CometOTP
 * Created with Android Studio
 * Company: Gigabyte Developers
 * User: Emmanuel Nwokoma
 * Title: Founder and CEO
 * Day: Tuesday, 09
 * Month: February
 * Year: 2021
 * Date: 09 Feb, 2021
 * Time: 4:04 PM
 * Desc: AuthenticationTask
 **/
class AuthenticationTask
/**
 * @param context Context to be used to query settings (the application Context will be used to avoid memory leaks).
 * @param isAuthUpgrade true if this is an authentication upgrade and new credentials should be saved, false if this is just confirmation.
 * @param existingAuthCredentials The existing hashed authentication credentials that we have stored.
 * @param plainPassword The plaintext user-entered password to check authentication with. */
constructor(
    context: Context,
    private val isAuthUpgrade: Boolean,
    private val existingAuthCredentials: String,
    private val plainPassword: String
) : UiBasedBackgroundTask<Result>(Result.failure()) {

    private val settings: Settings

    init {
        val applicationContext = context.applicationContext
        this.settings = Settings(applicationContext)
    }

    override fun doInBackground(): Result {
        return if (isAuthUpgrade) {
            upgradeAuthentication()
        } else {
            confirmAuthentication()
        }
    }

    private fun upgradeAuthentication(): Result {
        val hashedPassword = String(Hex.encodeHex(DigestUtils.sha256(plainPassword)))
        if (hashedPassword != existingAuthCredentials)
            return Result.failure()

        val authMethod = settings.authMethod
        return upgradeAuthCredentials(
            plainPassword,
            settings::generateAuthCredentials,
            { settings.saveAuthCredentials(it, null) },
            {
                if (authMethod == AuthMethod.PASSWORD)
                    settings.removeAuthPasswordHash()
                else if (authMethod == AuthMethod.PIN)
                    settings.removeAuthPINHash()
            }
        )
    }

    private fun confirmAuthentication(): Result {
        try {
            val credentials = EncryptionHelper.generatePBKDF2Credentials(plainPassword, settings.salt, settings.iterations)
            val passwordArray = Base64.decode(existingAuthCredentials, Base64.URL_SAFE)

            if (passwordArray.contentEquals(credentials.password)) {
                return Result.success(credentials.key)
            }
            return Result.failure()
        } catch (e: NoSuchAlgorithmException) {
            Log.e("AuthenticationTask", "Problem decoding password", e)
            return Result.failure()
        } catch (e: InvalidKeySpecException) {
            Log.e("AuthenticationTask", "Problem decoding password", e)
            return Result.failure()
        } catch (e: IllegalArgumentException) {
            Log.e("AuthenticationTask", "Problem decoding password", e)
            return Result.failure()
        }
    }

    /**
     * @property authUpgradeFailed The password matched the old-style hash, but it could not be
     * replaced with new credentials; the old hash is still stored and the upgrade is retried on
     * the next unlock.
     */
    class Result(
        @JvmField val encryptionKey: ByteArray?,
        @JvmField val authUpgradeFailed: Boolean
    ) {
        companion object {
            @JvmStatic
            fun success(encryptionKey: ByteArray?): Result {
                return Result(encryptionKey, false)
            }

            @JvmStatic
            fun upgradeFailure(): Result {
                return Result(null, true)
            }

            @JvmStatic
            fun failure(): Result {
                return Result(null, false)
            }
        }
    }
}

/**
 * Replaces an old form of the stored password or PIN with PBKDF2 credentials derived from
 * [plainPassword]: the old-style (SHA-256) hash of a user who has just entered the matching
 * password, or a password stored in plain text by much older versions ([Settings]). The old form
 * is removed only once the new credentials are stored: removing it first would leave no
 * credential at all whenever deriving or storing the new one fails, and the unlock screen would
 * then have nothing to check the next password against.
 */
internal fun upgradeAuthCredentials(
    plainPassword: String,
    generate: (String) -> Settings.NewAuthCredentials?,
    save: (Settings.NewAuthCredentials) -> Boolean,
    removeOldHash: () -> Unit
): Result {
    val credentials = generate(plainPassword)
    if (credentials == null || !save(credentials))
        return Result.upgradeFailure()

    removeOldHash()
    return Result.success(credentials.key)
}
