@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.BrowserSync

import com.gigabytedevelopersinc.app.cometOTP.BrowserSync.BrowserSyncException.Reason
import com.gigabytedevelopersinc.app.cometOTP.BrowserSync.SyncEncoding.concat
import com.gigabytedevelopersinc.app.cometOTP.BrowserSync.SyncEncoding.jsonString
import com.gigabytedevelopersinc.app.cometOTP.BrowserSync.SyncEncoding.lp
import com.gigabytedevelopersinc.app.cometOTP.BrowserSync.SyncEncoding.u8
import com.gigabytedevelopersinc.app.cometOTP.BrowserSync.SyncEncoding.utf8
import org.json.JSONException
import org.json.JSONObject
import java.net.URI
import java.net.URISyntaxException
import java.nio.ByteBuffer

/** What a browser's pairing QR code carries (protocol §3.1). */
class PairingCode(
    /** The relay's base URL. */
    val relay: String,
    /** 16 bytes. */
    val channel: ByteArray,
    /** The browser's ephemeral public key, 65 bytes. */
    val browserPublicKey: ByteArray,
    /** 32 bytes. */
    val secret: ByteArray,
    /** The browser's name as shown to the person. */
    val browserName: String,
)

/** The keys both sides derive, and the six digits the person compares (protocol §3.2). */
class PairingKeys(
    /** Seals messages to the browser. */
    val toBrowser: ByteArray,
    /** Seals messages to the phone. */
    val toPhone: ByteArray,
    /** Six digits in two groups, such as "135 557". */
    val sas: String,
)

/** The pairing messages of protocol §3.3. */
sealed class PairingMessage {
    data class Hello(val phone: String) : PairingMessage()
    object Confirm : PairingMessage()
    data class Vault(val id: String, val read: String, val key: String) : PairingMessage()
    object Done : PairingMessage()
    object Cancel : PairingMessage()
}

/** Pairing with a browser: reading its QR code, the keys and SAS, and the sealed messages. */
object Pairing {

    const val TO_BROWSER = 1
    const val TO_PHONE = 2

    /** Seconds a QR code accepts the phone's hello. */
    const val HELLO_WINDOW = 120

    /** Seconds a pairing channel lasts on the relay. */
    const val CHANNEL_LIFETIME = 600

    private val PAIR_LABEL = utf8("CometOTP pair v1")
    private val PAIR_KEYS_INFO = utf8("CometOTP pair v1 keys")
    private const val URI_PREFIX = "cometotp://pair?"
    private const val MAX_RELAY_LENGTH = 200
    private const val MAX_NAME_BYTES = 64

    private fun checkName(name: String?, what: String): String {
        if (name == null || utf8(name).size !in 1..MAX_NAME_BYTES)
            throw BrowserSyncException(Reason.BAD_MESSAGE, "$what must be 1 to $MAX_NAME_BYTES bytes")
        return name
    }

    private fun isLocalHost(host: String): Boolean {
        val h = host.removePrefix("[").removeSuffix("]").lowercase()
        return h == "localhost" || h.endsWith(".localhost") || h == "::1" ||
                h.startsWith("127.") || h.startsWith("10.") || h.startsWith("192.168.") ||
                Regex("^172\\.(1[6-9]|2\\d|3[01])\\.").containsMatchIn(h) ||
                Regex("^f[cd][0-9a-f]{2}:").containsMatchIn(h)
    }

    /**
     * Checks a relay base URL: absolute https, no credentials, query, fragment or trailing slash.
     * Plain http is only accepted for loopback and private addresses when [allowInsecure] is set,
     * which only debug builds do.
     */
    fun checkRelayUrl(relay: String, allowInsecure: Boolean = false): String {
        val uri = try {
            URI(relay)
        } catch (e: URISyntaxException) {
            throw BrowserSyncException(Reason.BAD_RELAY, "The relay is not a URL")
        }
        val host = uri.host
        val secure = uri.scheme == "https" && host != null
        val localHttp = uri.scheme == "http" && allowInsecure && host != null && isLocalHost(host)
        if (!secure && !localHttp)
            throw BrowserSyncException(Reason.BAD_RELAY, "The relay must use https")
        if (relay.length > MAX_RELAY_LENGTH)
            throw BrowserSyncException(Reason.BAD_RELAY, "The relay URL is too long")
        if (uri.rawUserInfo != null || '?' in relay || '#' in relay || relay.endsWith("/"))
            throw BrowserSyncException(Reason.BAD_RELAY, "The relay URL must have no credentials, query, fragment or trailing slash")
        return relay
    }

    /** Whether [text] looks like a pairing code at all, whether or not it is a valid one. */
    fun isPairingUri(text: String): Boolean = text.startsWith(URI_PREFIX)

