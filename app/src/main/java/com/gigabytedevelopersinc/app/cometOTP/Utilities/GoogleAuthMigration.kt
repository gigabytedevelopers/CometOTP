@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Utilities

import com.gigabytedevelopersinc.app.cometOTP.Database.Entry
import com.gigabytedevelopersinc.app.cometOTP.Importers.ImportedToken
import com.gigabytedevelopersinc.app.cometOTP.Importers.TokenImport
import org.apache.commons.codec.binary.Base64
import java.io.ByteArrayOutputStream

/**
 * Reads the QR codes Google Authenticator shows under Transfer accounts > Export accounts.
 *
 * Each code holds `otpauth-migration://offline?data=<base64>`, and the data is a protobuf
 * `MigrationPayload`. Google has never published the schema; this follows the one that has been
 * documented from the app and that every other authenticator importing these codes uses:
 *
 * ```
 * message MigrationPayload {
 *   repeated OtpParameters otp_parameters = 1;
 *   int32 version = 2; int32 batch_size = 3; int32 batch_index = 4; int32 batch_id = 5;
 * }
 * message OtpParameters {
 *   bytes secret = 1; string name = 2; string issuer = 3;
 *   Algorithm algorithm = 4;   // 0 unspecified, 1 SHA1, 2 SHA256, 3 SHA512, 4 MD5
 *   DigitCount digits = 5;     // 0 unspecified, 1 six, 2 eight
 *   OtpType type = 6;          // 0 unspecified, 1 HOTP, 2 TOTP
 *   int64 counter = 7;
 * }
 * ```
 *
 * The decoder is written by hand rather than generated: the payload is a handful of scalar
 * fields, and a protobuf runtime would be a large dependency to carry for them. The tests check it
 * against payloads built by Google's own protobuf library from the schema above.
 */
object GoogleAuthMigration {

    const val SCHEME = "otpauth-migration"

    /** One account as Google Authenticator exported it, before any mapping. */
    class Account(
        val secret: ByteArray,
        val name: String,
        val issuer: String,
        val algorithm: Int,
        val digits: Int,
        val type: Int,
        val counter: Long
    )

    /**
     * One QR code's worth of accounts. An export of more than about ten accounts is split over
     * several codes that share a [batchId] and number themselves with [batchIndex] out of
     * [batchSize]; older exports leave all three at 0.
     */
    class Payload(
        val accounts: List<Account>,
        val version: Int,
        val batchSize: Int,
        val batchIndex: Int,
        val batchId: Int
    )

    fun isMigrationUri(text: String?): Boolean =
        text != null && text.trimStart().startsWith("$SCHEME://", ignoreCase = true)

    /**
     * Decodes one scanned code.
     *
     * @throws IllegalArgumentException when the text is not a Google Authenticator export code or
     *         its data is damaged.
     */
    fun parse(text: String): Payload {
        require(isMigrationUri(text)) { "Not an $SCHEME URI" }
        val data = queryParameter(text.trim(), "data")
        require(!data.isNullOrEmpty()) { "The code carries no data" }

        // Only %XX escapes are undone here. Form decoding would also turn '+' into a space, and
        // '+' is part of the base64 alphabet; a space that some other step made of one is put back.
        val base64 = percentDecode(data).replace(' ', '+')
        val bytes = Base64.decodeBase64(base64)
        require(bytes.isNotEmpty()) { "The data is not base64" }

        return readPayload(ProtoReader(bytes))
    }

    /**
     * Maps exported accounts onto entries, keeping their order. Accounts that CometOTP cannot
     * generate codes for are returned in [TokenImport.Converted.skipped] instead of being dropped
     * silently.
     */
    fun convert(accounts: List<Account>): TokenImport.Converted =
        TokenImport.convert(accounts.map { toToken(it) })

    /** Google Authenticator's enum values, read into the shape every import shares. */
    internal fun toToken(account: Account): ImportedToken {
        val (issuer, label) = TokenImport.splitName(account.name, account.issuer)
        val type = when (account.type) {
            TYPE_UNSPECIFIED, TYPE_TOTP -> Entry.OTPType.TOTP
            TYPE_HOTP -> Entry.OTPType.HOTP
            else -> null
        }
        // Digits outside the enum are passed on as a value CometOTP cannot use, so they are
        // reported as unsupported digits rather than silently read as six.
        val digits = digits(account.digits) ?: -1

        // The export has no period: Google Authenticator only supports 30 seconds.
        return ImportedToken(type, account.secret, issuer, label, algorithm(account.algorithm), digits,
                TokenCalculator.TOTP_DEFAULT_PERIOD, account.counter)
    }

    /* ------------------------------------------------------------------------------------------
     * Field values
     * ------------------------------------------------------------------------------------------ */

    private const val TYPE_UNSPECIFIED = 0
    private const val TYPE_HOTP = 1
    private const val TYPE_TOTP = 2

    /** Unspecified means SHA1, as it does in the Key URI format. MD5 (4) has no equivalent. */
    private fun algorithm(value: Int): TokenCalculator.HashAlgorithm? = when (value) {
        0, 1 -> TokenCalculator.HashAlgorithm.SHA1
        2 -> TokenCalculator.HashAlgorithm.SHA256
        3 -> TokenCalculator.HashAlgorithm.SHA512
        else -> null
    }

