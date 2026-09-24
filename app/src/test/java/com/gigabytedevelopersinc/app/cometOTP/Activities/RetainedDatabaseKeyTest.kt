@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Activities

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The settings and security screens used to keep the database key in their saved instance state.
 * Kept in memory instead, a screen restored after the process was killed must not fall back to a
 * key that has been replaced since it was started.
 */
class RetainedDatabaseKeyTest {

    @Test
    fun freshStartUsesTheKeyItWasStartedWith() {
        assertEquals(Pair(DatabaseKeySource.LAUNCH_INTENT, false), initialDatabaseKeyState(false, null, false))
    }

    @Test
    fun configurationChangeKeepsTheKeyAndTheChangedFlag() {
        assertEquals(Pair(DatabaseKeySource.RETAINED, true), initialDatabaseKeyState(true, true, true))
        assertEquals(Pair(DatabaseKeySource.RETAINED, false), initialDatabaseKeyState(true, false, false))
    }

    @Test
    fun afterTheProcessWasKilledTheUnchangedLaunchKeyIsStillValid() {
        assertEquals(Pair(DatabaseKeySource.LAUNCH_INTENT, false), initialDatabaseKeyState(false, false, false))
    }

    @Test
    fun afterTheProcessWasKilledAChangedKeyIsNotReplacedWithTheStaleLaunchKey() {
        assertEquals(Pair(DatabaseKeySource.NONE, false), initialDatabaseKeyState(false, true, true))
        // Changed before an earlier kill: the flag is no longer set, the launch key still stale.
        assertEquals(Pair(DatabaseKeySource.NONE, false), initialDatabaseKeyState(false, false, true))
    }
}
