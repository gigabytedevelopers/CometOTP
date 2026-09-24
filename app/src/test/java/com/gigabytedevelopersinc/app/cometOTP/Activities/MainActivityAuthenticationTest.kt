@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Activities

import com.gigabytedevelopersinc.app.cometOTP.Utilities.Constants.AuthMethod
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The main screen used to skip authentication whenever it had saved state, including saved state
 * restored after the process had been killed.
 */
class MainActivityAuthenticationTest {

    private val locks = listOf(AuthMethod.PASSWORD, AuthMethod.PIN, AuthMethod.DEVICE)
    private val thisProcess = "token-of-this-process"

    @Test
    fun asksOnAFreshStart() {
        for (method in locks)
            assertTrue(method.name, requiresAuthenticationOnCreate(method, null, thisProcess))
    }

    @Test
    fun asksWhenTheSavedStateOutlivedTheProcess() {
        for (method in locks)
            assertTrue(method.name, requiresAuthenticationOnCreate(method, "token-of-a-killed-process", thisProcess))
    }

    @Test
    fun doesNotAskAgainAfterARotation() {
        for (method in locks)
            assertFalse(method.name, requiresAuthenticationOnCreate(method, thisProcess, thisProcess))
    }

    @Test
    fun neverAsksWithoutALock() {
        assertFalse(requiresAuthenticationOnCreate(AuthMethod.NONE, null, thisProcess))
        assertFalse(requiresAuthenticationOnCreate(AuthMethod.NONE, "token-of-a-killed-process", thisProcess))
        assertFalse(requiresAuthenticationOnCreate(AuthMethod.NONE, thisProcess, thisProcess))
    }
}
