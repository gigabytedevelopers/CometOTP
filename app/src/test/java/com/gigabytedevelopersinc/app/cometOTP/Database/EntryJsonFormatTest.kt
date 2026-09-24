@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Database

import com.gigabytedevelopersinc.app.cometOTP.Utilities.EntryThumbnail.EntryThumbnails
import com.gigabytedevelopersinc.app.cometOTP.Utilities.TokenCalculator.HashAlgorithm
import org.apache.commons.codec.binary.Hex
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.Locale

/**
 * Golden values for the entry JSON format: this is what the encrypted database and every backup
 * file contain. The expectations were produced by the original Java implementation of [Entry].
 * A failure here means saved accounts would read back differently - fix the code, never the
 * expected values.
 */
class EntryJsonFormatTest {

    @Test
    fun totpWithEveryOptionalField() {
        val json = """{"secret":"JBSWY3DPEHPK3PXP","issuer":"GitHub","label":"octocat","digits":6,
            "type":"TOTP","algorithm":"SHA1","thumbnail":"GitHub","last_used":1700000000123,
            "used_frequency":42,"period":30,"tags":["work","dev"]}"""
        val e = Entry(JSONObject(json))

        assertEquals(Entry.OTPType.TOTP, e.type)
        assertArrayEquals(unhex("48656c6c6f21deadbeef"), e.secret)
        assertEquals("GitHub", e.issuer)
        assertEquals("octocat", e.label)
        assertEquals(30, e.period)
        assertEquals(6, e.digits)
        assertEquals(0L, e.counter)
        assertEquals(HashAlgorithm.SHA1, e.algorithm)
        assertEquals(listOf("work", "dev"), e.tags)
        assertEquals(EntryThumbnails.GitHub, e.thumbnail)
        assertEquals(1700000000123L, e.lastUsed)
        assertEquals(42L, e.usedFrequency)
        assertEquals("JBSWY3DPEHPK3PXP", e.secretEncoded)

        assertJson(
            """{"thumbnail":"GitHub","period":30,"used_frequency":42,"last_used":1700000000123,
            "digits":6,"secret":"JBSWY3DPEHPK3PXP","label":"octocat","type":"TOTP","issuer":"GitHub",
            "algorithm":"SHA1","tags":["work","dev"]}""",
            e.toJSON()
        )
        assertRoundTrip(e)
    }

    @Test
    fun hotpWritesTheCounterAndNoPeriod() {
        val e = Entry(JSONObject("""{"secret":"GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ","issuer":"Bank",
            "label":"me","digits":8,"type":"HOTP","algorithm":"SHA512","counter":12345,
            "thumbnail":"amazon","tags":[]}"""))

        assertEquals(Entry.OTPType.HOTP, e.type)
        assertArrayEquals("12345678901234567890".toByteArray(Charsets.US_ASCII), e.secret)
        assertEquals(12345L, e.counter)
        assertEquals(30, e.period)
        assertEquals(8, e.digits)
        assertEquals(HashAlgorithm.SHA512, e.algorithm)
        // The stored thumbnail name is matched case-insensitively and re-saved canonically.
        assertEquals(EntryThumbnails.Amazon, e.thumbnail)

        assertJson(
            """{"thumbnail":"Amazon","used_frequency":0,"last_used":0,"digits":8,
            "secret":"GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ","label":"me","counter":12345,"type":"HOTP",
            "issuer":"Bank","algorithm":"SHA512","tags":[]}""",
            e.toJSON()
        )
        assertRoundTrip(e)
    }

    @Test
    fun hotpWithoutACounterIsRejected() {
        val error = assertThrows(Exception::class.java) {
            Entry(JSONObject("""{"secret":"JBSWY3DPEHPK3PXP","label":"h","type":"HOTP"}"""))
        }
        assertEquals("missing counter for HOTP", error.message)
    }

    /**
     * Steam entries write their period like TOTP does (they used to write neither period nor
     * counter, so a non-default Steam period was lost on every save).
     */
    @Test
    fun steamKeepsItsPaddedSecretAndWritesThePeriodButNoCounter() {
        val e = Entry(JSONObject("""{"secret":"GEZDGNBVGY======","issuer":"Steam","label":"gamer",
            "digits":5,"type":"STEAM","algorithm":"SHA1","thumbnail":"Steam","period":30}"""))

        assertEquals(Entry.OTPType.STEAM, e.type)
        assertArrayEquals(unhex("313233343536"), e.secret)
        assertEquals(5, e.digits)
        assertEquals(30, e.period)
        assertEquals(EntryThumbnails.Steam, e.thumbnail)
        assertEquals("GEZDGNBVGY======", e.secretEncoded)

        assertJson(
            """{"thumbnail":"Steam","period":30,"used_frequency":0,"last_used":0,"digits":5,
            "secret":"GEZDGNBVGY======","label":"gamer","type":"STEAM","issuer":"Steam",
            "algorithm":"SHA1","tags":[]}""",
            e.toJSON()
        )
        assertRoundTrip(e)
    }

