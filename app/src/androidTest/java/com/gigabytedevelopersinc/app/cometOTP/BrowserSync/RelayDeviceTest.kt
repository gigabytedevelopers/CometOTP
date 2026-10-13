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
 * Uploading to a real relay through Android's own HTTP stack and the debug build's network
 * security config, which allows plain HTTP to the development computer. Start the relay from
 * gigabytedevelopers/CometOTP-Browser on port 8787 first; the emulator reaches it at 10.0.2.2.
 * Skipped when no relay answers there, as in CI.
 */
class RelayDeviceTest {

    private val relayUrl = "http://10.0.2.2:8787"

    private fun relayAnswers(): Boolean = try {
        val connection = URL("$relayUrl/v1/").openConnection() as HttpURLConnection
        connection.connectTimeout = 2_000
        connection.readTimeout = 2_000
        connection.responseCode == 200
    } catch (e: Exception) {
        false
    }

    @Test
    fun uploadsAVaultTheBrowserCanOpen() {
        assumeTrue("No relay at $relayUrl", relayAnswers())
        val relay = RelayClient()
        val id = SyncCrypto.randomBytes(16)
        val write = SyncCrypto.randomBytes(32)
        val read = SyncCrypto.randomBytes(32)
        val key = SyncCrypto.randomBytes(32)
        val vaultId = SyncEncoding.toBase64Url(id)
        assertEquals(CreateResult.CREATED, relay.createVault(relayUrl, vaultId, SyncCrypto.sha256(write), SyncCrypto.sha256(read)))

        val browser = PairedBrowser(vaultId, relayUrl, SyncEncoding.toBase64Url(write), SyncEncoding.toBase64Url(key),
            "Device test", System.currentTimeMillis(), State.PAIRED)
        val services = VaultPayload.services(listOf(
            Entry(Entry.OTPType.TOTP, "JBSWY3DPEHPK3PXP", 30, 6, "GitHub", "alice", HashAlgorithm.SHA1, mutableListOf())))
        val uploaded = VaultUploader(relay).upload(browser, "Device test phone", services).browser!!
        assertEquals(1, uploaded.version)

        val connection = URL("$relayUrl/v1/vault/$vaultId").openConnection() as HttpURLConnection
        connection.setRequestProperty("Authorization", "Bearer ${SyncEncoding.toBase64Url(read)}")
        assertEquals(200, connection.responseCode)
        val (version, json) = SyncVault.open(key, id, connection.inputStream.use { it.readBytes() })
        assertEquals(1L, version)
        assertEquals(SyncVault.payloadJson("Device test phone", org.json.JSONObject(json).getLong("updated"), services), json)

        assertEquals(DeleteResult.DELETED, relay.delete(relayUrl, vaultId, uploaded.writeToken))
    }
}
