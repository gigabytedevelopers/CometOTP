@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Dialogs

import com.gigabytedevelopersinc.app.cometOTP.Database.Entry
import com.gigabytedevelopersinc.app.cometOTP.Dialogs.BulkTagsDialog.Change
import com.gigabytedevelopersinc.app.cometOTP.Utilities.TokenCalculator.HashAlgorithm
import com.google.android.material.checkbox.MaterialCheckBox.STATE_CHECKED
import com.google.android.material.checkbox.MaterialCheckBox.STATE_INDETERMINATE
import com.google.android.material.checkbox.MaterialCheckBox.STATE_UNCHECKED
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tagging several entries at once in manage mode. */
class BulkTagsDialogTest {

    private fun entry(vararg tags: String) = Entry(Entry.OTPType.TOTP, "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", 30, 6,
        "i", "l", HashAlgorithm.SHA1, tags.toMutableList())

    @Test
    fun aTagStartsAtAllSomeOrNone() {
        assertEquals(STATE_CHECKED, BulkTagsDialog.initialState(3, 3))
        assertEquals(STATE_INDETERMINATE, BulkTagsDialog.initialState(1, 3))
        assertEquals(STATE_UNCHECKED, BulkTagsDialog.initialState(0, 3))
    }

    @Test
    fun aTagSomeCarryCyclesBackToSome() {
        var state = STATE_INDETERMINATE
        val seen = mutableListOf<Int>()
        repeat(3) {
            state = BulkTagsDialog.nextState(state, STATE_INDETERMINATE)
            seen.add(state)
        }
        assertEquals(listOf(STATE_CHECKED, STATE_UNCHECKED, STATE_INDETERMINATE), seen)
    }

    @Test
    fun aTagAllOrNoneCarryOnlyToggles() {
        assertEquals(STATE_UNCHECKED, BulkTagsDialog.nextState(STATE_CHECKED, STATE_CHECKED))
        assertEquals(STATE_CHECKED, BulkTagsDialog.nextState(STATE_UNCHECKED, STATE_CHECKED))
        assertEquals(STATE_CHECKED, BulkTagsDialog.nextState(STATE_UNCHECKED, STATE_UNCHECKED))
    }

    @Test
    fun eachStateMeansAChange() {
        assertEquals(Change.ADD, BulkTagsDialog.changeFor(STATE_CHECKED))
        assertEquals(Change.REMOVE, BulkTagsDialog.changeFor(STATE_UNCHECKED))
        assertEquals(Change.KEEP, BulkTagsDialog.changeFor(STATE_INDETERMINATE))
    }

    @Test
    fun appliesTheChangesToEveryEntry() {
        val work = entry("work", "old")
        val home = entry("home")
        val changed = BulkTagsDialog.apply(listOf(work, home),
            mapOf("work" to Change.ADD, "old" to Change.REMOVE, "home" to Change.KEEP))

        assertTrue(changed)
        assertEquals(listOf("work"), work.tags)
        assertEquals(listOf("home", "work"), home.tags)
    }

    @Test
    fun addsATagOnlyOnceAndReportsNoChange() {
        val work = entry("work")
        assertFalse(BulkTagsDialog.apply(listOf(work), mapOf("work" to Change.ADD, "x" to Change.REMOVE)))
        assertEquals(listOf("work"), work.tags)
    }
}
