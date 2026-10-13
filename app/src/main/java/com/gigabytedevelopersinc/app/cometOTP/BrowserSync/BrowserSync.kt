@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.BrowserSync

import android.content.Context
import com.gigabytedevelopersinc.app.cometOTP.R
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Constants
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Settings
import java.io.File
import javax.crypto.SecretKey

/**
 * Keeps paired browsers' vaults current (protocol §7.1). Uploads happen only while CometOTP is
 * unlocked, after a change and when it is opened, because the payload needs the database key; a
 * failed one is tried again by the caller's queue while the app is open, and otherwise at the next
 * change or unlock. Nothing that could open a vault, the write token included, ever leaves
 * [BrowserStore]'s encrypted file for a background job.
 */
object BrowserSync {

    /** Whether this build includes browser sync (debug builds only, until it ships). */
    fun isAvailable(context: Context): Boolean = context.resources.getBoolean(R.bool.browser_sync_available)

    /** Whether any browser has been paired; cheap enough to check after every save. */
    fun hasBrowsers(context: Context): Boolean = File(context.filesDir, Constants.FILENAME_BROWSERS).exists()

    /** Syncing pauses while CometOTP has no lock: a copy of the keys must not leave an unlocked phone. */
    fun isPaused(settings: Settings): Boolean = settings.authMethod == Constants.AuthMethod.NONE

    /**
     * Uploads to every paired browser whose copy is out of date, on a background thread. With
     * [cleanUpPending] (when CometOTP is opened), first removes pairings that can no longer
     * finish and deletes their vaults. Returns false when an upload should be tried again later.
     */
    fun sync(
        context: Context,
        services: List<SyncService>,
        key: SecretKey,
        cleanUpPending: Boolean,
        relay: Relay = RelayClient(),
    ): Boolean {
        if (isPaused(Settings(context)))
            return true
        if (cleanUpPending) {
            // Best effort: a vault never downloaded is deleted by the relay within a week anyway.
            for (stale in BrowserStore.removeStalePending(context, key))
                relay.delete(stale.relay, stale.vaultId, stale.writeToken)
        }

        val phone = VaultPayload.phoneName(context)
        val uploader = VaultUploader(relay)
        var complete = true
        for (browser in BrowserStore.load(context, key)) {
            val outcome = uploader.upload(browser, phone, services)
            when {
                outcome.browser == null -> BrowserStore.remove(context, browser.vaultId, key)
                outcome.browser != browser -> BrowserStore.update(context, outcome.browser, key)
            }
            if (outcome.retry)
                complete = false
        }
        return complete
    }
}
