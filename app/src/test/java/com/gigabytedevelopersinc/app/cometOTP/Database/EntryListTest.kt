@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Database

import com.gigabytedevelopersinc.app.cometOTP.Utilities.Constants.SearchIncludes
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Constants.SortMode
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Constants.TagFunctionality
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Behaviour of [EntryList] as the original Java implementation had it. */
class EntryListTest {

    private fun entry(label: String, issuer: String, secret: String = "JBSWY3DPEHPK3PXP", tags: List<String> = emptyList(),
                      lastUsed: Long = 0, used: Long = 0): Entry =
        Entry(JSONObject().put("secret", secret).put("label", label).put("issuer", issuer)
            .put("tags", JSONArray(tags)).put("last_used", lastUsed).put("used_frequency", used))

    private fun labels(entries: List<Entry>) = entries.map { it.label }

    @Test
    fun addEntryHandsOutIdsAndIgnoresEqualEntries() {
        val list = EntryList()
        val a = entry("a", "x")
        val b = entry("b", "x")
        assertTrue(list.addEntry(a))
        assertTrue(list.addEntry(b))
        assertEquals(1L, a.listId)
        assertEquals(2L, b.listId)

        val aAgain = entry("a", "x")
        assertFalse(list.addEntry(aAgain))
        assertEquals(0L, aAgain.listId)
        assertSame(a, list.getEntry(0))

        // With update the equal entry replaces the old one in place and inherits its id.
        assertFalse(list.addEntry(aAgain, true))
        assertEquals(1L, aAgain.listId)
        assertSame(aAgain, list.getEntry(0))

        // Ids keep counting up; they are never reused.
        val c = entry("c", "x")
        list.addEntry(c)
        assertEquals(3L, c.listId)
    }

    @Test
    fun updateEntriesDropsMissingAndAddsNew() {
        val list = EntryList()
        val a = entry("a", "x")
        val b = entry("b", "x")
        list.addEntry(a)
        list.addEntry(b)

        val b2 = entry("b", "x")
        val c = entry("c", "x")
        list.updateEntries(arrayListOf(c, b2), true)
        assertEquals(listOf("b", "c"), labels(list.entries))
        assertSame(b2, list.getEntry(0))
        assertEquals(2L, b2.listId)
        assertEquals(3L, c.listId)
    }

    @Test
    fun basicListOperations() {
        val list = EntryList()
        val a = entry("a", "x")
        val b = entry("b", "x")
        val c = entry("c", "x")
        list.addEntry(a); list.addEntry(b); list.addEntry(c)

        list.swapEntries(0, 2)
        assertEquals(listOf("c", "b", "a"), labels(list.entries))
        assertEquals(2, list.indexOf(entry("a", "x")))
        list.removeEntry(1)
        assertEquals(listOf("c", "a"), labels(list.entries))

        assertTrue(list.isEqual(arrayListOf(entry("c", "x"), entry("a", "x"))))
        assertFalse(list.isEqual(arrayListOf(entry("a", "x"), entry("c", "x"))))

        val copy = list.entries
        copy.clear()
        assertEquals(2, list.entries.size)
    }

    @Test
    fun sortModes() {
        val list = EntryList()
        list.addEntry(entry("beta", "Zeta", secret = "AAAAAAAA", lastUsed = 10, used = 1))
        list.addEntry(entry("Alpha", "yankee", secret = "BBBBBBBB", lastUsed = 30, used = 1))
        list.addEntry(entry("alpha", "Xray", secret = "CCCCCCCC", lastUsed = 20, used = 5))
        list.addEntry(entry("gamma", "xray", secret = "DDDDDDDD", lastUsed = 20, used = 0))

        assertEquals(listOf("beta", "Alpha", "alpha", "gamma"), labels(list.getEntriesSorted(SortMode.UNSORTED)))
        // Primary-strength collation ignores case; ties keep their original order.
        assertEquals(listOf("Xray", "xray", "yankee", "Zeta"), list.getEntriesSorted(SortMode.ISSUER).map { it.issuer })
        assertEquals(listOf("Alpha", "alpha", "beta", "gamma"), labels(list.getEntriesSorted(SortMode.LABEL)))
        assertEquals(listOf("Alpha", "alpha", "gamma", "beta"), labels(list.getEntriesSorted(SortMode.LAST_USED)))
        assertEquals(listOf("alpha", "beta", "Alpha", "gamma"), labels(list.getEntriesSorted(SortMode.MOST_USED)))

        val sorted = EntryList.sortEntries(list.entries, SortMode.LABEL)
        assertEquals(listOf("Alpha", "alpha", "beta", "gamma"), labels(sorted))
    }