    @Test
    fun steamWithANonDefaultPeriodKeepsItThroughASave() {
        val e = Entry(JSONObject("""{"secret":"GEZDGNBVGY======","issuer":"Steam","label":"gamer",
            "digits":5,"type":"STEAM","algorithm":"SHA1","thumbnail":"Steam","period":60}"""))
        assertEquals(60, e.period)
        assertEquals(60, e.toJSON().getInt("period"))
        assertEquals(60, Entry(JSONObject(e.toJSON().toString())).period)
        assertRoundTrip(e)
    }

    /** Steam entries saved before the period was written still load, with the default period. */
    @Test
    fun steamSavedWithoutAPeriodLoadsWithThirtySeconds() {
        val e = Entry(JSONObject("""{"secret":"GEZDGNBVGY======","issuer":"Steam","label":"gamer",
            "digits":5,"type":"STEAM","algorithm":"SHA1","thumbnail":"Steam"}"""))
        assertEquals(Entry.OTPType.STEAM, e.type)
        assertEquals(30, e.period)
        assertEquals(30, e.toJSON().getInt("period"))
    }

    /** A missing digits count defaults by type: Steam codes are 5 characters, like the URI reader. */
    @Test
    fun steamWithoutDigitsFallsBackToFive() {
        val e = Entry(JSONObject("""{"secret":"JBSWY3DPEHPK3PXP","label":"noperiod steam","type":"STEAM"}"""))
        assertEquals(Entry.OTPType.STEAM, e.type)
        assertEquals(5, e.digits)
        assertEquals(30, e.period)
        assertEquals(EntryThumbnails.Default, e.thumbnail)

        // A stored digits value still wins, and the other types keep the default of six.
        assertEquals(6, Entry(JSONObject("""{"secret":"JBSWY3DPEHPK3PXP","label":"s","type":"STEAM","digits":6}""")).digits)
        for (type in listOf("TOTP", "MOTP"))
            assertEquals(type, 6, Entry(JSONObject("""{"secret":"JBSWY3DPEHPK3PXP","label":"t","type":"$type"}""")).digits)
        assertEquals(6, Entry(JSONObject("""{"secret":"JBSWY3DPEHPK3PXP","label":"h","type":"HOTP","counter":1}""")).digits)
    }

    @Test
    fun motpStoresItsHexSecretAsBase32OfTheAsciiText() {
        val e = Entry(JSONObject("""{"secret":"MUZTCNJSMFTGKZJWGI2TSOLDHA======","issuer":"Corp",
            "label":"motp user","digits":6,"type":"MOTP","algorithm":"SHA1","thumbnail":"Default"}"""))

        assertEquals(Entry.OTPType.MOTP, e.type)
        assertArrayEquals("e3152afee62599c8".toByteArray(Charsets.US_ASCII), e.secret)
        // For mOTP the "encoded" secret shown to the user is the text itself, not Base32.
        assertEquals("e3152afee62599c8", e.secretEncoded)

        assertJson(
            """{"thumbnail":"Default","used_frequency":0,"last_used":0,"digits":6,
            "secret":"MUZTCNJSMFTGKZJWGI2TSOLDHA======","label":"motp user","type":"MOTP",
            "issuer":"Corp","algorithm":"SHA1","tags":[]}""",
            e.toJSON()
        )
        assertRoundTrip(e)
    }

    @Test
    fun legacyEntryWithoutIssuerTypeOrExtrasGetsTheDefaults() {
        val e = Entry(JSONObject("""{"secret":"jbswy3dpehpk3pxp","label":"Legacy account","period":30}"""))

        assertEquals(Entry.OTPType.TOTP, e.type)
        assertArrayEquals(unhex("48656c6c6f21deadbeef"), e.secret)
        assertEquals("", e.issuer)
        assertEquals("Legacy account", e.label)
        assertEquals(30, e.period)
        assertEquals(6, e.digits)
        assertEquals(HashAlgorithm.SHA1, e.algorithm)
        assertEquals(emptyList<String>(), e.tags)
        assertEquals(EntryThumbnails.Default, e.thumbnail)
        assertEquals(0L, e.lastUsed)
        assertEquals(0L, e.usedFrequency)

        // The lower-case secret is re-saved upper case, and the empty issuer is written out.
        assertJson(
            """{"thumbnail":"Default","period":30,"used_frequency":0,"last_used":0,"digits":6,
            "secret":"JBSWY3DPEHPK3PXP","label":"Legacy account","type":"TOTP","issuer":"",
            "algorithm":"SHA1","tags":[]}""",
            e.toJSON()
        )
        assertRoundTrip(e)
    }

    @Test
    fun totpWithoutAPeriodGetsThirtySeconds() {
        val e = Entry(JSONObject("""{"secret":"JBSWY3DPEHPK3PXP","label":"x","type":"TOTP"}"""))
        assertEquals(30, e.period)
    }

