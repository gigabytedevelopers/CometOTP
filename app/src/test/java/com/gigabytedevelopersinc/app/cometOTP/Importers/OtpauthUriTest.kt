@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Importers

import com.gigabytedevelopersinc.app.cometOTP.Database.Entry
import com.gigabytedevelopersinc.app.cometOTP.Utilities.TokenCalculator.HashAlgorithm
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OtpauthUriTest {

    private val hello = "Hello!".toByteArray(Charsets.US_ASCII)

    @Test
    fun recognisesOnlyOtpauthLinks() {
        assertTrue(OtpauthUri.isOtpauthUri("otpauth://totp/x?secret=JBSWY3DPEE"))
        assertTrue(OtpauthUri.isOtpauthUri("  OTPAUTH://hotp/x?secret=JBSWY3DPEE&counter=1"))
        assertFalse(OtpauthUri.isOtpauthUri("otpauth-migration://offline?data=x"))
        assertFalse(OtpauthUri.isOtpauthUri("https://example.com"))
        assertFalse(OtpauthUri.isOtpauthUri(null))
        assertNull(OtpauthUri.parse("https://example.com"))
    }

    @Test
    fun readsTheClassicExample() {
        val t = OtpauthUri.parse("otpauth://totp/Example:alice@google.com?secret=JBSWY3DPEHPK3PXP&issuer=Example")!!
        assertEquals(Entry.OTPType.TOTP, t.type)
        assertArrayEquals("Hello!Þ­¾ï".toByteArray(Charsets.ISO_8859_1), t.secret)
        assertEquals("Example", t.issuer)
        assertEquals("alice@google.com", t.label)
        assertEquals(HashAlgorithm.SHA1, t.algorithm)
        assertEquals(6, t.digits)
        assertEquals(30, t.period)
        assertEquals(0L, t.counter)
    }

    @Test
    fun readsEveryParameter() {
        val t = OtpauthUri.parse("otpauth://hotp/ACME%20Co:john.doe%40email.com?secret=JBSWY3DPEE&issuer=ACME%20Co&algorithm=SHA256&digits=8&counter=99")!!
        assertEquals(Entry.OTPType.HOTP, t.type)
        assertArrayEquals(hello, t.secret)
        assertEquals("ACME Co", t.issuer)
        assertEquals("john.doe@email.com", t.label)
        assertEquals(HashAlgorithm.SHA256, t.algorithm)
        assertEquals(8, t.digits)
        assertEquals(99L, t.counter)
    }

    @Test
    fun takesTheIssuerFromTheLabelWhenThereIsNoParameter() {
        val t = OtpauthUri.parse("otpauth://totp/GitHub:bob?secret=JBSWY3DPEE&period=60")!!
        assertEquals("GitHub", t.issuer)
        assertEquals("bob", t.label)
        assertEquals(60, t.period)

        val plain = OtpauthUri.parse("otpauth://totp/bob?secret=JBSWY3DPEE")!!
        assertEquals("", plain.issuer)
        assertEquals("bob", plain.label)
    }

    @Test
    fun decodesPlusAndUnicodeInTheLabel() {
        val t = OtpauthUri.parse("otpauth://totp/My+Bank:%C3%A9mile?secret=JBSWY3DPEE&issuer=My+Bank")!!
        assertEquals("My Bank", t.issuer)
        assertEquals("émile", t.label)

        // An escape that is not two hex digits is kept as it is.
        assertEquals("100%", OtpauthUri.percentDecode("100%"))
        assertEquals("a%zz", OtpauthUri.percentDecode("a%zz"))
        assertEquals("🔐 lock", OtpauthUri.percentDecode("🔐+lock"))
    }

    @Test
    fun steamDefaultsToFiveDigitsAndMotpKeepsItsSecretAsText() {
        val steam = OtpauthUri.parse("otpauth://steam/Steam:gamer?secret=JBSWY3DPEE&issuer=Steam")!!
        assertEquals(Entry.OTPType.STEAM, steam.type)
        assertEquals(5, steam.digits)

        val motp = OtpauthUri.parse("otpauth://motp/VPN:me?secret=0123456789abcdef")!!
        assertEquals(Entry.OTPType.MOTP, motp.type)
        assertEquals("0123456789abcdef", String(motp.secret!!))
    }

    @Test
    fun leavesWhatItCannotUseNullRatherThanFailing() {
        val unknownType = OtpauthUri.parse("otpauth://yandex/x?secret=JBSWY3DPEE")!!
        assertNull(unknownType.type)
        assertEquals("x", unknownType.label)

        val md5 = OtpauthUri.parse("otpauth://totp/x?secret=JBSWY3DPEE&algorithm=MD5")!!
        assertNull(md5.algorithm)

        val badSecret = OtpauthUri.parse("otpauth://totp/x?secret=not-base32!")!!
        assertNull(badSecret.secret)

        val noSecret = OtpauthUri.parse("otpauth://totp/x")!!
        assertArrayEquals(ByteArray(0), noSecret.secret)

        val badDigits = OtpauthUri.parse("otpauth://totp/x?secret=JBSWY3DPEE&digits=six")!!
        assertEquals(6, badDigits.digits)
    }

    @Test
    fun readsOneLinkPerLineAndIgnoresTheRest() {
        val text = """
            # exported codes
            otpauth://totp/A:a?secret=JBSWY3DPEE

            otpauth://totp/B:b?secret=JBSWY3DPEE&digits=8
            https://example.com/not-a-code
        """.trimIndent()
        val tokens = OtpauthUri.parseAll(text)
        assertEquals(listOf("A", "B"), tokens.map { it.issuer })
        assertEquals(8, tokens[1].digits)
    }
}
