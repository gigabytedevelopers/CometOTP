@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.BrowserSync

/**
 * Brings one paired browser's vault up to date (protocol §7.1): uploads the next version when what
 * the browser shows has changed, and works out what the answer means for the pairing.
 */
class VaultUploader(private val relay: Relay, private val now: () -> Long = System::currentTimeMillis) {

    /**
     * What became of the browser: [browser] is the pairing to keep (unchanged, updated or broken),
     * null when the vault is gone and the pairing should be removed. [retry] asks for another try
     * later.
     */
    class Outcome(val browser: PairedBrowser?, val retry: Boolean)

    /**
     * Uploads to a paired browser. With [pairing], uploads version 1 for a pairing still under way
     * (protocol §3.4), which is otherwise left alone.
     */
    fun upload(browser: PairedBrowser, phone: String, services: List<SyncService>, pairing: Boolean = false): Outcome {
        val expected = if (pairing) PairedBrowser.State.PENDING else PairedBrowser.State.PAIRED
        if (browser.state != expected)
            return Outcome(browser, retry = false)
        val hash = VaultPayload.hash(phone, services)
        if (hash == browser.payloadHash)
            return Outcome(browser, retry = false)

        val json = SyncVault.payloadJson(phone, now() / 1000, services)
        val key = SyncEncoding.fromBase64Url(browser.vaultKey)
        val id = SyncEncoding.fromBase64Url(browser.vaultId)
        var version = browser.version + 1
        // A stale version (the relay is ahead, e.g. after this phone lost an answer) is retried
        // once, after the version the relay has.
        repeat(2) {
            val blob = SyncVault.seal(key, id, version, json)
            when (val result = relay.upload(browser.relay, browser.vaultId, browser.writeToken, blob)) {
                is UploadResult.Stored -> return Outcome(
                    browser.copy(version = result.version, payloadHash = hash, uploadedAt = now(), lastRead = result.lastRead,
                        sharedIds = services.map { it.id }),
                    retry = false,
                )
                is UploadResult.Stale -> version = result.storedVersion + 1
                UploadResult.Gone -> return Outcome(null, retry = false)
                UploadResult.Lost -> return Outcome(browser.copy(state = PairedBrowser.State.BROKEN), retry = false)
                UploadResult.Failed -> return Outcome(browser, retry = true)
            }
        }
        return Outcome(browser, retry = true)
    }
}
