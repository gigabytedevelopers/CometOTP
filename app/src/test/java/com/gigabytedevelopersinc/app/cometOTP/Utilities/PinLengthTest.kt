@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Utilities

import com.gigabytedevelopersinc.app.cometOTP.Utilities.Constants.AuthMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The keypad unlocks as soon as the stored PIN length is typed, so a wrong one would lock the user out. */
class PinLengthTest {

    @Test
    fun onlyAPinHasItsLengthStored() {
        assertEquals(6, pinLengthToStore(AuthMethod.PIN, 6))
        for (method in listOf(AuthMethod.PASSWORD, AuthMethod.DEVICE, AuthMethod.NONE))
            assertNull("$method", pinLengthToStore(method, 6))
        assertNull(pinLengthToStore(AuthMethod.PIN, 0))
    }

    @Test
    fun theLengthIsReadBackForTheSameCredentials() {
        val stored = encodePinLength(6, "hash-of-the-pin")
        assertEquals(6, decodePinLength(stored, "hash-of-the-pin"))
    }

    @Test
    fun theHashMayComeBackWithOtherWhitespace() {
        // Base64.encodeToString ends the stored hash with a line break; the preferences file
        // returns it with indentation after it.
        val stored = encodePinLength(6, "hash-of-the-pin\n")
        assertEquals(6, decodePinLength(stored, "hash-of-the-pin\n    "))
    }

    @Test
    fun theLengthIsUnknownOnceTheCredentialsChangedWithoutIt() {
        val stored = encodePinLength(6, "hash-of-the-old-pin")
        assertNull(decodePinLength(stored, "hash-of-a-new-pin"))
        assertNull(decodePinLength(stored, ""))
        assertNull(decodePinLength(stored, "\n  "))
    }

    @Test
    fun anythingElseIsUnknown() {
        for (stored in listOf("", "6", ":", "x:${"h".hashCode()}", "0:${"h".hashCode()}", "-4:${"h".hashCode()}", "6:1:2"))
            assertNull(stored, decodePinLength(stored, "h"))
    }
}
