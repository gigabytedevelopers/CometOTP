@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Utilities

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class UnlockThrottleTest {

    @Test
    fun theFirstWrongAttemptsAreFree() {
        for (failures in 0 until UnlockThrottle.FREE_ATTEMPTS)
            assertEquals("$failures", 0L, UnlockThrottle.lockoutAfter(failures))
    }

    @Test
    fun everyWrongAttemptAfterThemLocksForLonger() {
        val lockouts = (UnlockThrottle.FREE_ATTEMPTS until UnlockThrottle.FREE_ATTEMPTS + 5).map { UnlockThrottle.lockoutAfter(it) }
        assertEquals(listOf(30_000L, 60_000L, 300_000L, 900_000L, 1_800_000L), lockouts)
        assertEquals(1_800_000L, UnlockThrottle.lockoutAfter(100))
    }

    @Test
    fun countsTheAttemptsLeft() {
        assertEquals(5, UnlockThrottle.attemptsLeft(0))
        assertEquals(1, UnlockThrottle.attemptsLeft(4))
        assertEquals(0, UnlockThrottle.attemptsLeft(5))
        assertEquals(0, UnlockThrottle.attemptsLeft(9))
    }

    @Test
    fun aLockoutCountsDownOnTheSameBoot() {
        val lockout = Lockout(7, 100_000, 30_000)
        assertSame(lockout, lockoutAt(lockout, 7, 75_000))
        assertEquals(25_000L, remainingLockout(lockout, 75_000))
        assertEquals(0L, remainingLockout(lockout, 100_000))
        assertEquals(0L, remainingLockout(lockout, 500_000))
        assertEquals(0L, remainingLockout(null, 0))
    }

    @Test
    fun aRebootStartsTheLockoutOver() {
        // The elapsed-realtime clock starts at 0 again: a reboot must not end the lockout early.
        val restarted = lockoutAt(Lockout(7, 100_000, 30_000), 8, 5_000)
        assertEquals(Lockout(8, 35_000, 30_000), restarted)
        assertEquals(30_000L, remainingLockout(restarted, 5_000))
    }

    @Test
    fun aClockThatWentBackStartsTheLockoutOver() {
        // Without a boot count (-1) a reboot shows as a clock far behind the lockout's end; the
        // lockout must still end, rather than wait for the old boot's uptime.
        val restarted = lockoutAt(Lockout(-1, 9_000_000, 30_000), -1, 5_000)
        assertEquals(Lockout(-1, 35_000, 30_000), restarted)
    }

    @Test
    fun theStatusFollowsTheCount() {
        assertEquals(UnlockStatus.Clear, unlockStatus(0, 0))
        assertEquals(UnlockStatus.AttemptsLeft(4), unlockStatus(1, 0))
        assertEquals(UnlockStatus.AttemptsLeft(1), unlockStatus(4, 0))
        // After the first lockout, every wrong attempt locks again, for longer.
        assertEquals(UnlockStatus.NextLocks(60_000), unlockStatus(5, 0))
        assertEquals(UnlockStatus.Locked(12_000), unlockStatus(5, 12_000))
    }

    @Test
    fun storesLockouts() {
        val lockout = Lockout(-1, 1_234_567_890_123, 300_000)
        assertEquals(lockout, decodeLockout(encodeLockout(lockout)))
        for (stored in listOf("", "1:2", "a:2:3", "1:b:3", "1:2:0", "1:2:-5", "1:2:3:4"))
            assertNull(stored, decodeLockout(stored))
    }
}
