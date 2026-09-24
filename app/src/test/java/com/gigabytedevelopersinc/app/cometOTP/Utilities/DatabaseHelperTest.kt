@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Utilities

import com.gigabytedevelopersinc.app.cometOTP.Database.Entry
import com.gigabytedevelopersinc.app.cometOTP.Database.EntryJsonFormatTest.Companion.content
import com.gigabytedevelopersinc.app.cometOTP.Utilities.TokenCalculator.HashAlgorithm
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The plain-text database / backup body is a JSON array of entries. The file I/O around it needs
 * a Context and is not covered here; the string conversion is.
 */
class DatabaseHelperTest {

    private fun entries(): ArrayList<Entry> = arrayListOf(
        Entry(Entry.OTPType.TOTP, "jbswy3dpehpk3pxp", 45, 7, "My Google account", "me", HashAlgorithm.SHA256, mutableListOf("a")),
        Entry(Entry.OTPType.HOTP, "jbswy3dpehpk3pxp", 9L, 6, null, "me", HashAlgorithm.SHA1, mutableListOf()),
        Entry(Entry.OTPType.MOTP, "abcdef0123456789", "Amazon", "m", mutableListOf())
    )

    @Test
    fun entriesToStringGolden() {
        val actual = JSONArray(DatabaseHelper.entriesToString(entries()))
        val expected = JSONArray(GOLDEN)
        assertEquals(expected.length(), actual.length())
        for (i in 0 until expected.length())
            assertEquals("entry $i", content(expected.getJSONObject(i)), content(actual.getJSONObject(i)))
    }

    @Test
    fun stringToEntriesReadsTheGoldenString() {
        val loaded = DatabaseHelper.stringToEntries(GOLDEN)
        val original = entries()
        assertEquals(original, loaded)
        for (i in original.indices) {
            assertEquals(original[i].tags, loaded[i].tags)
            assertEquals(original[i].thumbnail, loaded[i].thumbnail)
        }
    }

    @Test
    fun emptyAndBrokenInput() {
        assertEquals(0, DatabaseHelper.stringToEntries("[]").size)
        assertEquals(0, DatabaseHelper.stringToEntries("").size)
        assertEquals(0, DatabaseHelper.stringToEntries("not json").size)
        assertEquals("[]", DatabaseHelper.entriesToString(ArrayList()))
    }

    /**
     * A bad entry is skipped on its own. (Loading used to stop at the first bad entry, and since
     * callers save what they load, every account after it was then deleted.)
     */
    @Test
    fun aBrokenEntryIsSkippedAndTheRestAreKept() {
        val data = """[{"secret":"JBSWY3DPEHPK3PXP","label":"first"},
            {"secret":"JBSWY3DPEHPK3PXP","label":"hotp without counter","type":"HOTP"},
            {"secret":"JBSWY3DPEHPK3PXP","label":"third"}]"""
        val loaded = DatabaseHelper.stringToEntries(data)
        assertEquals(listOf("first", "third"), loaded.map { it.label })
    }

    @Test
    fun brokenEntriesAnywhereInTheArrayAreSkipped() {
        val data = """[{"label":"no secret"},
            "not an object",
            {"secret":"JBSWY3DPEHPK3PXP","label":"second"},
            null,
            {"secret":"JBSWY3DPEHPK3PXP"},
            {"secret":"GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ","label":"last","type":"HOTP","counter":3}]"""
        val loaded = DatabaseHelper.stringToEntries(data)
        assertEquals(listOf("second", "last"), loaded.map { it.label })
        assertEquals(3L, loaded[1].counter)
    }

    @Test
    fun roundTrip() {
        val original = entries()
        original[0].lastUsed = 1700000000123
        original[0].usedFrequency = 3
        val text = DatabaseHelper.entriesToString(original)
        val loaded = DatabaseHelper.stringToEntries(text)
        assertEquals(original, loaded)
        assertEquals(1700000000123L, loaded[0].lastUsed)
        assertEquals(3L, loaded[0].usedFrequency)
        assertTrue(text.startsWith("[{"))
    }

    private companion object {
        const val GOLDEN = """[{"thumbnail":"Google","period":45,"used_frequency":0,"last_used":0,"digits":7,"secret":"JBSWY3DPEHPK3PXP","label":"me","type":"TOTP","issuer":"My Google account","algorithm":"SHA256","tags":["a"]},{"thumbnail":"Default","used_frequency":0,"last_used":0,"digits":6,"secret":"JBSWY3DPEHPK3PXP","label":"me","counter":9,"type":"HOTP","issuer":"","algorithm":"SHA1","tags":[]},{"thumbnail":"Amazon","used_frequency":0,"last_used":0,"digits":6,"secret":"MFRGGZDFMYYDCMRTGQ2TMNZYHE======","label":"m","type":"MOTP","issuer":"Amazon","algorithm":"SHA1","tags":[]}]"""
    }
}
