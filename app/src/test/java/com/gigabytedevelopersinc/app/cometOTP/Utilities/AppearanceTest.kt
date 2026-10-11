@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Utilities

import org.junit.Assert.assertEquals
import org.junit.Test

/** The theme picker maps onto the settings the app has always kept. */
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
}
