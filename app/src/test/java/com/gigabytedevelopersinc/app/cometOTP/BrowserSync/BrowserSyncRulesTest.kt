@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.BrowserSync

import com.gigabytedevelopersinc.app.cometOTP.BrowserSync.BrowserSyncException.Reason
import org.apache.commons.codec.binary.Hex
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The browser sync protocol's rules that its test vectors do not pin down. Expected values come
 * from RFC 5869 and from JavaScript's JSON.stringify in Node 26, never from the code under test.
 */
class BrowserSyncRulesTest {

    private fun hex(b: ByteArray) = String(Hex.encodeHex(b))
    private fun unhex(s: String): ByteArray = Hex.decodeHex(s.toCharArray())

    private fun reason(block: () -> Unit): Reason = assertThrows(BrowserSyncException::class.java) { block() }.reason

    private val validUri = "cometotp://pair?v=1&r=https%3A%2F%2Frelay.example.com" +
            "&c=oohBsTHg0DyxK-e7cO2VwA" +
            "&k=BJ-UYdgvNYnsbAksih0zJfNjnnV9zttQ0_FkLupZ-fbVE1Upq40qvEENsEFMsBpbnCXjPhX-_9iUKOi6Di_N5UQ" +
            "&s=ONCA0r62gtS4e2QvgwI40EN8W_qQcJSUfJZbGKg2Fbo" +
            "&n=Chrome%20on%20macOS"

    @Test
    fun hkdfMatchesRfc5869() {
        // Test case 1, and test case 3 with no salt and no info.
        assertEquals("3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865",
                hex(SyncCrypto.hkdf(ByteArray(22) { 0x0b }, unhex("000102030405060708090a0b0c"), unhex("f0f1f2f3f4f5f6f7f8f9"), 42)))
        assertEquals("8da4e775a563c18f715f802a063c5a31b8a11f5c5ee1879ec3454e5f3c738d2d9d201395faa4b61a96c8",
                hex(SyncCrypto.hkdf(ByteArray(22) { 0x0b }, ByteArray(0), ByteArray(0), 42)))
    }

    @Test
    fun base64UrlRoundTripsAndRefusesAnythingElse() {
        for (length in 0..40) {
            val bytes = SyncCrypto.randomBytes(length)
            assertArrayEquals(bytes, SyncEncoding.fromBase64Url(SyncEncoding.toBase64Url(bytes)))
        }
        for (text in listOf("AA==", "AA+/", "A", "AA AA"))
            assertEquals(text, Reason.BAD_ENCODING, reason { SyncEncoding.fromBase64Url(text) })
    }

    @Test
    fun jsonStringEscapesAsJavaScriptDoes() {
        assertEquals("\"a\\\"b\\\\c\\n\\t\\u0001\\u001f\u007f é😀/\"",
                SyncEncoding.jsonString("a\"b\\c\n\t\u0001\u001f\u007f é😀/"))
        assertEquals("\"x\\ud800y\\udc00z\"", SyncEncoding.jsonString("x\ud800y\udc00z"))
    }

    @Test
    fun readsNamesWithPercentEncodingAndPlusSigns() {
        val code = Pairing.parseUri(validUri.replace("&n=Chrome%20on%20macOS", "&n=Firefox+%E2%80%94%20%C3%BCber"))
        assertEquals("Firefox+— über", code.browserName)
    }

    @Test
    fun refusesBadPairingCodes() {
        assertEquals(Reason.UNSUPPORTED_VERSION, reason { Pairing.parseUri(validUri.replace("v=1", "v=2")) })
        assertEquals(Reason.BAD_URI, reason { Pairing.parseUri(validUri.replace("cometotp://", "otpauth://")) })
        assertEquals(Reason.BAD_URI, reason { Pairing.parseUri(validUri.replace("&c=oohBsTHg0DyxK-e7cO2VwA", "&c=AAAA")) })
        assertEquals(Reason.BAD_URI, reason { Pairing.parseUri(validUri.replace("&n=Chrome%20on%20macOS", "&n=")) })
        assertEquals(Reason.BAD_URI, reason { Pairing.parseUri(validUri.replace("&n=Chrome%20on%20macOS", "&n=" + "x".repeat(65))) })
        assertEquals(Reason.BAD_URI, reason { Pairing.parseUri(validUri.replace("%20on", "%2Gon")) })
        assertEquals(Reason.BAD_RELAY, reason { Pairing.parseUri(validUri.replace("https%3A", "http%3A")) })
    }

