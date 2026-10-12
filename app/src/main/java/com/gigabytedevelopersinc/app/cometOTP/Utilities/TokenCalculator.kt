@file:Suppress("PackageName", "FunctionName")
package com.gigabytedevelopersinc.app.cometOTP.Utilities

import org.apache.commons.codec.binary.Hex
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.security.InvalidKeyException
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object TokenCalculator {
    const val TOTP_DEFAULT_PERIOD = 30
    const val TOTP_DEFAULT_DIGITS = 6
    const val HOTP_INITIAL_COUNTER = 1
    const val STEAM_DEFAULT_DIGITS = 5
    /** mOTP codes change every ten seconds; unlike TOTP, the period is not configurable. */
    const val MOTP_PERIOD = 10

    /**
     * The code lengths CometOTP accepts, wherever an account comes from. RFC 4226's dynamic
     * truncation yields a 31-bit value (at most 2,147,483,647), so a code longer than ten digits
     * would only add leading zeros that no service expects.
     */
    val SUPPORTED_DIGITS = 4..10

    private val STEAMCHARS = charArrayOf(
        '2', '3', '4', '5', '6', '7', '8', '9', 'B', 'C',
        'D', 'F', 'G', 'H', 'J', 'K', 'M', 'N', 'P', 'Q',
        'R', 'T', 'V', 'W', 'X', 'Y'
    )

    enum class HashAlgorithm {
        SHA1, SHA256, SHA512
    }

    val DEFAULT_ALGORITHM = HashAlgorithm.SHA1

    @Throws(NoSuchAlgorithmException::class, InvalidKeyException::class)
    private fun generateHash(algorithm: HashAlgorithm, key: ByteArray, data: ByteArray): ByteArray {
        val algo = "Hmac$algorithm"

        val mac = Mac.getInstance(algo)
        mac.init(SecretKeySpec(key, algo))

        return mac.doFinal(data)
    }

    // TODO: Rewrite tests so this compatibility wrapper can be removed
    fun TOTP_RFC6238(secret: ByteArray, period: Int, time: Long, digits: Int, algorithm: HashAlgorithm): Int {
        return TOTP_RFC6238(secret, period, time, digits, algorithm, 0)
    }

    fun TOTP_RFC6238(secret: ByteArray, period: Int, time: Long, digits: Int, algorithm: HashAlgorithm, offset: Int): Int {
        return truncate(TOTP(secret, period, time, algorithm, offset), digits)
    }

    fun TOTP_RFC6238(secret: ByteArray, period: Int, digits: Int, algorithm: HashAlgorithm, offset: Int): String {
        return Tools.formatTokenString(TOTP_RFC6238(secret, period, System.currentTimeMillis() / 1000, digits, algorithm, offset), digits)
    }

    fun TOTP_Steam(secret: ByteArray, period: Int, digits: Int, algorithm: HashAlgorithm, offset: Int): String {
        var fullToken = TOTP(secret, period, System.currentTimeMillis() / 1000, algorithm, offset)

        val tokenBuilder = StringBuilder()

        for (i in 0 until digits) {
            tokenBuilder.append(STEAMCHARS[fullToken % STEAMCHARS.size])
            fullToken /= STEAMCHARS.size
        }

        return tokenBuilder.toString()
    }

    fun HOTP(secret: ByteArray, counter: Long, digits: Int, algorithm: HashAlgorithm): String {
        return Tools.formatTokenString(truncate(HOTP(secret, counter, algorithm), digits), digits)
    }

    /**
     * Keeps the last [digits] digits of a 31-bit HOTP value. The modulus is a Long because 10^10
     * does not fit in an Int; it stops growing at 10^10, which already exceeds any 31-bit value.
     */
    internal fun truncate(fullToken: Int, digits: Int): Int {
        var modulus = 1L
        repeat(digits.coerceAtMost(SUPPORTED_DIGITS.last)) { modulus *= 10 }
        return (fullToken % modulus).toInt()
    }

    private fun TOTP(key: ByteArray, period: Int, time: Long, algorithm: HashAlgorithm, offset: Int): Int {
        return HOTP(key, (time / period) + offset, algorithm)
    }

    private fun HOTP(key: ByteArray, counter: Long, algorithm: HashAlgorithm): Int {
        var r = 0

        try {
            val data = ByteBuffer.allocate(8).putLong(counter).array()
            val hash = generateHash(algorithm, key, data)

            val offset = hash[hash.size - 1].toInt() and 0xF

            var binary = (hash[offset].toInt() and 0x7F) shl 0x18
            binary = binary or ((hash[offset + 1].toInt() and 0xFF) shl 0x10)
            binary = binary or ((hash[offset + 2].toInt() and 0xFF) shl 0x08)
            binary = binary or (hash[offset + 3].toInt() and 0xFF)

            r = binary
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return r
    }

    fun MOTP(PIN: String, secret: String, epoch: Long, offset: Int): String {
        val epochText = ((epoch / MOTP_PERIOD) + offset).toString()
        val hashText = epochText + secret + PIN
        var otp = ""

        try {
            // Create MD5 Hash
            val digest = MessageDigest.getInstance("MD5")
            digest.update(hashText.toByteArray(Charset.defaultCharset()))
            val messageDigest = digest.digest()

            // Create Hex String
            val hexString = String(Hex.encodeHex(messageDigest))
            otp = hexString.substring(0, 6)
        } catch (e: NoSuchAlgorithmException) {
            e.printStackTrace()
        }

        return otp
    }
}
