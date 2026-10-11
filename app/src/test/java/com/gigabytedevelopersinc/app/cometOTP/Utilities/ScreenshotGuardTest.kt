@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Utilities

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Screenshots are allowed for the time chosen, and blocked again once it is up. */
class ScreenshotGuardTest {
    private val now = 1_760_000_000_000L
    private val minute = 60_000L

    @Test
    fun theTimeLimitCountsFromWhenScreenshotsAreAllowed() {
        assertEquals(now + 5 * minute, screenshotWindowEnd(5, now))
        assertEquals(now + 30 * minute, screenshotWindowEnd(30, now))
        assertEquals(now + 60 * minute, screenshotWindowEnd(60, now))
    }

    @Test
    fun untilTurnedOffHasNoEnd() {
        assertEquals(0L, screenshotWindowEnd(0, now))
        assertEquals(0L, screenshotWindowEnd(-1, now))
    }

    @Test
    fun allowedOnlyWhileSwitchedOnAndBeforeTheEnd() {
        val until = screenshotWindowEnd(30, now)
        assertTrue(screenshotsAllowedAt(true, until, now))
        assertTrue(screenshotsAllowedAt(true, until, until - 1))
        assertFalse(screenshotsAllowedAt(true, until, until))
        assertFalse(screenshotsAllowedAt(true, until, until + minute))
        assertFalse(screenshotsAllowedAt(false, until, now))
    }

    @Test
    fun withoutAnEndTheSwitchDecides() {
        assertTrue(screenshotsAllowedAt(true, 0, now))
        assertFalse(screenshotsAllowedAt(false, 0, now))
    }
}
