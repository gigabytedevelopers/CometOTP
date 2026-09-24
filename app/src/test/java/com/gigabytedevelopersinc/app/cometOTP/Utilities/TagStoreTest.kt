@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Utilities

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Reading the stored tag metadata; Color.parseColor() is replaced by a simple hex parser. */
class TagStoreTest {

    private val color: (String) -> Int = { s ->
        if (s.startsWith("#") && s.length == 7) (0xFF000000 or s.substring(1).toLong(16)).toInt() else DEFAULT
    }

    @Test
    fun readsTheStoredFormat() {
        val all = TagStore.parse("""{"Important": {"color": "#2979FF", "count": true}, "Work": {}}""", color)
        assertEquals(setOf("Important", "Work"), all.keys)
        assertEquals(0xFF2979FF.toInt(), all["Important"]!!.color)
        assertTrue(all["Important"]!!.showCount)
        assertEquals(DEFAULT, all["Work"]!!.color)
        assertFalse(all["Work"]!!.showCount)
    }

    /**
     * A value that is not an object is skipped on its own. Reading used to stop there, and the
     * next change wrote the partial map back, deleting the metadata of the remaining tags.
     */
    @Test
    fun aBadValueIsSkippedAndTheRestAreKept() {
        val all = TagStore.parse(
            """{"a": {"color": "#000001"}, "b": "not an object", "c": null, "d": 5, "e": [], "f": {"color": "#000002", "count": true}}""",
            color)
        assertEquals(setOf("a", "f"), all.keys)
        assertEquals(0xFF000002.toInt(), all["f"]!!.color)
        assertTrue(all["f"]!!.showCount)
    }

    @Test
    fun emptyOrBrokenJsonIsAnEmptyMap() {
        assertTrue(TagStore.parse("", color).isEmpty())
        assertTrue(TagStore.parse(null, color).isEmpty())
        assertTrue(TagStore.parse("not json", color).isEmpty())
    }

    private companion object {
        const val DEFAULT = 0x12345678
    }
}
