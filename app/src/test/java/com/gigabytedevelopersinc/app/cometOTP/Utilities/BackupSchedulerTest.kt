@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Utilities

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone
import java.util.concurrent.TimeUnit

class BackupSchedulerTest {

    private val utc = TimeZone.getTimeZone("UTC")
    private val london = TimeZone.getTimeZone("Europe/London")

    private fun hours(h: Long) = TimeUnit.HOURS.toMillis(h)
    private fun minutes(m: Long) = TimeUnit.MINUTES.toMillis(m)

    @Test
    fun shortIntervalsStartRightAway() {
        assertEquals(0, BackupScheduler.initialDelay(T_2026_09_28_1000_UTC, utc, 120, 6 * 60))
        assertEquals(0, BackupScheduler.initialDelay(T_2026_09_28_1000_UTC, utc, 120, 15))
    }

    @Test
    fun dailyStartsAtTheNextPreferredTime() {
        // 10:00 now: 14:30 is later today, 02:00 is tomorrow.
        assertEquals(hours(4) + minutes(30), BackupScheduler.initialDelay(T_2026_09_28_1000_UTC, utc, 14 * 60 + 30, 1440))
        assertEquals(hours(16), BackupScheduler.initialDelay(T_2026_09_28_1000_UTC, utc, 2 * 60, 1440))
        // Weekly uses the same time of day.
        assertEquals(hours(16), BackupScheduler.initialDelay(T_2026_09_28_1000_UTC, utc, 2 * 60, 7 * 1440))
    }

    @Test
    fun aTimeThatIsAboutToPassMovesToTomorrow() {
        // 10:00 preferred at 10:00 would run straight away; it is tomorrow's.
        assertEquals(hours(24), BackupScheduler.initialDelay(T_2026_09_28_1000_UTC, utc, 10 * 60, 1440))
    }

    @Test
    fun theWallClockTimeIsKeptOverADaylightSavingChange() {
        // Saturday 28 March 2026, 22:00 GMT. The clocks go forward at 01:00 GMT, so 03:00 on
        // Sunday is 02:00 UTC: four hours away, not five.
        val saturdayEvening = 1774735200000L
        assertEquals(hours(4), BackupScheduler.initialDelay(saturdayEvening, london, 3 * 60, 1440))
    }

    @Test
    fun overdueOnceAnIntervalHasPassed() {
        val now = T_2026_09_28_1000_UTC
        assertTrue(BackupScheduler.isOverdue(0, 1440, now))
        assertFalse(BackupScheduler.isOverdue(now - hours(23), 1440, now))
        assertTrue(BackupScheduler.isOverdue(now - hours(24), 1440, now))
        // A backup "in the future" means the clock was turned back.
        assertTrue(BackupScheduler.isOverdue(now + hours(1), 1440, now))
    }

    @Test
    fun catchUpHonorsChargingButAutoSyncDoesNotWaitForIt() {
        assertFalse(BackupScheduler.foregroundConstraintsMet(onlyCharging = true, scheduled = true, charging = false))
        assertTrue(BackupScheduler.foregroundConstraintsMet(onlyCharging = true, scheduled = true, charging = true))
        assertTrue(BackupScheduler.foregroundConstraintsMet(onlyCharging = false, scheduled = true, charging = false))
        assertTrue(BackupScheduler.foregroundConstraintsMet(onlyCharging = true, scheduled = false, charging = false))
    }

    @Test
    fun foregroundDriveBackupsWaitForAnAllowedNetwork() {
        assertFalse(BackupScheduler.foregroundNetworkMet(onlyUnmetered = true, connected = true, unmetered = false))
        assertFalse(BackupScheduler.foregroundNetworkMet(onlyUnmetered = true, connected = false, unmetered = true))
        assertFalse(BackupScheduler.foregroundNetworkMet(onlyUnmetered = false, connected = false, unmetered = false))
        assertTrue(BackupScheduler.foregroundNetworkMet(onlyUnmetered = true, connected = true, unmetered = true))
        assertTrue(BackupScheduler.foregroundNetworkMet(onlyUnmetered = false, connected = true, unmetered = false))
    }

    companion object {
        /** Monday 28 September 2026, 10:00 UTC. */
        private const val T_2026_09_28_1000_UTC = 1790589600000L
    }
}