    @Test
    fun filteringBySearchText() {
        val list = EntryList()
        list.addEntry(entry("Work mail", "Google", secret = "AAAAAAAA", tags = listOf("Office")))
        list.addEntry(entry("home", "GitHub", secret = "BBBBBBBB", tags = listOf("private")))
        list.addEntry(entry("bank", "Chase", secret = "CCCCCCCC", tags = listOf("money", "OFFICE-2")))

        val all = listOf(SearchIncludes.LABEL, SearchIncludes.ISSUER, SearchIncludes.TAGS)
        assertEquals(listOf("Work mail", "home"), labels(list.getFilteredEntries("G", all, SortMode.UNSORTED)))
        assertEquals(listOf("Work mail", "bank"), labels(list.getFilteredEntries("office", all, SortMode.UNSORTED)))
        assertEquals(listOf("Work mail", "bank"), labels(list.getFilteredEntries("office", listOf(SearchIncludes.TAGS), SortMode.UNSORTED)))
        assertEquals(emptyList<String>(), labels(list.getFilteredEntries("office", listOf(SearchIncludes.LABEL), SortMode.UNSORTED)))
        assertEquals(listOf("home"), labels(list.getFilteredEntries("HUB", listOf(SearchIncludes.ISSUER), SortMode.UNSORTED)))
        assertEquals(listOf("bank", "home", "Work mail"), labels(list.getFilteredEntries("", all, SortMode.LABEL)))
        assertEquals(3, list.getFilteredEntries(null, all, SortMode.UNSORTED).size)

        // An empty search returns a copy, not the list's own storage.
        val unfiltered = list.getFilteredEntries(null, all, SortMode.UNSORTED)
        assertNotSame(unfiltered, list.getFilteredEntries(null, all, SortMode.UNSORTED))
        unfiltered.clear()
        assertEquals(3, list.entries.size)
    }

    /** An entry without a label used to crash the label search and the label sort. */
    @Test
    fun anEntryWithoutALabelCanBeSearchedAndSorted() {
        val list = EntryList()
        list.addEntry(entry("beta", "Bank", secret = "AAAAAAAA"))
        list.addEntry(Entry(Entry.OTPType.TOTP, "BBBBBBBB", 30, 6, "Nameless", null,
            com.gigabytedevelopersinc.app.cometOTP.Utilities.TokenCalculator.HashAlgorithm.SHA1, mutableListOf()))
        list.addEntry(entry("alpha", "Card", secret = "CCCCCCCC"))

        val all = listOf(SearchIncludes.LABEL, SearchIncludes.ISSUER, SearchIncludes.TAGS)
        assertEquals(listOf("beta"), labels(list.getFilteredEntries("bet", all, SortMode.UNSORTED)))
        assertEquals(listOf(null), labels(list.getFilteredEntries("nameless", all, SortMode.UNSORTED)))
        assertEquals(listOf(null, "alpha", "beta"), labels(list.getEntriesSorted(SortMode.LABEL)))
    }

    @Test
    fun filteringByTags() {
        val list = EntryList()
        list.addEntry(entry("none", "i", secret = "AAAAAAAA"))
        list.addEntry(entry("ab", "i", secret = "BBBBBBBB", tags = listOf("a", "b")))
        list.addEntry(entry("a", "i", secret = "CCCCCCCC", tags = listOf("a")))
        list.addEntry(entry("c", "i", secret = "DDDDDDDD", tags = listOf("c")))

        fun f(tags: List<String>, noTags: Boolean, mode: TagFunctionality) =
            labels(list.getEntriesFilteredByTags(tags, noTags, mode, SortMode.UNSORTED))

        assertEquals(listOf("ab", "a"), f(listOf("a"), false, TagFunctionality.OR))
        assertEquals(listOf("none", "ab", "a", "c"), f(listOf("a", "c"), true, TagFunctionality.OR))
        assertEquals(listOf("ab"), f(listOf("a", "b"), false, TagFunctionality.AND))
        assertEquals(listOf("none", "ab"), f(listOf("a", "b"), true, TagFunctionality.AND))
        // An empty selection matches everything in AND mode and only untagged entries otherwise.
        assertEquals(listOf("none", "ab", "a", "c"), f(emptyList(), false, TagFunctionality.AND))
        assertEquals(listOf("none"), f(emptyList(), true, TagFunctionality.SINGLE))
        assertEquals(emptyList<String>(), f(emptyList(), false, TagFunctionality.OR))

        assertEquals(setOf("a", "b", "c"), list.allTags.toSet())
        assertEquals(3, list.allTags.size)
    }
}