    /**
     * Reads a scanned pairing code. The browser's key is checked on the curve when it is used, by
     * [SyncCrypto.ecdh].
     */
    fun parseUri(uri: String, allowInsecureRelay: Boolean = false): PairingCode {
        if (!isPairingUri(uri))
            throw BrowserSyncException(Reason.BAD_URI, "Not a CometOTP pairing code")
        val params = HashMap<String, String>()
        for (pair in uri.substring(URI_PREFIX.length).split('&')) {
            val eq = pair.indexOf('=')
            if (eq >= 0)
                params[pair.substring(0, eq)] = SyncEncoding.percentDecode(pair.substring(eq + 1))
        }
        if (params["v"] != "1")
            throw BrowserSyncException(Reason.UNSUPPORTED_VERSION, "This pairing code needs a newer version of CometOTP")
        val relay = checkRelayUrl(params["r"] ?: "", allowInsecureRelay)
        try {
            return PairingCode(
                relay,
                SyncEncoding.fromBase64Url(params["c"], 16, "The channel"),
                SyncEncoding.fromBase64Url(params["k"], 65, "The browser's key"),
                SyncEncoding.fromBase64Url(params["s"], 32, "The secret"),
                checkName(params["n"], "The browser's name"),
            )
        } catch (e: BrowserSyncException) {
            throw BrowserSyncException(Reason.BAD_URI, e.message ?: "Invalid pairing code")
        }
    }

    /** `SHA256(PAIR_LABEL ‖ lp(r) ‖ c ‖ E_pub ‖ lp(n) ‖ P_pub)`. */
    fun transcript(code: PairingCode, phonePublicKey: ByteArray): ByteArray = SyncCrypto.sha256(
        PAIR_LABEL,
        lp(utf8(code.relay)),
        code.channel,
        code.browserPublicKey,
        lp(utf8(code.browserName)),
        phonePublicKey,
    )

    /** Both keys and the SAS from the ECDH result, the code's secret and the transcript. */
    fun deriveKeys(shared: ByteArray, secret: ByteArray, transcript: ByteArray): PairingKeys {
        val okm = SyncCrypto.hkdf(concat(shared, secret), transcript, PAIR_KEYS_INFO, 68)
        val number = (ByteBuffer.wrap(okm, 64, 4).int.toLong() and 0xffffffffL) % 1_000_000
        val digits = number.toString().padStart(6, '0')
        return PairingKeys(okm.copyOfRange(0, 32), okm.copyOfRange(32, 64), "${digits.substring(0, 3)} ${digits.substring(3)}")
    }

    fun aad(channel: ByteArray, direction: Int, index: Int): ByteArray =
        concat(PAIR_LABEL, channel, u8(direction), u8(index))

    /** The message's JSON with its members in a fixed order, as the browser writes it too. */
    private fun json(message: PairingMessage): String = when (message) {
        is PairingMessage.Hello -> "{\"t\":\"hello\",\"phone\":${jsonString(checkName(message.phone, "The phone's name"))}}"
        is PairingMessage.Vault ->
            "{\"t\":\"vault\",\"id\":${jsonString(message.id)},\"read\":${jsonString(message.read)},\"key\":${jsonString(message.key)}}"
        PairingMessage.Confirm -> "{\"t\":\"confirm\"}"
        PairingMessage.Done -> "{\"t\":\"done\"}"
        PairingMessage.Cancel -> "{\"t\":\"cancel\"}"
    }

    private fun isHello(direction: Int, index: Int) = direction == TO_BROWSER && index == 1

    /**
     * Seals a message into a frame. The hello (index 1 to the browser) starts with the phone's
     * public key in the clear, since the browser needs it to derive the keys.
     */
    fun sealFrame(
        keys: PairingKeys,
        channel: ByteArray,
        direction: Int,
        index: Int,
        message: PairingMessage,
        phonePublicKey: ByteArray? = null,
        nonce: ByteArray = SyncCrypto.randomBytes(12),
    ): ByteArray {
        require(isHello(direction, index) == (phonePublicKey != null)) { "Exactly the hello carries the phone's key" }
        val key = if (direction == TO_BROWSER) keys.toBrowser else keys.toPhone
        val sealed = SyncCrypto.seal(key, nonce, aad(channel, direction, index), utf8(json(message)))
        return concat(phonePublicKey ?: ByteArray(0), nonce, sealed)
    }

    /** Opens a frame expected at this direction and index, and checks the message in it. */
    fun openFrame(keys: PairingKeys, channel: ByteArray, direction: Int, index: Int, frame: ByteArray): PairingMessage {
        val start = if (isHello(direction, index)) 65 else 0
        if (frame.size < start + 12 + 16)
            throw BrowserSyncException(Reason.BAD_MESSAGE, "The frame is too short")
        val key = if (direction == TO_BROWSER) keys.toBrowser else keys.toPhone
        val plaintext = SyncCrypto.open(
            key,
            frame.copyOfRange(start, start + 12),
            aad(channel, direction, index),
            frame.copyOfRange(start + 12, frame.size),
        )
        val json = try {
            JSONObject(SyncEncoding.fromUtf8(plaintext))
        } catch (e: JSONException) {
            throw BrowserSyncException(Reason.BAD_MESSAGE, "The message is not JSON")
        }
        return when (json.optString("t")) {
            "hello" -> PairingMessage.Hello(checkName(json.optString("phone", null), "The phone's name"))
            "vault" -> {
                val id = json.optString("id", null)
                val read = json.optString("read", null)
                val key = json.optString("key", null)
                SyncEncoding.fromBase64Url(id, 16, "The vault id")
                SyncEncoding.fromBase64Url(read, 32, "The read token")
                SyncEncoding.fromBase64Url(key, 32, "The vault key")
                PairingMessage.Vault(id, read, key)
            }
            "confirm" -> PairingMessage.Confirm
            "done" -> PairingMessage.Done
            "cancel" -> PairingMessage.Cancel
            else -> throw BrowserSyncException(Reason.BAD_MESSAGE, "Unknown message")
        }
    }
}
