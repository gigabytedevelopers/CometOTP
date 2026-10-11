@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Utilities

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TokenCalculator touches no Android API, so its test vectors run on the JVM in a second or two
 * rather than waiting on an emulator. These are the published vectors: if this class ever stops
 * agreeing with them, every code the app shows is wrong.
 */
class TokenCalculatorTest {

    /** RFC 6238, appendix B. */
    @Test
    fun totpMatchesRfc6238() {
        assertTotp(94287082, 46119246, 90693936, 59L)
        assertTotp(7081804, 68084774, 25091201, 1111111109L)
        assertTotp(14050471, 67062674, 99943326, 1111111111L)
        assertTotp(89005924, 91819424, 93441116, 1234567890L)
        assertTotp(69279037, 90698825, 38618901, 2000000000L)
        assertTotp(65353130, 77737706, 47863826, 20000000000L)
    }

    /** The previous code is the one before it, which is what the card shows underneath. */
    @Test
    fun totpHonoursTheOffset() {
        assertEquals(84755224, TokenCalculator.TOTP_RFC6238(KEY_SHA1,
                TokenCalculator.TOTP_DEFAULT_PERIOD, 59L, 8, TokenCalculator.HashAlgorithm.SHA1, -1))
    }

    /** RFC 4226, appendix D. */
    @Test
    fun hotpMatchesRfc4226() {
        val expected = arrayOf("755224", "287082", "359152", "969429", "338314",
                               "254676", "287922", "162583", "399871", "520489")
        for (counter in expected.indices) {
            assertEquals("counter $counter", expected[counter],
                    TokenCalculator.HOTP(KEY_SHA1, counter.toLong(), 6, TokenCalculator.HashAlgorithm.SHA1))
        }
    }

    /** RFC 4226, appendix D, before truncation: the whole 31-bit value is a ten digit code. */
    @Test
    fun hotpKeepsAllTenDigits() {
        val expected = arrayOf("1284755224", "1094287082", "0137359152", "1726969429", "1640338314",
                               "0868254676", "1918287922", "0082162583", "0673399871", "0645520489")
        for (counter in expected.indices) {
            assertEquals("counter $counter", expected[counter],
                    TokenCalculator.HOTP(KEY_SHA1, counter.toLong(), 10, TokenCalculator.HashAlgorithm.SHA1))
        }
    }

    /** 10^10 does not fit in an Int; computed as one, it saturated and turned this value into 0. */
    @Test
    fun truncatesTheLargest31BitValue() {
        assertEquals(Int.MAX_VALUE, TokenCalculator.truncate(Int.MAX_VALUE, 10))
        assertEquals(147483647, TokenCalculator.truncate(Int.MAX_VALUE, 9))
        assertEquals(3647, TokenCalculator.truncate(Int.MAX_VALUE, 4))
        // Longer codes stored before the range was enforced keep the whole value, as before.
        assertEquals(Int.MAX_VALUE, TokenCalculator.truncate(Int.MAX_VALUE, 12))
        assertEquals("002147483647", Tools.formatTokenString(TokenCalculator.truncate(Int.MAX_VALUE, 12), 12))
    }

    @Test
    fun supportsFourToTenDigits() {
        assertEquals(4, TokenCalculator.SUPPORTED_DIGITS.first)
        assertEquals(10, TokenCalculator.SUPPORTED_DIGITS.last)
        assertFalse(3 in TokenCalculator.SUPPORTED_DIGITS)
        assertTrue(TokenCalculator.STEAM_DEFAULT_DIGITS in TokenCalculator.SUPPORTED_DIGITS)
        assertTrue(TokenCalculator.TOTP_DEFAULT_DIGITS in TokenCalculator.SUPPORTED_DIGITS)
        assertFalse(11 in TokenCalculator.SUPPORTED_DIGITS)
    }

    private fun assertTotp(sha1: Int, sha256: Int, sha512: Int, time: Long) {
        assertEquals("SHA1 at $time", sha1.toLong(), TokenCalculator.TOTP_RFC6238(
                KEY_SHA1, TokenCalculator.TOTP_DEFAULT_PERIOD, time, 8, TokenCalculator.HashAlgorithm.SHA1).toLong())
        assertEquals("SHA256 at $time", sha256.toLong(), TokenCalculator.TOTP_RFC6238(
                KEY_SHA256, TokenCalculator.TOTP_DEFAULT_PERIOD, time, 8, TokenCalculator.HashAlgorithm.SHA256).toLong())
        assertEquals("SHA512 at $time", sha512.toLong(), TokenCalculator.TOTP_RFC6238(
                KEY_SHA512, TokenCalculator.TOTP_DEFAULT_PERIOD, time, 8, TokenCalculator.HashAlgorithm.SHA512).toLong())
    }

    private companion object {
        val KEY_SHA1 = "12345678901234567890".toByteArray(Charsets.US_ASCII)
        val KEY_SHA256 = "12345678901234567890123456789012".toByteArray(Charsets.US_ASCII)
        val KEY_SHA512 = "1234567890123456789012345678901234567890123456789012345678901234".toByteArray(Charsets.US_ASCII)
    }
}
