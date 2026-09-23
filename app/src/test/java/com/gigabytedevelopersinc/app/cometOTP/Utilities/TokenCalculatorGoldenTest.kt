@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Utilities

import com.gigabytedevelopersinc.app.cometOTP.Utilities.TokenCalculator.HashAlgorithm
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Golden values from the original Java TokenCalculator for the paths the RFC vectors in
 * [TokenCalculatorTest] do not reach: Steam's alphabet, mOTP, the String-returning TOTP and
 * zero-padding of short codes.
 *
 * The String TOTP and Steam functions read the clock. A period of Int.MAX_VALUE makes the time
 * step 0 until 2038, so their output is fixed and the offset selects the counter directly.
 */
class TokenCalculatorGoldenTest {

    private val key = "12345678901234567890".toByteArray(Charsets.US_ASCII)

    @Test
    fun constants() {
        assertEquals(30, TokenCalculator.TOTP_DEFAULT_PERIOD)
        assertEquals(6, TokenCalculator.TOTP_DEFAULT_DIGITS)
        assertEquals(1, TokenCalculator.HOTP_INITIAL_COUNTER)
        assertEquals(5, TokenCalculator.STEAM_DEFAULT_DIGITS)
        assertEquals(HashAlgorithm.SHA1, TokenCalculator.DEFAULT_ALGORITHM)
        assertEquals(listOf("SHA1", "SHA256", "SHA512"), HashAlgorithm.values().map { it.name })
    }

    @Test
    fun totpStringUsesTheClockAndPadsWithZeros() {
        assertEquals("755224", TokenCalculator.TOTP_RFC6238(key, Int.MAX_VALUE, 6, HashAlgorithm.SHA1, 0))
        assertEquals("27790413", TokenCalculator.TOTP_RFC6238(key, Int.MAX_VALUE, 8, HashAlgorithm.SHA256, -1))
    }

    @Test
    fun steamAlphabet() {
        assertEquals("GG5F5", TokenCalculator.TOTP_Steam(key, Int.MAX_VALUE, 5, HashAlgorithm.SHA1, 0))
        assertEquals("7W2CY", TokenCalculator.TOTP_Steam(key, Int.MAX_VALUE, 5, HashAlgorithm.SHA1, -1))
        assertEquals("PV9M4", TokenCalculator.TOTP_Steam(key, Int.MAX_VALUE, 5, HashAlgorithm.SHA1, 1))
        assertEquals("C9PRW", TokenCalculator.TOTP_Steam(key, Int.MAX_VALUE, 5, HashAlgorithm.SHA1, 7))
        // Past the value's significant characters the code is padded with the alphabet's first one.
        assertEquals("J4KK9V3222", TokenCalculator.TOTP_Steam(key, Int.MAX_VALUE, 10, HashAlgorithm.SHA512, 3))
    }

    @Test
    fun hotpDigitsAndAlgorithms() {
        assertEquals("66318745", TokenCalculator.HOTP(key, 123456789L, 8, HashAlgorithm.SHA256))
        assertEquals("1328862", TokenCalculator.HOTP(key, -5L, 7, HashAlgorithm.SHA512))
        // 10 digits: 10^10 overflows int, the cast saturates, and the code is zero-padded.
        assertEquals("0137359152", TokenCalculator.HOTP(key, 2L, 10, HashAlgorithm.SHA1))
    }

    @Test
    fun motp() {
        assertEquals("ac896a", TokenCalculator.MOTP("1234", "e3152afee62599c8", 1700000000L, 0))
        assertEquals("673285", TokenCalculator.MOTP("1234", "e3152afee62599c8", 1700000000L, -1))
        assertEquals("289473", TokenCalculator.MOTP("0000", "abcdef0123456789", 1700000009L, 1))
    }

    @Test
    fun formatTokenString() {
        assertEquals("000042", Tools.formatTokenString(42, 6))
        assertEquals("07081804", Tools.formatTokenString(7081804, 8))
        assertEquals("1234567", Tools.formatTokenString(1234567, 6))
    }
}
