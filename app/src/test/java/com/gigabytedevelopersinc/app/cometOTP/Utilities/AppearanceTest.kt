@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Utilities

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The theme and list style pickers map onto the settings the app has always kept. */
class AppearanceTest {

    @Test
    fun autoFollowsTheSystemDarkMode() {
        assertEquals(ThemeChoice.LIGHT, resolveTheme("auto", "black", true, systemDark = false, autoSupported = true))
        assertEquals(ThemeChoice.DARK, resolveTheme("auto", "light", false, systemDark = true, autoSupported = true))
        assertEquals(ThemeChoice.BLACK, resolveTheme("auto", "light", true, systemDark = true, autoSupported = true))
    }

    @Test
    fun manualKeepsItsTheme() {
        for (dark in listOf(true, false)) {
            assertEquals(ThemeChoice.LIGHT, resolveTheme("manual", "light", true, dark, true))
            assertEquals(ThemeChoice.DARK, resolveTheme("manual", "dark", true, dark, true))
            assertEquals(ThemeChoice.BLACK, resolveTheme("manual", "black", false, dark, true))
        }
        assertEquals(ThemeChoice.LIGHT, resolveTheme("manual", "unknown", false, true, true))
    }

    @Test
    fun beforeAndroid10AutoFallsBackToTheManualTheme() {
        assertEquals(ThemeChoice.DARK, resolveTheme("auto", "dark", true, systemDark = false, autoSupported = false))
        assertEquals(ThemePick.DARK, themePickFor("auto", "dark", autoSupported = false))
    }

    @Test
    fun thePickerShowsTheStoredChoice() {
        assertEquals(ThemePick.AUTO, themePickFor("auto", "dark", true))
        assertEquals(ThemePick.LIGHT, themePickFor("manual", "light", true))
        assertEquals(ThemePick.DARK, themePickFor("manual", "dark", true))
        assertEquals(ThemePick.BLACK, themePickFor("manual", "black", true))
        assertEquals("auto", ThemePick.AUTO.value)
        assertEquals("black", ThemePick.BLACK.value)
    }

    @Test
    fun theDefaultListStyleIsTodaysSizes() {
        // settings_default_label_size and settings_default_thumbnail_size
        assertEquals(ListStyle.DEFAULT, ListStyle.of(18, "46dp"))
        assertEquals(ListStyle.LARGE, ListStyle.of(26, "46dp"))
        assertEquals(ListStyle.EXTRA_LARGE, ListStyle.of(30, "64dp"))
    }

    @Test
    fun theStylesFitTheLabelSizeSetting() {
        // settings_min_label_size and settings_max_label_size
        for (style in ListStyle.entries)
            assertTrue(style.labelSize in 12..32)
    }

    @Test
    fun sizesSetOneByOneMayMatchNoStyle() {
        assertNull(ListStyle.of(18, "64dp"))
        assertNull(ListStyle.of(20, "46dp"))
    }
}
