@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.BrowserSync

import com.gigabytedevelopersinc.app.cometOTP.BrowserSync.BrowserSyncException.Reason
import com.gigabytedevelopersinc.app.cometOTP.Utilities.TokenCalculator
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Tools
import org.apache.commons.codec.binary.Base32
import org.apache.commons.codec.binary.Hex
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.math.BigInteger
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.spec.ECPrivateKeySpec

/**
 * Reproduces every value in the browser sync protocol's test vectors and refuses every input they
 * mark as rejected (protocol §11).
 *
 * `browser-sync/v1.json` is a copy of `vectors/v1.json` from gigabytedevelopers/CometOTP-Browser,
 * where it is built from fixed inputs with Node's crypto and checked by an independent Java
 * implementation; never from the code under test. Update both together.
 */
class BrowserSyncVectorsTest {

    private val vectors = JSONObject(
        javaClass.getResourceAsStream("/browser-sync/v1.json")!!.bufferedReader().use { it.readText() }
    )
    private val pairing = vectors.getJSONObject("pairing")
    private val vault = vectors.getJSONObject("vault")

    private fun hex(b: ByteArray) = String(Hex.encodeHex(b))
    private fun unhex(s: String): ByteArray = Hex.decodeHex(s.toCharArray())
    private fun b64u(s: String) = SyncEncoding.fromBase64Url(s)
    private fun b64u(b: ByteArray) = SyncEncoding.toBase64Url(b)
    private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }

    private fun privateKey(scalarHex: String): PrivateKey =
        KeyFactory.getInstance("EC").generatePrivate(ECPrivateKeySpec(BigInteger(1, unhex(scalarHex)), SyncCrypto.p256))

    private fun keys(): PairingKeys {
        val code = Pairing.parseUri(pairing.getString("uri"))
        val phonePublic = b64u(pairing.getString("phone_public"))
        val shared = SyncCrypto.ecdh(privateKey(pairing.getString("phone_private")), code.browserPublicKey)
        return Pairing.deriveKeys(shared, code.secret, Pairing.transcript(code, phonePublic))
    }

    private fun direction(name: String) = if (name == "to-browser") Pairing.TO_BROWSER else Pairing.TO_PHONE

    @Test
    fun readsTheQrCode() {
        val code = Pairing.parseUri(pairing.getString("uri"))
        assertEquals(pairing.getString("relay"), code.relay)
        assertEquals(pairing.getString("browser_name"), code.browserName)
        assertEquals(pairing.getString("channel"), b64u(code.channel))
        assertEquals(pairing.getString("browser_public"), b64u(code.browserPublicKey))
        assertEquals(pairing.getString("secret"), b64u(code.secret))
    }

    @Test
    fun bothSidesReachTheSameSecretWithItsLeadingZero() {
        val browserPublic = b64u(pairing.getString("browser_public"))
        val phonePublic = b64u(pairing.getString("phone_public"))
        val fromPhone = SyncCrypto.ecdh(privateKey(pairing.getString("phone_private")), browserPublic)
        val fromBrowser = SyncCrypto.ecdh(privateKey(pairing.getString("browser_private")), phonePublic)
        assertEquals(pairing.getString("shared"), hex(fromPhone))
        assertEquals(pairing.getString("shared"), hex(fromBrowser))
        assertEquals(32, fromPhone.size)
    }

    @Test
    fun derivesTheTranscriptKeysAndSas() {
        val code = Pairing.parseUri(pairing.getString("uri"))
        val transcript = Pairing.transcript(code, b64u(pairing.getString("phone_public")))
        assertEquals(pairing.getString("transcript"), hex(transcript))
        val okm = SyncCrypto.hkdf(
            SyncEncoding.concat(unhex(pairing.getString("shared")), code.secret),
            transcript,
            SyncEncoding.utf8("CometOTP pair v1 keys"),
            68,
        )
        assertEquals(pairing.getString("okm"), hex(okm))
        val keys = keys()
        assertEquals(pairing.getString("key_to_browser"), b64u(keys.toBrowser))
        assertEquals(pairing.getString("key_to_phone"), b64u(keys.toPhone))
        assertEquals(pairing.getString("sas"), keys.sas)
    }

    @Test
    fun opensAndResealsEveryMessageByteForByte() {
        val keys = keys()
        val channel = b64u(pairing.getString("channel"))
        val phonePublic = b64u(pairing.getString("phone_public"))
        val expected = listOf(
            PairingMessage.Hello(pairing.getString("phone_name")),
            PairingMessage.Confirm,
            PairingMessage.Vault(vault.getString("id"), vault.getString("read_token"), vault.getString("key")),
            PairingMessage.Done,
        )
        for ((m, message) in pairing.getJSONArray("messages").objects().zip(expected)) {
            val dir = direction(m.getString("direction"))
            val index = m.getInt("index")
            val isHello = dir == Pairing.TO_BROWSER && index == 1
            assertEquals(m.getString("aad"), hex(Pairing.aad(channel, dir, index)))
            assertEquals(message, Pairing.openFrame(keys, channel, dir, index, b64u(m.getString("frame"))))
            val resealed = Pairing.sealFrame(
                keys, channel, dir, index, message,
                phonePublicKey = if (isHello) phonePublic else null,
                nonce = b64u(m.getString("nonce")),
            )
            assertEquals(m.getString("frame"), b64u(resealed))
        }
    }

    @Test
    fun hashesTheTokens() {
        assertEquals(vault.getString("write_hash"), b64u(SyncCrypto.sha256(b64u(vault.getString("write_token")))))
        assertEquals(vault.getString("read_hash"), b64u(SyncCrypto.sha256(b64u(vault.getString("read_token")))))
    }

    @Test
    fun buildsAndSealsTheVaultByteForByte() {
        val id = b64u(vault.getString("id"))
        val key = b64u(vault.getString("key"))
        assertEquals(vault.getString("blob_key"), b64u(SyncVault.blobKey(key, id)))

        val payload = JSONObject(vault.getString("payload"))
        val services = payload.getJSONArray("services").objects().map {
            SyncService(
                id = it.getString("id"),
                type = it.getString("type"),
                secret = it.getString("secret"),
                algorithm = it.getString("algorithm"),
                digits = it.getInt("digits"),
                period = it.getInt("period"),
                issuer = it.getString("issuer"),
                label = it.getString("label"),
                icon = it.optString("icon", null),
                initials = it.optString("initials", null),
                badge = it.optString("badge", null),
                tags = it.optJSONArray("tags")?.let { tags -> (0 until tags.length()).map(tags::getString) },
            )
        }
        val json = SyncVault.payloadJson(payload.getString("phone"), payload.getLong("updated"), services)
        assertEquals(vault.getString("payload"), json)

        val blob = SyncVault.seal(key, id, vault.getLong("version"), json, b64u(vault.getString("nonce")))
        assertEquals(vault.getString("blob"), b64u(blob))
        assertEquals(vault.getInt("padded_length"), blob.size - SyncVault.OVERHEAD)
        assertEquals(Pair(vault.getLong("version"), json), SyncVault.open(key, id, blob))
    }

    @Test
    fun computesTheServiceIds() {
        for (v in vectors.getJSONArray("service_ids").objects())
            assertEquals(v.getString("id"), SyncVault.serviceId(Base32().decode(v.getString("secret"))))
    }

    /** The codes a browser computes from the vault must be the ones CometOTP shows. */
    @Test
    fun tokenCalculatorMatchesTheCodeVectors() {
        for (v in vectors.getJSONArray("codes").objects()) {
            val secret = Base32().decode(v.getString("secret"))
            val algorithm = TokenCalculator.HashAlgorithm.valueOf(v.getString("algorithm"))
            val digits = v.getInt("digits")
            val period = v.getInt("period")
            val time = v.getLong("time")
            val code = if (v.getString("type") == "steam") {
                // TOTP_Steam reads the clock: a period of Int.MAX_VALUE makes its time step 0
                // until 2038, so the offset is the counter (see TokenCalculatorGoldenTest).
                TokenCalculator.TOTP_Steam(secret, Int.MAX_VALUE, digits, algorithm, (time / period).toInt())
            } else {
                Tools.formatTokenString(TokenCalculator.TOTP_RFC6238(secret, period, time, digits, algorithm), digits)
            }
            assertEquals(v.toString(), v.getString("code"), code)
        }
    }

    @Test
    fun refusesEveryRejectedInput() {
        val keys = keys()
        val channel = b64u(pairing.getString("channel"))
        val id = b64u(vault.getString("id"))
        val key = b64u(vault.getString("key"))
        for (v in vectors.getJSONArray("reject").objects()) {
            val error = assertThrows(v.getString("what"), BrowserSyncException::class.java) {
                when (v.getString("kind")) {
                    "frame" -> Pairing.openFrame(keys, channel, direction(v.getString("direction")), v.getInt("index"), b64u(v.getString("frame")))
                    "blob" -> SyncVault.open(key, id, b64u(v.getString("blob")))
                    "public_key" -> SyncCrypto.publicKey(b64u(v.getString("key")))
                    else -> throw AssertionError("Unknown kind in: " + v.getString("what"))
                }
            }
            val expected = if (v.getString("kind") == "public_key") Reason.BAD_PUBLIC_KEY else Reason.AUTH_FAILED
            assertEquals(v.getString("what"), expected, error.reason)
        }
    }

    @Test
    fun theVectorsAreVersionOne() {
        assertEquals(1, vectors.getInt("protocol"))
        assertArrayEquals(b64u(pairing.getString("phone_public")), b64u(pairing.getJSONArray("messages").getJSONObject(0).getString("frame")).copyOf(65))
    }
}
