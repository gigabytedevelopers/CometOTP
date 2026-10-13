@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.BrowserSync

import com.gigabytedevelopersinc.app.cometOTP.BrowserSync.PairedBrowser.State
import com.gigabytedevelopersinc.app.cometOTP.Database.Entry
import com.gigabytedevelopersinc.app.cometOTP.Utilities.TokenCalculator.HashAlgorithm
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL

/**
 * Against the real relay from gigabytedevelopers/CometOTP-Browser, when one is running: set
 * COMETOTP_TEST_RELAY to its URL (for example http://127.0.0.1:8787). Skipped otherwise, as in CI.
 * The phone creates a vault and uploads two versions; the browser's download then opens.
 */
class RelayInteropTest {

    private val relayUrl: String? = System.getenv("COMETOTP_TEST_RELAY")

    @Test
    fun aVaultUploadedHereOpensWhereTheBrowserDownloadsIt() {
        assumeTrue("COMETOTP_TEST_RELAY is not set", relayUrl != null)
        val relayUrl = relayUrl!!
        val relay = RelayClient()

        val id = SyncCrypto.randomBytes(16)
        val write = SyncCrypto.randomBytes(32)
        val read = SyncCrypto.randomBytes(32)
        val key = SyncCrypto.randomBytes(32)
        val vaultId = SyncEncoding.toBase64Url(id)
        assertEquals(CreateResult.CREATED, relay.createVault(relayUrl, vaultId, SyncCrypto.sha256(write), SyncCrypto.sha256(read)))

        var browser = PairedBrowser(vaultId, relayUrl, SyncEncoding.toBase64Url(write), SyncEncoding.toBase64Url(key),
            "Interop test", System.currentTimeMillis(), State.PAIRED)
        val uploader = VaultUploader(relay)
        val one = VaultPayload.services(listOf(
            Entry(Entry.OTPType.TOTP, "JBSWY3DPEHPK3PXP", 30, 6, "GitHub", "alice", HashAlgorithm.SHA1, mutableListOf())))
        browser = uploader.upload(browser, "Interop phone", one).browser!!
        assertEquals(1, browser.version)
        val two = one + VaultPayload.services(listOf(
            Entry(Entry.OTPType.STEAM, "GEZDGNBVGY3TQOJQ", 30, 5, "Steam", "gamer", HashAlgorithm.SHA1, mutableListOf())))
        browser = uploader.upload(browser, "Interop phone", two).browser!!
        assertEquals(2, browser.version)

        // What the browser does: download with the read token, then open with the vault key.
        val connection = URL("$relayUrl/v1/vault/$vaultId").openConnection() as HttpURLConnection
        connection.setRequestProperty("Authorization", "Bearer ${SyncEncoding.toBase64Url(read)}")
        assertEquals(200, connection.responseCode)
        assertEquals("\"2\"", connection.getHeaderField("ETag"))
        val blob = connection.inputStream.use { it.readBytes() }
        val (version, json) = SyncVault.open(key, id, blob)
        assertEquals(2L, version)
        assertEquals(listOf("GitHub", "Steam"), org.json.JSONObject(json).getJSONArray("services").let { s ->
            (0 until s.length()).map { s.getJSONObject(it).getString("issuer") }
        })

        assertEquals(DeleteResult.DELETED, relay.delete(relayUrl, vaultId, browser.writeToken))
        assertEquals(UploadResult.Gone, relay.upload(relayUrl, vaultId, browser.writeToken, blob))
    }
}
