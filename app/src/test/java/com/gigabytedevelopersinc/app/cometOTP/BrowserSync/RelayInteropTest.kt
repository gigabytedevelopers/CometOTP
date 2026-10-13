@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.BrowserSync

import com.gigabytedevelopersinc.app.cometOTP.BrowserSync.PairedBrowser.State
import com.gigabytedevelopersinc.app.cometOTP.Database.Entry
import com.gigabytedevelopersinc.app.cometOTP.Utilities.TokenCalculator.HashAlgorithm
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL

/**
 * Against the real relay from gigabytedevelopers/CometOTP-Browser, when one is running: set
 * COMETOTP_TEST_RELAY to its URL (for example http://127.0.0.1:8787). Skipped otherwise, as in CI.
 * The phone creates a vault and uploads two versions, and the browser's download then opens; and
 * a whole pairing goes through the relay, the browser's side done here as the extension does it.
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

    /** One request as the browser makes it; the status and the JSON answer, if any. */
    private fun browserCall(method: String, path: String, token: String?, body: String? = null): Pair<Int, JSONObject?> {
        val connection = URL(relayUrl + path).openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.readTimeout = 40_000
        if (token != null)
            connection.setRequestProperty("Authorization", "Bearer $token")
        if (body != null) {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { it.write(body.toByteArray()) }
        }
        val code = connection.responseCode
        val text = (if (code >= 400) connection.errorStream else connection.inputStream)?.use { String(it.readBytes()) } ?: ""
        return Pair(code, if (text.isEmpty()) null else JSONObject(text))
    }

    /** The browser's message [index], long-polling the relay. */
    private fun browserReceive(channel: String, token: String, index: Int): ByteArray {
        while (true) {
            val (code, json) = browserCall("GET", "/v1/pair/$channel/to-browser?after=${index - 1}", token)
            assertEquals(200, code)
            val messages = json!!.getJSONArray("messages")
            if (messages.length() > 0) {
                assertEquals(index, messages.getJSONObject(0).getInt("seq"))
                return SyncEncoding.fromBase64Url(messages.getJSONObject(0).getString("m"))
            }
        }
    }

    private fun browserPost(channel: String, token: String, frame: ByteArray) {
        val (code, _) = browserCall("POST", "/v1/pair/$channel/to-phone", token, "{\"m\":\"${SyncEncoding.toBase64Url(frame)}\"}")
        assertEquals(201, code)
    }

    @Test
    fun aPairingThroughTheRelayGivesTheBrowserAVaultItCanOpen() {
        assumeTrue("COMETOTP_TEST_RELAY is not set", relayUrl != null)
        val own = SyncCrypto.generateKeyPair()
        val code = PairingCode(relayUrl!!, SyncCrypto.randomBytes(16), SyncCrypto.encodePublicKey(own.public),
            SyncCrypto.randomBytes(32), "Interop browser")
        val channel = SyncEncoding.toBase64Url(code.channel)
        val (created, createdJson) = browserCall("PUT", "/v1/pair/$channel", null)
        assertEquals(201, created)
        val browserToken = createdJson!!.getString("token")

        val browsers = LinkedHashMap<String, PairedBrowser>()
        val records = object : PhonePairing.Records {
            override fun put(browser: PairedBrowser) = true.also { browsers[browser.vaultId] = browser }
            override fun update(browser: PairedBrowser) = (browser.vaultId in browsers).also { if (it) browsers[browser.vaultId] = browser }
            override fun remove(vaultId: String) {
                browsers.remove(vaultId)
            }
        }
        val relay = RelayClient()
        val steps = ArrayList<PhonePairing.Step>()
        lateinit var pairing: PhonePairing
        pairing = PhonePairing(code, "Interop phone", records, relay, relay, listener = { step ->
            synchronized(steps) { steps.add(step) }
            if (step is PhonePairing.Step.Compare)
                pairing.approve()
        })
        val services = VaultPayload.services(listOf(
            Entry(Entry.OTPType.TOTP, "JBSWY3DPEHPK3PXP", 30, 6, "GitHub", "alice", HashAlgorithm.SHA1, mutableListOf())))
        val phone = Thread { pairing.run { services } }
        phone.start()

        // The browser's side (protocol §3.4).
        val hello = browserReceive(channel, browserToken, 1)
        val phoneKey = hello.copyOfRange(0, 65)
        val keys = Pairing.deriveKeys(SyncCrypto.ecdh(own.private, phoneKey), code.secret, Pairing.transcript(code, phoneKey))
        assertEquals(PairingMessage.Hello("Interop phone"), Pairing.openFrame(keys, code.channel, Pairing.TO_BROWSER, 1, hello))
        browserPost(channel, browserToken, Pairing.sealFrame(keys, code.channel, Pairing.TO_PHONE, 1, PairingMessage.Confirm))
        val vault = Pairing.openFrame(keys, code.channel, Pairing.TO_BROWSER, 2, browserReceive(channel, browserToken, 2))
            as PairingMessage.Vault
        browserPost(channel, browserToken, Pairing.sealFrame(keys, code.channel, Pairing.TO_PHONE, 2, PairingMessage.Done))
        phone.join(30_000)

        val paired = (steps.last() as PhonePairing.Step.Paired).browser
        assertEquals(State.PAIRED, browsers.getValue(vault.id).state)
        assertEquals(keys.sas, (steps.single { it is PhonePairing.Step.Compare } as PhonePairing.Step.Compare).sas)
        val connection = URL("$relayUrl/v1/vault/${vault.id}").openConnection() as HttpURLConnection
        connection.setRequestProperty("Authorization", "Bearer ${vault.read}")
        assertEquals(200, connection.responseCode)
        val blob = connection.inputStream.use { it.readBytes() }
        val (version, json) = SyncVault.open(SyncEncoding.fromBase64Url(vault.key), SyncEncoding.fromBase64Url(vault.id), blob)
        assertEquals(1L, version)
        assertEquals("GitHub", JSONObject(json).getJSONArray("services").getJSONObject(0).getString("issuer"))
        // The phone deleted the channel once paired.
        assertEquals(410, browserCall("GET", "/v1/pair/$channel/to-browser?after=2", browserToken).first)

        assertEquals(DeleteResult.DELETED, relay.delete(relayUrl, paired.vaultId, paired.writeToken))
    }
}
