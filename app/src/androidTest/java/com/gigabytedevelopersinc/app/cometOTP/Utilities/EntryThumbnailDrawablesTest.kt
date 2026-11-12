@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Utilities

import androidx.appcompat.content.res.AppCompatResources
import androidx.test.platform.app.InstrumentationRegistry
import com.gigabytedevelopersinc.app.cometOTP.Utilities.EntryThumbnail.EntryThumbnails
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every service icon must load. A vector whose outline Android cannot parse would not crash the
 * app: getThumbnailGraphic catches the error and shows the launcher icon instead, which is easy to
 * miss among hundreds of icons. Only a device parses the outlines, so this runs on the emulator.
 */
class EntryThumbnailDrawablesTest {

    @Test
    fun everyIconLoadsAndHasASize() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val broken = ArrayList<String>()
        for (thumbnail in EntryThumbnails.values()) {
            if (thumbnail == EntryThumbnails.Default)
                continue
            try {
                val drawable = AppCompatResources.getDrawable(context, thumbnail.resource)
                if (drawable == null || drawable.minimumWidth <= 0 || drawable.minimumHeight <= 0)
                    broken.add("${thumbnail.name}: no size")
            } catch (e: Exception) {
                broken.add("${thumbnail.name}: ${e.message}")
            }
        }
        assertTrue("Icons that do not load:\n" + broken.joinToString("\n"), broken.isEmpty())
    }
}
