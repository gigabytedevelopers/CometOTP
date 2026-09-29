@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Utilities

import android.accounts.Account
import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.annotation.WorkerThread
import com.gigabytedevelopersinc.app.cometOTP.R
import com.gigabytedevelopersinc.app.cometOTP.Utilities.BackupDestination.DestinationException
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.ClearTokenRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.RevokeAccessRequest
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Access to the user's Google Drive, limited to the files this app creates (drive.file). Google
 * Play services keeps the grant and hands out access tokens; the app only remembers which account
 * it was given.
 *
 * The OAuth client is matched by package name and signing certificate in the Google Cloud project,
 * so nothing identifying it is in the code. A build whose certificate is not registered there
 * fails with DEVELOPER_ERROR.
 */
object DriveAuth {
    private const val SCOPE_DRIVE_FILE = "https://www.googleapis.com/auth/drive.file"
    private const val ACCOUNT_TYPE = "com.google"
    private const val TIMEOUT_SECONDS = 30L

    private fun scopes() = listOf(Scope(SCOPE_DRIVE_FILE))

    private fun request(email: String?): AuthorizationRequest {
        val builder = AuthorizationRequest.builder().setRequestedScopes(scopes())
        if (!email.isNullOrEmpty())
            builder.setAccount(Account(email, ACCOUNT_TYPE))
        return builder.build()
    }

    /**
     * Asks for access, from an activity. The result either carries a token (already granted) or,
     * when [AuthorizationResult.hasResolution], a pending intent for the account picker and
     * consent screen; its result goes to [resultFromIntent].
     */
    fun authorize(activity: Activity): Task<AuthorizationResult> {
        return Identity.getAuthorizationClient(activity).authorize(request(null))
    }

    @Throws(ApiException::class)
    fun resultFromIntent(activity: Activity, data: Intent?): AuthorizationResult {
        return Identity.getAuthorizationClient(activity).getAuthorizationResultFromIntent(data)
    }

    /** The account a grant was given for, to show and to ask for the same one later. */
    @Suppress("DEPRECATION")   // toGoogleSignInAccount is the only way the result names the account
    fun accountEmail(result: AuthorizationResult): String? {
        return result.toGoogleSignInAccount()?.email
    }

    /**
     * An access token without any UI, for background backups. Throws when the user has to connect
     * again (the grant was revoked, or the account is gone) or Google Play services cannot be
     * reached right now.
     */
    @WorkerThread
    @Throws(DestinationException::class)
    fun silentToken(context: Context, email: String): String {
        val result = try {
            Tasks.await(Identity.getAuthorizationClient(context).authorize(request(email)), TIMEOUT_SECONDS, TimeUnit.SECONDS)
        } catch (e: ExecutionException) {
            throw exceptionFor(e.cause)
        } catch (e: TimeoutException) {
            throw DestinationException(R.string.backup_error_drive_unreachable, true, cause = e)
        } catch (e: InterruptedException) {
            throw DestinationException(R.string.backup_error_drive_unreachable, true, cause = e)
        }

        if (result.hasResolution())
            throw DestinationException(R.string.backup_error_drive_reconnect, false)

        return result.accessToken
            ?: throw DestinationException(R.string.backup_error_drive_reconnect, false)
    }

    /** Forgets a token Drive refused, so the next [silentToken] fetches a fresh one. */
    @WorkerThread
    fun clearToken(context: Context, token: String) {
        try {
            Tasks.await(Identity.getAuthorizationClient(context)
                .clearToken(ClearTokenRequest.builder().setToken(token).build()), TIMEOUT_SECONDS, TimeUnit.SECONDS)
        } catch (e: Exception) {
            // The retry then fails the same way and is reported from there.
        }
    }

    /** Withdraws the app's access to [email]'s Drive. */
    fun revoke(context: Context, email: String): Task<Void> {
        val request = RevokeAccessRequest.builder()
            .setAccount(Account(email, ACCOUNT_TYPE))
            .setScopes(scopes())
            .build()
        return Identity.getAuthorizationClient(context).revokeAccess(request)
    }

    /** A message for a failed authorization, e.g. from [authorize]'s failure listener. */
    fun messageFor(error: Throwable?): Int {
        return exceptionFor(error).messageId
    }

    private fun exceptionFor(error: Throwable?): DestinationException {
        val status = (error as? ApiException)?.statusCode
        return when (status) {
            CommonStatusCodes.NETWORK_ERROR, CommonStatusCodes.TIMEOUT, CommonStatusCodes.INTERNAL_ERROR ->
                DestinationException(R.string.backup_error_drive_unreachable, true, cause = error)
            CommonStatusCodes.DEVELOPER_ERROR ->
                DestinationException(R.string.backup_error_drive_not_configured, false, cause = error)
            else ->
                DestinationException(R.string.backup_error_drive_reconnect, false, cause = error)
        }
    }
}