    @Test
    fun malformedOptionalFieldsFallBackOneByOne() {
        val e = Entry(JSONObject("""{"secret":"JBSWY3DPEHPK3PXP","issuer":"x","label":"weird",
            "type":"FOO","algorithm":"MD5","thumbnail":"NoSuchService","last_used":"abc",
            "used_frequency":"7","tags":"notarray","digits":"8","period":"45"}"""))

        assertEquals(Entry.OTPType.TOTP, e.type)
        assertEquals(HashAlgorithm.SHA1, e.algorithm)
        assertEquals(EntryThumbnails.Default, e.thumbnail)
        assertEquals(0L, e.lastUsed)
        assertEquals(7L, e.usedFrequency)
        assertEquals(emptyList<String>(), e.tags)
        assertEquals(8, e.digits)
        assertEquals(45, e.period)

        assertJson(
            """{"thumbnail":"Default","period":45,"used_frequency":7,"last_used":0,"digits":8,
            "secret":"JBSWY3DPEHPK3PXP","label":"weird","type":"TOTP","issuer":"x",
            "algorithm":"SHA1","tags":[]}""",
            e.toJSON()
        )
    }

    @Test
    fun sha256EntryAndDottedThumbnailName() {
        val e = Entry(JSONObject("""{"secret":"JBSWY3DPEHPK3PXP","label":"s256","type":"TOTP",
            "algorithm":"SHA256","period":60,"digits":8,"thumbnail":"web.de"}"""))

        assertEquals(HashAlgorithm.SHA256, e.algorithm)
        assertEquals(60, e.period)
        assertEquals(EntryThumbnails.WebDe, e.thumbnail)
        assertJson(
            """{"thumbnail":"WebDe","period":60,"used_frequency":0,"last_used":0,"digits":8,
            "secret":"JBSWY3DPEHPK3PXP","label":"s256","type":"TOTP","issuer":"",
            "algorithm":"SHA256","tags":[]}""",
            e.toJSON()
        )
        assertRoundTrip(e)
    }

    @Test
    fun secretAndLabelAreRequired() {
        assertThrows(JSONException::class.java) { Entry(JSONObject("""{"label":"nosecret"}""")) }
        assertThrows(JSONException::class.java) { Entry(JSONObject("""{"secret":"JBSWY3DPEHPK3PXP"}""")) }
    }

    /**
     * The secret is upper-cased with the device's default locale before Base32 decoding. Under a
     * Turkish locale "i" becomes a dotted capital I, which Base32 skips, so the decoded secret is
     * different. That is how the app has always read its data, so it is pinned here: a port that
     * switched to a locale-independent uppercase() would read these entries differently.
     */
    @Test
    fun secretIsUpperCasedWithTheDefaultLocale() {
        val json = """{"secret":"mfrgiz3i","label":"tr"}"""
        assertArrayEquals(unhex("6162646768"), Entry(JSONObject(json)).secret)

        val saved = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertArrayEquals(unhex("61626c"), Entry(JSONObject(json)).secret)
            val typed = Entry(Entry.OTPType.TOTP, "mfrgiz3i", 30, 6, "Github", "l",
                HashAlgorithm.SHA1, mutableListOf())
            assertArrayEquals(unhex("61626c"), typed.secret)
        } finally {
            Locale.setDefault(saved)
        }
    }

    private fun assertRoundTrip(original: Entry) {
        val json = original.toJSON()
        val reloaded = Entry(JSONObject(json.toString()))
        assertEquals(original, reloaded)
        assertArrayEquals(original.secret, reloaded.secret)
        assertEquals(original.tags, reloaded.tags)
        assertEquals(original.thumbnail, reloaded.thumbnail)
        assertEquals(original.lastUsed, reloaded.lastUsed)
        assertEquals(original.usedFrequency, reloaded.usedFrequency)
        assertJson(json.toString(), reloaded.toJSON())
    }

    companion object {
        /** Compares by parsed content: key order in the output is not part of the format. */
        fun assertJson(expected: String, actual: JSONObject) {
            val expectedMap = content(JSONObject(expected)) as Map<*, *>
            val actualMap = content(JSONObject(actual.toString())) as Map<*, *>
            assertEquals(expectedMap.keys, actualMap.keys)
            assertEquals(expectedMap, actualMap)
        }

        /**
         * Plain maps and lists for a parsed JSON value. (The unit tests compile against
         * android.jar's org.json, which has no toMap(), and run against the real library.)
         */
        fun content(value: Any?): Any? = when (value) {
            is JSONObject -> value.keys().asSequence().associateWith { content(value.get(it)) }
            is JSONArray -> (0 until value.length()).map { content(value.get(it)) }
            else -> value
        }

        fun unhex(hex: String): ByteArray = Hex.decodeHex(hex)
    }
}
