@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.BrowserSync

import com.gigabytedevelopersinc.app.cometOTP.BrowserSync.BrowserSyncException.Reason
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.util.Locale

/**
 * The browser sync protocol's encodings. java.util.Base64 only exists from Android 8 and
 * commons-codec's decoder skips characters it does not know, while the protocol requires invalid
 * input to be refused, so base64url is done here.
 */
object SyncEncoding {

    private const val BASE64URL = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

    /** Base64url (RFC 4648 §5) without padding. */
    fun toBase64Url(bytes: ByteArray): String {
        val out = StringBuilder((bytes.size * 4 + 2) / 3)
        var i = 0
        while (i < bytes.size) {
            val remaining = bytes.size - i
            val chunk = (bytes[i].toInt() and 0xff shl 16) or
                    ((if (remaining > 1) bytes[i + 1].toInt() and 0xff else 0) shl 8) or
                    (if (remaining > 2) bytes[i + 2].toInt() and 0xff else 0)
            val chars = minOf(4, (remaining * 8 + 5) / 6)
            for (j in 0 until chars)
                out.append(BASE64URL[(chunk ushr (18 - 6 * j)) and 63])
            i += 3
        }
        return out.toString()
    }

    /** Base64url without padding; padding, other characters and impossible lengths are refused. */
    fun fromBase64Url(text: String): ByteArray {
        if (text.length % 4 == 1)
            throw BrowserSyncException(Reason.BAD_ENCODING, "Invalid base64url length")
        val out = ByteArray(text.length * 6 / 8)
        var bits = 0
        var value = 0
        var index = 0
        for (char in text) {
            val digit = BASE64URL.indexOf(char)
            if (digit < 0)
                throw BrowserSyncException(Reason.BAD_ENCODING, "Invalid base64url character")
            value = ((value shl 6) or digit) and 0xffffff
            bits += 6
            if (bits >= 8) {
                bits -= 8
                out[index++] = (value ushr bits).toByte()
            }
        }
        return out
    }

    /** Decodes base64url and checks the length, for keys, tokens and ids. */
    fun fromBase64Url(text: String?, length: Int, what: String): ByteArray {
        if (text == null)
            throw BrowserSyncException(Reason.BAD_ENCODING, "$what is missing")
        val bytes = fromBase64Url(text)
        if (bytes.size != length)
            throw BrowserSyncException(Reason.BAD_ENCODING, "$what must be $length bytes")
        return bytes
    }

    fun utf8(text: String): ByteArray = text.toByteArray(Charsets.UTF_8)

    /** Decodes UTF-8, refusing invalid sequences rather than replacing them. */
    fun fromUtf8(bytes: ByteArray): String = try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    } catch (e: CharacterCodingException) {
        throw BrowserSyncException(Reason.BAD_ENCODING, "Invalid UTF-8")
    }

    fun concat(vararg parts: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(parts.sumOf { it.size })
        for (part in parts)
            out.write(part, 0, part.size)
        return out.toByteArray()
    }

    fun u8(n: Int): ByteArray = byteArrayOf(n.toByte())

    fun u32(n: Int): ByteArray = ByteBuffer.allocate(4).putInt(n).array()

    fun u64(n: Long): ByteArray = ByteBuffer.allocate(8).putLong(n).array()

    /** `u16(len(x)) ‖ x`. */
    fun lp(bytes: ByteArray): ByteArray {
        require(bytes.size <= 0xffff) { "Too long for a 16-bit length prefix" }
        return concat(byteArrayOf((bytes.size ushr 8).toByte(), bytes.size.toByte()), bytes)
    }

    /**
     * Decodes %XX escapes as UTF-8. Unlike URLDecoder, a `+` stays a `+`: the browser encodes with
     * encodeURIComponent, which never writes a space as `+`.
     */
    fun percentDecode(text: String): String {
        val bytes = ByteArrayOutputStream(text.length)
        var i = 0
        while (i < text.length) {
            if (text[i] != '%') {
                val end = text.indexOf('%', i).let { if (it < 0) text.length else it }
                val plain = utf8(text.substring(i, end))
                bytes.write(plain, 0, plain.size)
                i = end
                continue
            }
            val hex = text.substring(i + 1, minOf(i + 3, text.length))
            if (hex.length != 2 || !hex.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' })
                throw BrowserSyncException(Reason.BAD_URI, "Invalid percent-encoding")
            bytes.write(hex.toInt(16))
            i += 3
        }
        return fromUtf8(bytes.toByteArray())
    }

    /**
     * A JSON string literal, escaped as JavaScript's JSON.stringify does, so that what the phone
     * seals is byte for byte what the protocol's test vectors expect. org.json cannot be used to
     * write it: it escapes more characters, and on the JVM it does not keep the members' order.
     */
    fun jsonString(text: String): String {
        val out = StringBuilder(text.length + 2).append('"')
        for ((i, char) in text.withIndex()) {
            when {
                char == '"' -> out.append("\\\"")
                char == '\\' -> out.append("\\\\")
                char == '\b' -> out.append("\\b")
                char == '\u000c' -> out.append("\\f")
                char == '\n' -> out.append("\\n")
                char == '\r' -> out.append("\\r")
                char == '\t' -> out.append("\\t")
                char < ' ' -> out.append(String.format(Locale.ROOT, "\\u%04x", char.code))
                // A lone surrogate is not valid UTF-8, so JSON.stringify escapes it.
                char.isHighSurrogate() && !(i + 1 < text.length && text[i + 1].isLowSurrogate()) ->
                    out.append(String.format(Locale.ROOT, "\\u%04x", char.code))
                char.isLowSurrogate() && !(i > 0 && text[i - 1].isHighSurrogate()) ->
                    out.append(String.format(Locale.ROOT, "\\u%04x", char.code))
                else -> out.append(char)
            }
        }
        return out.append('"').toString()
    }
}
