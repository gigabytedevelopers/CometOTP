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
     * finish, deleting their vaults like those of removed browsers. Returns false when an upload
     * or a deletion should be tried again later.
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
        if (cleanUpPending)
            BrowserStore.removeStalePending(context, key)

        var complete = true
        // Browsers removed while the relay could not be reached: try their deletion again.
        for (browser in BrowserStore.load(context, key).filter { it.state == PairedBrowser.State.REMOVING }) {
            if (!finishRemoval(context, browser, key, relay))
                complete = false
        }

        val phone = VaultPayload.phoneName(context)
        val uploader = VaultUploader(relay)
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

    /**
     * Removes [browser]: it is marked [PairedBrowser.State.REMOVING] at once, so it gets no more
     * updates, and dropped once the relay has deleted its vault. Returns whether that happened
     * now; if not, [sync] tries again at the next change or unlock. Background thread.
     */
    fun remove(context: Context, browser: PairedBrowser, key: SecretKey, relay: Relay = RelayClient()): Boolean {
        val removing = browser.copy(state = PairedBrowser.State.REMOVING)
        if (!BrowserStore.update(context, removing, key))
            return true
        return finishRemoval(context, removing, key, relay)
    }

    private fun finishRemoval(context: Context, browser: PairedBrowser, key: SecretKey, relay: Relay): Boolean {
        if (!vaultDeleted(relay.delete(browser.relay, browser.vaultId, browser.writeToken)))
            return false
        BrowserStore.remove(context, browser.vaultId, key)
        return true
    }

    /** Whether nothing is left of the vault on the relay after a delete: deleted, or already gone or unknown. */
    internal fun vaultDeleted(result: DeleteResult): Boolean = result != DeleteResult.FAILED
}
