@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Database

import com.gigabytedevelopersinc.app.cometOTP.Database.EntryJsonFormatTest.Companion.assertJson
import com.gigabytedevelopersinc.app.cometOTP.Database.EntryJsonFormatTest.Companion.unhex
import com.gigabytedevelopersinc.app.cometOTP.Utilities.EntryThumbnail.EntryThumbnails
import com.gigabytedevelopersinc.app.cometOTP.Utilities.TokenCalculator.HashAlgorithm
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Objects

/**
 * Constructors, equality and token generation of [Entry], pinned against the original Java
 * implementation. The otpauth:// constructor and toUri() need android.net.Uri and are not covered
 * on the JVM.
 */
class EntryBehaviourTest {

    @Test
    fun defaultConstructorState() {
        val e = Entry()
        assertEquals(Entry.OTPType.TOTP, e.type)
        assertEquals(30, e.period)
        assertEquals(6, e.digits)
        assertEquals(HashAlgorithm.SHA1, e.algorithm)
        assertNull(e.secret)
        assertEquals(0L, e.counter)
        assertEquals("", e.issuer)
        assertNull(e.label)
        assertEquals(emptyList<String>(), e.tags)
        assertEquals(EntryThumbnails.Default, e.thumbnail)
        assertEquals(0, e.color)
        assertEquals("", e.pin)
        assertEquals(0L, e.listId)
        assertFalse(e.isVisible)
        assertNull(e.hideTask)
        assertNull(e.currentOTP)
        assertNull(e.prevOTP)
        assertEquals(0L, e.lastUsed)
        assertEquals(0L, e.usedFrequency)
    }

    @Test
    fun periodConstructorDecodesBase32AndFuzzyMatchesTheThumbnail() {
        val e = Entry(Entry.OTPType.TOTP, "jbswy3dpehpk3pxp", 45, 7, "My Google account", "me",
            HashAlgorithm.SHA256, mutableListOf("a"))
        assertEquals(Entry.OTPType.TOTP, e.type)
        assertArrayEquals(unhex("48656c6c6f21deadbeef"), e.secret)
        assertEquals(45, e.period)
        assertEquals(7, e.digits)
        assertEquals(0L, e.counter)
        assertEquals("My Google account", e.issuer)
        assertEquals("me", e.label)
        assertEquals(HashAlgorithm.SHA256, e.algorithm)
        assertEquals(listOf("a"), e.tags)
        assertEquals(EntryThumbnails.Google, e.thumbnail)
        assertTrue(e.hasNonDefaultPeriod())
    }

    @Test
    fun counterConstructorTurnsANullIssuerIntoEmpty() {
        val e = Entry(Entry.OTPType.HOTP, "jbswy3dpehpk3pxp", 9L, 6, null, "me", HashAlgorithm.SHA1, mutableListOf())
        assertEquals(9L, e.counter)
        assertEquals(30, e.period)
        assertEquals("", e.issuer)
        assertEquals(EntryThumbnails.Default, e.thumbnail)
        assertFalse(e.hasNonDefaultPeriod())
    }

    @Test
    fun motpConstructorKeepsTheSecretAsText() {
        val e = Entry(Entry.OTPType.MOTP, "abcdef0123456789", "Amazon", "m", mutableListOf())
        assertArrayEquals("abcdef0123456789".toByteArray(Charsets.US_ASCII), e.secret)
        assertEquals(30, e.period)
        assertEquals(6, e.digits)
        assertEquals(HashAlgorithm.SHA1, e.algorithm)
        assertEquals(EntryThumbnails.Amazon, e.thumbnail)
        assertEquals("abcdef0123456789", e.secretEncoded)
        assertJson(
            """{"thumbnail":"Amazon","used_frequency":0,"last_used":0,"digits":6,
            "secret":"MFRGGZDFMYYDCMRTGQ2TMNZYHE======","label":"m","type":"MOTP",
            "issuer":"Amazon","algorithm":"SHA1","tags":[]}""",
            e.toJSON()
        )
    }

    @Test
    fun setIssuerOnlyMovesTheThumbnailWhenAsked() {
        val e = Entry(JSONObject("""{"secret":"JBSWY3DPEHPK3PXP","label":"l","thumbnail":"Amazon"}"""))
        e.setIssuer("GitHub", false)
        assertEquals("GitHub", e.issuer)
        assertEquals(EntryThumbnails.Amazon, e.thumbnail)

        e.setIssuer("GitHub", true)
        assertEquals(EntryThumbnails.GitHub, e.thumbnail)

        e.setIssuer(null, true)
        assertEquals("", e.issuer)
        assertEquals(EntryThumbnails.Default, e.thumbnail)
    }

    @Test
    fun typeHelpers() {
        val e = Entry()
        for (type in Entry.OTPType.values()) {
            e.type = type
            assertEquals(type != Entry.OTPType.HOTP, e.isTimeBased)
            assertEquals(type == Entry.OTPType.HOTP, e.isCounterBased)
        }
    }

