@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.View

import android.view.KeyEvent

/**
 * The digits of a PIN while it is typed on a [PinPadView].
 *
 * They are kept in a char array that is wiped as soon as they are handed over, never in an
 * Editable (which the system may keep copies of) and only turned into a String by [take].
 *
 * With a [length] the entry is complete as soon as that many digits are typed. Without one (null)
 * it takes up to [maxLength] digits and the user confirms it once it has at least [minLength].
 */
class PinEntry(val length: Int?, val minLength: Int, maxLength: Int) {
    private val digits = CharArray(length ?: maxLength)

    /** How many digits have been typed. */
    var count = 0
        private set

    init {
        require(length == null || length > 0) { "length must be positive" }
        require(minLength > 0 && minLength <= digits.size) { "minLength must fit" }
    }

    /** True once a fixed-length entry has all its digits. */
    val isComplete: Boolean
        get() = length != null && count == length

    /** True when the entry can be submitted: complete, or long enough without a fixed length. */
    val canSubmit: Boolean
        get() = if (length != null) isComplete else count >= minLength

    /** Adds [digit]; false if it was not taken (not a digit, or no room left). */
    fun append(digit: Char): Boolean {
        if (digit !in '0'..'9' || count >= digits.size)
            return false
        digits[count++] = digit
        return true
    }

    /** Removes the last digit; false if there was none. */
    fun backspace(): Boolean {
        if (count == 0)
            return false
        digits[--count] = ZERO
        return true
    }

    fun clear() {
        digits.fill(ZERO)
        count = 0
    }

    /** The digits typed so far, wiping them from this entry. */
    fun take(): String {
        val pin = String(digits, 0, count)
        clear()
        return pin
    }

    private companion object {
        const val ZERO = '\u0000'
    }
}

/** What a hardware key press means on the PIN keypad, see [pinKeyFor]. */
internal sealed class PinKey {
    class Digit(val digit: Char) : PinKey()
    object Backspace : PinKey()
    object Submit : PinKey()
}

/**
 * Maps a hardware key ([keyCode], and the character it types as [unicodeChar]) to the keypad, so
 * a PIN can also be typed on a keyboard or a Chromebook. Null for keys the keypad does not use.
 */
internal fun pinKeyFor(keyCode: Int, unicodeChar: Int): PinKey? {
    return when (keyCode) {
        in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> PinKey.Digit('0' + (keyCode - KeyEvent.KEYCODE_0))
        in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9 -> PinKey.Digit('0' + (keyCode - KeyEvent.KEYCODE_NUMPAD_0))
        KeyEvent.KEYCODE_DEL, KeyEvent.KEYCODE_FORWARD_DEL -> PinKey.Backspace
        KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> PinKey.Submit
        // Layouts with the digits on shifted keys (AZERTY) type them with other key codes.
        else -> if (unicodeChar in '0'.code..'9'.code) PinKey.Digit(unicodeChar.toChar()) else null
    }
}
