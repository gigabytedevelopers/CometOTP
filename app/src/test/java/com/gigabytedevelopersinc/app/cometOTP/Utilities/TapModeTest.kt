@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Utilities

import com.gigabytedevelopersinc.app.cometOTP.Utilities.Constants.TapMode
import org.junit.Assert.assertEquals
import org.junit.Test

/** Which tap modes copy: those leave a card without its copy button (Settings.tapCopies). */
class TapModeTest {

    @Test
    fun onlyTheCopyModesCopy() {
        val copying = TapMode.entries.filter { it.copies }
        assertEquals(listOf(TapMode.COPY, TapMode.COPY_BACKGROUND), copying)
    }
}