    private fun digits(value: Int): Int? = when (value) {
        0, 1 -> 6
        2 -> 8
        else -> null
    }

    /* ------------------------------------------------------------------------------------------
     * Protobuf
     * ------------------------------------------------------------------------------------------ */

    private const val WIRE_VARINT = 0
    private const val WIRE_FIXED64 = 1
    private const val WIRE_LENGTH_DELIMITED = 2
    private const val WIRE_FIXED32 = 5

    private fun readPayload(reader: ProtoReader): Payload {
        val accounts = ArrayList<Account>()
        var version = 0
        var batchSize = 0
        var batchIndex = 0
        var batchId = 0

        while (reader.hasMore) {
            val (field, wire) = reader.tag()
            when (field) {
                1 -> accounts.add(readAccount(reader.message(wire)))
                // int32 fields arrive as 64-bit varints, negative ones sign-extended; the low
                // 32 bits are the value. Google Authenticator's batch ids are often negative.
                2 -> version = reader.varint(wire).toInt()
                3 -> batchSize = reader.varint(wire).toInt()
                4 -> batchIndex = reader.varint(wire).toInt()
                5 -> batchId = reader.varint(wire).toInt()
                else -> reader.skip(wire)
            }
        }

        return Payload(accounts, version, batchSize, batchIndex, batchId)
    }

    private fun readAccount(reader: ProtoReader): Account {
        var secret = ByteArray(0)
        var name = ""
        var issuer = ""
        var algorithm = 0
        var digits = 0
        var type = 0
        var counter = 0L

        while (reader.hasMore) {
            val (field, wire) = reader.tag()
            when (field) {
                1 -> secret = reader.bytes(wire)
                2 -> name = reader.bytes(wire).toString(Charsets.UTF_8)
                3 -> issuer = reader.bytes(wire).toString(Charsets.UTF_8)
                4 -> algorithm = reader.varint(wire).toInt()
                5 -> digits = reader.varint(wire).toInt()
                6 -> type = reader.varint(wire).toInt()
                7 -> counter = reader.varint(wire)
                else -> reader.skip(wire)
            }
        }

        return Account(secret, name, issuer, algorithm, digits, type, counter)
    }

    private class ProtoReader(private val buf: ByteArray, private var pos: Int = 0, private val end: Int = buf.size) {
        val hasMore: Boolean
            get() = pos < end

        fun tag(): Pair<Int, Int> {
            val tag = rawVarint()
            val field = (tag ushr 3).toInt()
            require(field > 0) { "Invalid field number" }
            return field to (tag and 7).toInt()
        }

        fun varint(wire: Int): Long {
            require(wire == WIRE_VARINT) { "Expected a varint, got wire type $wire" }
            return rawVarint()
        }

        fun bytes(wire: Int): ByteArray {
            val length = lengthPrefix(wire)
            val out = buf.copyOfRange(pos, pos + length)
            pos += length
            return out
        }

        fun message(wire: Int): ProtoReader {
            val length = lengthPrefix(wire)
            val nested = ProtoReader(buf, pos, pos + length)
            pos += length
            return nested
        }

        fun skip(wire: Int) {
            when (wire) {
                WIRE_VARINT -> rawVarint()
                WIRE_FIXED64 -> advance(8)
                WIRE_LENGTH_DELIMITED -> advance(lengthPrefix(wire))
                WIRE_FIXED32 -> advance(4)
                else -> throw IllegalArgumentException("Unsupported wire type $wire")
            }
        }

        private fun lengthPrefix(wire: Int): Int {
            require(wire == WIRE_LENGTH_DELIMITED) { "Expected a length-delimited field, got wire type $wire" }
            val length = rawVarint()
            require(length >= 0 && length <= end - pos) { "Field runs past the end of the data" }
            return length.toInt()
        }

        private fun advance(count: Int) {
            require(count <= end - pos) { "Field runs past the end of the data" }
            pos += count
        }

        /** Up to ten bytes, seven bits each, least significant group first. */
        private fun rawVarint(): Long {
            var result = 0L
            var shift = 0
            while (shift < 64) {
                require(pos < end) { "The data ends inside a number" }
                val b = buf[pos++].toInt() and 0xFF
                result = result or ((b and 0x7F).toLong() shl shift)
                if (b and 0x80 == 0)
                    return result
                shift += 7
            }
            throw IllegalArgumentException("A number is longer than ten bytes")
        }
    }

    /* ------------------------------------------------------------------------------------------
     * URI
     * ------------------------------------------------------------------------------------------ */

    private fun queryParameter(uri: String, name: String): String? {
        val query = uri.substringAfter('?', "").substringBefore('#')
        return query.split('&')
            .firstOrNull { it.substringBefore('=') == name }
            ?.substringAfter('=', "")
    }

    /** The data is base64, so everything outside the %XX escapes is ASCII. */
    private fun percentDecode(s: String): String {
        if (!s.contains('%'))
            return s

        val out = ByteArrayOutputStream(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '%') {
                val hex = if (i + 2 < s.length) s.substring(i + 1, i + 3).toIntOrNull(16) else null
                require(hex != null) { "Invalid escape in the data" }
                out.write(hex)
                i += 3
            } else {
                out.write(c.code)
                i++
            }
        }
        return out.toString(Charsets.US_ASCII.name())
    }
}