    @Test
    fun equalityCoversTheSecretAndParametersOnly() {
        val base = """{"secret":"JBSWY3DPEHPK3PXP","issuer":"i","label":"l","type":"TOTP","period":30,"digits":6,"algorithm":"SHA1"}"""
        val a = Entry(JSONObject(base))
        val b = Entry(JSONObject(base))
        assertEquals(a, b)
        assertEquals(a, a)
        assertNotEquals(a, null)
        assertNotEquals(a, "not an entry")

        // Presentation and usage data do not take part.
        b.tags = mutableListOf("x")
        b.thumbnail = EntryThumbnails.GitHub
        b.lastUsed = 5
        b.usedFrequency = 6
        b.color = Entry.COLOR_RED
        b.pin = "1234"
        b.listId = 99
        b.isVisible = true
        assertEquals(a, b)

        fun variant(change: String) = Entry(JSONObject(JSONObject(base).apply {
            val kv = JSONObject(change)
            for (k in kv.keys()) put(k, kv.get(k))
        }.toString()))
        assertNotEquals(a, variant("""{"secret":"JBSWY3DPEHPK3PXQ"}"""))
        assertNotEquals(a, variant("""{"issuer":"j"}"""))
        assertNotEquals(a, variant("""{"label":"m"}"""))
        assertNotEquals(a, variant("""{"type":"STEAM"}"""))
        assertNotEquals(a, variant("""{"period":60}"""))
        assertNotEquals(a, variant("""{"digits":8}"""))
        assertNotEquals(a, variant("""{"algorithm":"SHA256"}"""))
        assertNotEquals(a, variant("""{"counter":1}"""))
    }

    /** Equal entries must have equal hash codes, so the secret is hashed by content. */
    @Test
    fun equalEntriesHaveEqualHashCodes() {
        val base = """{"secret":"JBSWY3DPEHPK3PXP","issuer":"i","label":"l","type":"TOTP","period":30,"digits":6,"algorithm":"SHA1"}"""
        val a = Entry(JSONObject(base))
        val b = Entry(JSONObject(base))
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertTrue(hashSetOf(a).contains(b))

        val e = Entry(Entry.OTPType.HOTP, "jbswy3dpehpk3pxp", 9L, 6, "i", "me", HashAlgorithm.SHA1, mutableListOf())
        val expected = Objects.hash(e.type, e.period, e.counter, e.digits, e.algorithm, e.secret.contentHashCode(), e.label, e.issuer)
        assertEquals(expected, e.hashCode())

        // An entry without a secret (the no-argument constructor) still hashes.
        assertEquals(Entry().hashCode(), Entry().hashCode())
    }

    /*
     * Token generation. A period of Int.MAX_VALUE makes the time step 0 until 2038, so the current
     * code is the HOTP value for counter 0 and the previous one for counter -1.
     */

    @Test
    fun totpUpdateComputesCurrentAndPreviousAndSkipsUntilTheNextStep() {
        val e = Entry(Entry.OTPType.TOTP, "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", Int.MAX_VALUE, 6, "i", "l",
            HashAlgorithm.SHA1, mutableListOf())
        assertTrue(e.updateOTP(true))
        assertEquals("755224", e.currentOTP)
        assertEquals("094451", e.prevOTP)

        assertFalse(e.updateOTP(false))
        assertEquals("755224", e.currentOTP)
        assertEquals("094451", e.prevOTP)

        // A forced update shifts the current code into "previous" rather than recomputing it.
        assertTrue(e.updateOTP(true))
        assertEquals("755224", e.currentOTP)
        assertEquals("755224", e.prevOTP)
    }

    @Test
    fun steamUpdateUsesTheSteamAlphabet() {
        val e = Entry(Entry.OTPType.STEAM, "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", Int.MAX_VALUE, 5, "Steam", "l",
            HashAlgorithm.SHA1, mutableListOf())
        assertTrue(e.updateOTP(true))
        assertEquals("GG5F5", e.currentOTP)
        assertEquals("7W2CY", e.prevOTP)
    }

    @Test
    fun hotpUpdateAlwaysRecomputesAndHasNoPreviousCode() {
        val e = Entry(Entry.OTPType.HOTP, "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", 3L, 6, "i", "l",
            HashAlgorithm.SHA1, mutableListOf())
        assertTrue(e.updateOTP(false))
        assertEquals("969429", e.currentOTP)
        assertNull(e.prevOTP)
        e.counter = 4
        assertTrue(e.updateOTP(false))
        assertEquals("338314", e.currentOTP)
    }

    @Test
    fun motpWithoutAPinShowsThePlaceholder() {
        val e = Entry(Entry.OTPType.MOTP, "abcdef0123456789", "i", "l", mutableListOf())
        assertTrue(e.updateOTP(true))
        assertEquals("PINREQ", e.currentOTP)
        assertEquals("", e.prevOTP)
    }

    @Test
    fun colourTurnsRedOnceNearExpiryAndResetsOnUpdate() {
        val e = Entry(Entry.OTPType.TOTP, "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", 1, 6, "i", "l",
            HashAlgorithm.SHA1, mutableListOf())
        // With a 1 s period every second is within the last 8 seconds of the step.
        assertTrue(e.hasColorChanged())
        assertEquals(Entry.COLOR_RED, e.color)
        assertFalse(e.hasColorChanged())
        e.updateOTP(true)
        assertEquals(0, e.color)

        val slow = Entry(Entry.OTPType.TOTP, "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", Int.MAX_VALUE, 6, "i", "l",
            HashAlgorithm.SHA1, mutableListOf())
        assertFalse(slow.hasColorChanged())
        assertEquals(0, slow.color)
    }

    @Test
    fun validateSecret() {
        assertTrue(Entry.validateSecret("JBSWY3DPEHPK3PXP", Entry.OTPType.TOTP))
        assertTrue(Entry.validateSecret("jbswy3dpehpk3pxp", Entry.OTPType.HOTP))
        assertTrue(Entry.validateSecret("abcdef0123456789", Entry.OTPType.MOTP))
        assertFalse(Entry.validateSecret("abc", Entry.OTPType.MOTP))
        assertFalse(Entry.validateSecret("xyz!", Entry.OTPType.MOTP))
    }
}