    @Test
    fun onlyAcceptsHttpsRelaysOrLocalHttpWhenAllowed() {
        assertEquals("https://relay.example.com/sync", Pairing.checkRelayUrl("https://relay.example.com/sync"))
        for (relay in listOf(
            "http://relay.example.com",
            "https://relay.example.com/",
            "https://relay.example.com?x=1",
            "https://relay.example.com#x",
            "https://user:pass@relay.example.com",
            "ftp://relay.example.com",
            "not a url",
        )) {
            assertEquals(relay, Reason.BAD_RELAY, reason { Pairing.checkRelayUrl(relay) })
        }
        assertEquals(Reason.BAD_RELAY, reason { Pairing.checkRelayUrl("http://192.168.1.20:8080") })
        assertEquals("http://192.168.1.20:8080", Pairing.checkRelayUrl("http://192.168.1.20:8080", allowInsecure = true))
        assertEquals("http://10.0.2.2:8080", Pairing.checkRelayUrl("http://10.0.2.2:8080", allowInsecure = true))
        assertEquals(Reason.BAD_RELAY, reason { Pairing.checkRelayUrl("http://relay.example.com", allowInsecure = true) })
    }

    @Test
    fun aWholeExchangeWithFreshKeys() {
        val browser = SyncCrypto.generateKeyPair()
        val phone = SyncCrypto.generateKeyPair()
        val browserPublic = SyncCrypto.encodePublicKey(browser.public)
        val phonePublic = SyncCrypto.encodePublicKey(phone.public)
        assertTrue(SyncCrypto.isP256Point(phonePublic))
        val shared = SyncCrypto.ecdh(phone.private, browserPublic)
        assertArrayEquals(shared, SyncCrypto.ecdh(browser.private, phonePublic))

        val keys = Pairing.deriveKeys(shared, SyncCrypto.randomBytes(32), SyncCrypto.randomBytes(32))
        assertTrue(Regex("\\d{3} \\d{3}").matches(keys.sas))
        val channel = SyncCrypto.randomBytes(16)
        val hello = Pairing.sealFrame(keys, channel, Pairing.TO_BROWSER, 1, PairingMessage.Hello("Pixel 7"), phonePublic)
        assertEquals(PairingMessage.Hello("Pixel 7"), Pairing.openFrame(keys, channel, Pairing.TO_BROWSER, 1, hello))
        val confirm = Pairing.sealFrame(keys, channel, Pairing.TO_PHONE, 1, PairingMessage.Confirm)
        assertEquals(PairingMessage.Confirm, Pairing.openFrame(keys, channel, Pairing.TO_PHONE, 1, confirm))
        val vault = PairingMessage.Vault(
            SyncEncoding.toBase64Url(SyncCrypto.randomBytes(16)),
            SyncEncoding.toBase64Url(SyncCrypto.randomBytes(32)),
            SyncEncoding.toBase64Url(SyncCrypto.randomBytes(32)),
        )
        val sealed = Pairing.sealFrame(keys, channel, Pairing.TO_BROWSER, 2, vault)
        assertEquals(vault, Pairing.openFrame(keys, channel, Pairing.TO_BROWSER, 2, sealed))
        assertEquals(Reason.AUTH_FAILED, reason { Pairing.openFrame(keys, SyncCrypto.randomBytes(16), Pairing.TO_BROWSER, 2, sealed) })
        assertEquals(Reason.AUTH_FAILED, reason { Pairing.openFrame(keys, channel, Pairing.TO_PHONE, 2, sealed) })
    }

