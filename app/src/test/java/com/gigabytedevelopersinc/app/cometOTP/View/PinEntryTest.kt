@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.View

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PinEntryTest {

    private fun PinEntry.type(digits: String) = digits.forEach { append(it) }

    @Test
    fun fixedLengthIsCompleteAtItsLength() {
        val entry = PinEntry(4, 4, 16)
        entry.type("123")
        assertFalse(entry.isComplete)
        assertFalse(entry.canSubmit)

        entry.type("4")
        assertTrue(entry.isComplete)
        assertTrue(entry.canSubmit)
    }

    @Test
    fun fixedLengthTakesNoMoreDigits() {
        val entry = PinEntry(4, 4, 16)
        entry.type("12345")
        assertEquals(4, entry.count)
        assertEquals("1234", entry.take())
    }

    @Test
    fun fixedLengthMayBeLongerThanTheMaximum() {
        // A PIN set up elsewhere before the keypad capped its length still has to fit.
        val entry = PinEntry(20, 4, 16)
        entry.type("12345678901234567890")
        assertTrue(entry.isComplete)
    }

    @Test
    fun withoutALengthItNeedsTheMinimumAndStopsAtTheMaximum() {
        val entry = PinEntry(null, 4, 6)
        entry.type("123")
        assertFalse(entry.canSubmit)
        entry.type("4")
        assertTrue(entry.canSubmit)
        assertFalse(entry.isComplete)

        entry.type("5678")
        assertEquals(6, entry.count)
        assertEquals("123456", entry.take())
    }

    @Test
    fun rejectsAnythingButDigits() {
        val entry = PinEntry(null, 4, 16)
        for (c in listOf('a', ' ', '-', '٣', '\u0000'))
            assertFalse("$c", entry.append(c))
        assertEquals(0, entry.count)
    }

    @Test
    fun backspaceRemovesTheLastDigit() {
        val entry = PinEntry(null, 4, 16)
        assertFalse(entry.backspace())
        entry.type("1234")
        assertTrue(entry.backspace())
        entry.type("9")
        assertEquals("1239", entry.take())
    }

    @Test
    fun takeAndClearEmptyTheEntry() {
        val entry = PinEntry(4, 4, 16)
        entry.type("1234")
        assertEquals("1234", entry.take())
        assertEquals(0, entry.count)
        assertEquals("", entry.take())

        entry.type("56")
        entry.clear()
        assertEquals(0, entry.count)
        entry.type("7890")
        assertEquals("7890", entry.take())
    }

    @Test
    fun mapsDigitKeys() {
        for (i in 0..9) {
            assertEquals('0' + i, (pinKeyFor(KeyEvent.KEYCODE_0 + i, 0) as PinKey.Digit).digit)
            assertEquals('0' + i, (pinKeyFor(KeyEvent.KEYCODE_NUMPAD_0 + i, 0) as PinKey.Digit).digit)
        }
    }

    @Test
    fun mapsDigitsTypedOnOtherKeys() {
        // AZERTY: "1" is shift + the "&" key, which has no key code of its own.
        assertEquals('1', (pinKeyFor(KeyEvent.KEYCODE_UNKNOWN, '1'.code) as PinKey.Digit).digit)
        assertNull(pinKeyFor(KeyEvent.KEYCODE_A, 'a'.code))
    }

    @Test
    fun mapsEditingKeys() {
        assertEquals(PinKey.Backspace, pinKeyFor(KeyEvent.KEYCODE_DEL, 0))
        assertEquals(PinKey.Backspace, pinKeyFor(KeyEvent.KEYCODE_FORWARD_DEL, 0))
        assertEquals(PinKey.Submit, pinKeyFor(KeyEvent.KEYCODE_ENTER, '\n'.code))
        assertEquals(PinKey.Submit, pinKeyFor(KeyEvent.KEYCODE_NUMPAD_ENTER, 0))
    }
}
