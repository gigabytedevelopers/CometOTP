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
import java.math.BigInteger
import java.security.KeyFactory
import java.security.spec.ECPrivateKeySpec

/**
 * The browser sync crypto on Android's own security provider. The JVM tests in app/src/test run
 * every test vector on the desktop JDK; these repeat the parts that depend on the provider (EC key
 * generation and parsing, ECDH, AES-GCM, HMAC) with values from `vectors/v1.json` in
 * gigabytedevelopers/CometOTP-Browser.
 */
class BrowserSyncDeviceTest {

    private fun hex(b: ByteArray) = String(Hex.encodeHex(b))
    private fun unhex(s: String): ByteArray = Hex.decodeHex(s.toCharArray())
    private fun b64u(s: String) = SyncEncoding.fromBase64Url(s)

    private val uri = "cometotp://pair?v=1&r=https%3A%2F%2Frelay.example.com&c=oohBsTHg0DyxK-e7cO2VwA" +
            "&k=BJ-UYdgvNYnsbAksih0zJfNjnnV9zttQ0_FkLupZ-fbVE1Upq40qvEENsEFMsBpbnCXjPhX-_9iUKOi6Di_N5UQ" +
            "&s=ONCA0r62gtS4e2QvgwI40EN8W_qQcJSUfJZbGKg2Fbo&n=Chrome%20on%20macOS"
    private val phonePrivate = "28dd103d25601ba0cfcbfa613dc3910ef97f871f5ddf6c6140d2a2dd3028c5fe"
    private val phonePublic = "BBWWogqbaHDBKIK8ug7dLfN-vhB6MvG2nLVO8qek3u8ARSSsUQLqMrA8YBQsUeI9idFbZtvOJR_R2Qbq2OQRhtI"
    private val shared = "009fa5f169fcce4f01c890ac529329e81496321559caba918f774b9402c56b48"
    private val hello = "BBWWogqbaHDBKIK8ug7dLfN-vhB6MvG2nLVO8qek3u8ARSSsUQLqMrA8YBQsUeI9idFbZtvOJR_R2Qbq2OQRhtIjvCEhXhou-u4a" +
            "EvA3GSoTBKdCAgp8iCmaqoqBagcHYuwXtsCgSD-qKj7ZiNOorpgX-xQlmLUUq3Kmq76dLHKyfjvmVA"

    @Test
    fun theVectorKeyExchangeOnThisDevice() {
        val code = Pairing.parseUri(uri)
        val key = KeyFactory.getInstance("EC").generatePrivate(ECPrivateKeySpec(BigInteger(1, unhex(phonePrivate)), SyncCrypto.p256))
        val secret = SyncCrypto.ecdh(key, code.browserPublicKey)
        assertEquals(shared, hex(secret))
        val keys = Pairing.deriveKeys(secret, code.secret, Pairing.transcript(code, b64u(phonePublic)))
        assertEquals("135 557", keys.sas)
        val frame = b64u(hello)
        assertEquals(PairingMessage.Hello("Galaxy S25 Ultra"), Pairing.openFrame(keys, code.channel, Pairing.TO_BROWSER, 1, frame))
        val resealed = Pairing.sealFrame(keys, code.channel, Pairing.TO_BROWSER, 1, PairingMessage.Hello("Galaxy S25 Ultra"),
                b64u(phonePublic), frame.copyOfRange(65, 77))
        assertArrayEquals(frame, resealed)
    }

    @Test
    fun theVectorVaultOnThisDevice() {
        val id = b64u("PJB3Y7VMaXcST--a56YZ7A")
        val key = b64u("em6jXuraHPQE7de9mrP86-D-yROQxe-vS7sC7ir1fgc")
        val payload = SyncVault.payloadJson("Galaxy S25 Ultra", 1791763200, listOf(
            SyncService("mQq6NvYAZpg7QAw2", "totp", "JBSWY3DPEHPK3PXP", "SHA1", 6, 30, "GitHub", "alice@example.com",
                    icon = "Github", tags = listOf("Work")),
            SyncService("SXvyuudfADeefNlo", "steam", "Q4UE3E4GXL4TXE2SSGECPF3S6TJ6AUPU", "SHA1", 5, 30, "Steam", "alice",
                    initials = "ST", badge = "#3F51B5"),
        ))
        val blob = SyncVault.seal(key, id, 3, payload, b64u("ozecPfaVWKWZ1_L-"))
        // SHA-256 of the vectors' blob (computed with Node), rather than its 4,136 bytes.
        assertEquals("3390a6a9fb3ca7c55c7d8b4b94747186a97f4c93ba24925a254f00ae835ecea9", hex(SyncCrypto.sha256(blob)))
        assertEquals(Pair(3L, payload), SyncVault.open(key, id, blob))
    }

    @Test
    fun aFreshExchangeOnThisDevice() {
        val browser = SyncCrypto.generateKeyPair()
        val phone = SyncCrypto.generateKeyPair()
        val browserPublic = SyncCrypto.encodePublicKey(browser.public)
        val phonePublicKey = SyncCrypto.encodePublicKey(phone.public)
        assertTrue(SyncCrypto.isP256Point(phonePublicKey))
        assertArrayEquals(SyncCrypto.ecdh(phone.private, browserPublic), SyncCrypto.ecdh(browser.private, phonePublicKey))
        val off = phonePublicKey.copyOf().also { it[64] = (it[64].toInt() xor 1).toByte() }
        assertFalse(SyncCrypto.isP256Point(off))
        val error = assertThrows(BrowserSyncException::class.java) { SyncCrypto.ecdh(browser.private, off) }
        assertEquals(Reason.BAD_PUBLIC_KEY, error.reason)
    }
}