    @Test
    fun checksMessagesAndWhereThePhoneKeyGoes() {
        val keys = Pairing.deriveKeys(SyncCrypto.randomBytes(32), SyncCrypto.randomBytes(32), SyncCrypto.randomBytes(32))
        val channel = SyncCrypto.randomBytes(16)
        val shortKey = PairingMessage.Vault(SyncEncoding.toBase64Url(SyncCrypto.randomBytes(16)), SyncEncoding.toBase64Url(SyncCrypto.randomBytes(32)), "AAAA")
        val sealed = Pairing.sealFrame(keys, channel, Pairing.TO_BROWSER, 2, shortKey)
        assertEquals(Reason.BAD_ENCODING, reason { Pairing.openFrame(keys, channel, Pairing.TO_BROWSER, 2, sealed) })
        assertEquals(Reason.BAD_MESSAGE, reason { Pairing.sealFrame(keys, channel, Pairing.TO_BROWSER, 1, PairingMessage.Hello(""), ByteArray(65)) })
        assertThrows(IllegalArgumentException::class.java) { Pairing.sealFrame(keys, channel, Pairing.TO_BROWSER, 1, PairingMessage.Hello("x")) }
        assertThrows(IllegalArgumentException::class.java) { Pairing.sealFrame(keys, channel, Pairing.TO_PHONE, 1, PairingMessage.Confirm, ByteArray(65)) }
    }

    @Test
    fun checksPointsOnTheCurve() {
        val publicKey = SyncCrypto.encodePublicKey(SyncCrypto.generateKeyPair().public)
        assertTrue(SyncCrypto.isP256Point(publicKey))
        val off = publicKey.copyOf().also { it[64] = (it[64].toInt() xor 1).toByte() }
        assertFalse(SyncCrypto.isP256Point(off))
        assertFalse(SyncCrypto.isP256Point(publicKey.copyOf(33)))
        assertEquals(Reason.BAD_PUBLIC_KEY, reason { SyncCrypto.ecdh(SyncCrypto.generateKeyPair().private, off) })
    }

    private val service = SyncService("AAAAAAAAAAAAAAAA", "totp", "JBSWY3DPEHPK3PXP", "SHA1", 6, 30, "Example", "alice")

    @Test
    fun padsToWholeBlocksAndRefusesOtherShapes() {
        val key = SyncCrypto.randomBytes(32)
        val id = SyncCrypto.randomBytes(16)
        val blob = SyncVault.seal(key, id, 7, SyncVault.payloadJson("Pixel 7", 0, listOf(service)))
        assertEquals(SyncVault.OVERHEAD + SyncVault.PAD_BLOCK, blob.size)
        assertEquals(7L, SyncVault.version(blob))
        assertEquals(Reason.BAD_BLOB, reason { SyncVault.version(blob.copyOf(blob.size - 1)) })
        assertEquals(Reason.BAD_BLOB, reason { SyncVault.version(blob.copyOf().also { it[0] = 'X'.code.toByte() }) })
        assertEquals(Reason.BAD_BLOB, reason { SyncVault.version(ByteArray(SyncVault.MAX_BLOB_LENGTH + SyncVault.PAD_BLOCK)) })
        assertThrows(IllegalArgumentException::class.java) { SyncVault.seal(key, id, 0, "{}") }
        val forty = SyncVault.payloadJson("Pixel 7", 0, List(40) { service })
        assertEquals(SyncVault.OVERHEAD + 2 * SyncVault.PAD_BLOCK, SyncVault.seal(key, id, 1, forty).size)
    }

    @Test
    fun refusesARollbackAndAnotherVaultsBlob() {
        val key = SyncCrypto.randomBytes(32)
        val id = SyncCrypto.randomBytes(16)
        val blob = SyncVault.seal(key, id, 4, SyncVault.payloadJson("Pixel 7", 0, listOf(service)))
        assertEquals(Reason.ROLLBACK, reason { SyncVault.open(key, id, blob, minVersion = 5) })
        assertEquals(4L, SyncVault.open(key, id, blob, minVersion = 4).first)
        assertEquals(Reason.AUTH_FAILED, reason { SyncVault.open(key, SyncCrypto.randomBytes(16), blob) })
    }

    @Test
    fun writesOptionalMembersOnlyWhenSet() {
        assertEquals(
            "{\"v\":1,\"phone\":\"P\",\"updated\":5,\"services\":[{\"id\":\"AAAAAAAAAAAAAAAA\",\"type\":\"totp\"," +
                    "\"secret\":\"JBSWY3DPEHPK3PXP\",\"algorithm\":\"SHA1\",\"digits\":6,\"period\":30," +
                    "\"issuer\":\"Example\",\"label\":\"alice\"}]}",
            SyncVault.payloadJson("P", 5, listOf(service)),
        )
        assertEquals("{\"v\":1,\"phone\":\"P\",\"updated\":5,\"services\":[]}", SyncVault.payloadJson("P", 5, emptyList()))
    }
}
