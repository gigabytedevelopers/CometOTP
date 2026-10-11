@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Utilities

import com.gigabytedevelopersinc.app.cometOTP.Utilities.Constants.TapMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which tap modes copy (those leave a card without its copy button, Settings.tapCopies), and what
 * a double tap may do beside a single tap.
 */
class TapModeTest {

    @Test
    fun onlyTheCopyModesCopy() {
        val copying = TapMode.entries.filter { it.copies }
        assertEquals(listOf(TapMode.COPY, TapMode.COPY_BACKGROUND), copying)
    }

    @Test
    fun aDoubleTapCannotDoWhatTheSingleTapDoes() {
        for (single in TapMode.entries) {
            for (double in TapMode.entries) {
                val allowed = double.allowedAsDoubleTap(single)
                if (double == TapMode.NOTHING)
                    assertTrue("Nothing is always allowed", allowed)
                else
                    assertEquals("$double after $single", double != single, allowed)
            }
        }
        assertFalse(TapMode.COPY.allowedAsDoubleTap(TapMode.COPY))
        assertTrue(TapMode.REVEAL.allowedAsDoubleTap(TapMode.COPY))
    }
}
