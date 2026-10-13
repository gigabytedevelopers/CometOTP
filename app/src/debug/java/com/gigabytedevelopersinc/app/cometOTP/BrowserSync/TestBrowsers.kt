@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.BrowserSync

import android.content.Context
import android.os.Build
import javax.crypto.SecretKey

/**
 * Debug builds only: pairs a stand-in browser with the relay on the development computer, so
 * Connected extensions and the uploads can be tried before a real browser can pair. Start the relay
 * from gigabytedevelopers/CometOTP-Browser on port 8787; from a phone, `adb reverse tcp:8787 tcp:8787`.
 * Release builds have a version of this that does nothing (src/release).
 */
object TestBrowsers {
    const val AVAILABLE = true

    private val isEmulator: Boolean
        get() = Build.PRODUCT.startsWith("sdk") || Build.FINGERPRINT.contains("generic")

    /** Adds "Test browser [number]"; its vault is created on the relay if one answers. Background thread. */
    fun add(context: Context, key: SecretKey, number: Int, relay: Relay = RelayClient()): PairedBrowser? {
        val relayUrl = if (isEmulator) "http://10.0.2.2:8787" else "http://localhost:8787"
        val id = SyncCrypto.randomBytes(16)
        val write = SyncCrypto.randomBytes(32)
        val read = SyncCrypto.randomBytes(32)
        val vaultKey = SyncCrypto.randomBytes(32)
        relay.createVault(relayUrl, SyncEncoding.toBase64Url(id), SyncCrypto.sha256(write), SyncCrypto.sha256(read))
        val browser = PairedBrowser(
            vaultId = SyncEncoding.toBase64Url(id),
            relay = relayUrl,
            writeToken = SyncEncoding.toBase64Url(write),
            vaultKey = SyncEncoding.toBase64Url(vaultKey),
            name = "Test browser $number",
            pairedAt = System.currentTimeMillis(),
            state = PairedBrowser.State.PAIRED,
        )
        return if (BrowserStore.put(context, browser, key)) browser else null
    }
}
