@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.View

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.View
import android.view.accessibility.AccessibilityManager
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Constants

/**
 * Connects a [PinPadView] and a [PinDotsView] to the [PinEntry] being typed, and hands the PIN to
 * [onSubmit] as soon as it is complete, or once the user confirms one of no fixed length.
 *
 * After [onSubmit] the dots keep showing the digits until the caller moves on: [setBusy] while
 * the PIN is checked, [showError] when it is wrong, or [setLength] for the next step.
 */
class PinInput(private val pad: PinPadView, private val dots: PinDotsView) {
    var onSubmit: ((String) -> Unit)? = null

    /** Runs whenever a digit is typed or removed, e.g. to clear an error message. */
    var onChanged: (() -> Unit)? = null

    /**
     * Set, the keypad's bottom-left key asks for a biometric instead, whenever the PIN has a fixed
     * length (with none, that key is OK and the biometric is only offered by the caller).
     */
    var onBiometric: (() -> Unit)? = null
        set(value) {
            field = value
            updateActionKey()
        }

    private var busy = false

    private var entry = PinEntry(null, Constants.AUTH_MIN_PIN_LENGTH, Constants.AUTH_MAX_PIN_LENGTH)

    init {
        pad.onDigit = ::type
        pad.onBackspace = ::backspace
        pad.onClear = ::clear
        pad.onAction = ::action
        setLength(null, Constants.AUTH_MIN_PIN_LENGTH, Constants.AUTH_MAX_PIN_LENGTH)
    }

    /**
     * Starts a new entry of [length] digits, or of [minLength] to [maxLength] digits confirmed with
     * the keypad's OK key when the length is not known (null).
     */
    fun setLength(length: Int?, minLength: Int, maxLength: Int) {
        entry.clear()
        entry = PinEntry(length, minLength, maxLength)
        dots.setLength(length, minLength)
        updateActionKey()
        update()
    }

    /** Wipes the digits typed so far. */
    fun clear() {
        entry.clear()
        update()
    }

    /** Disables the keypad and pulses the dots while the PIN is checked. */
    fun setBusy(busy: Boolean) {
        this.busy = busy
        pad.isEnabled = !busy
        dots.setBusy(busy)
        if (!busy)
            update()
    }

    /** Turns the keypad on or off, e.g. off while wrong attempts lock the screen. */
    fun setEnabled(enabled: Boolean) {
        pad.isEnabled = enabled
    }

    /** Shakes the dots in the error colour, then clears them and runs [onEnd]. */
    fun showError(onEnd: () -> Unit = {}) {
        entry.clear()
        pad.isEnabled = false
        pad.performHapticFeedback(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.REJECT
            else HapticFeedbackConstants.LONG_PRESS
        )
        dots.showError {
            pad.isEnabled = true
            update()
            onEnd()
        }
    }

    /**
     * For the "block accessibility" setting. From Android 14 the keys are always marked sensitive
     * (see [PinPadView]), which hides them from every accessibility service but tools such as
     * screen readers; before that they are hidden from all of them. A running screen reader keeps
     * them, as its user could not unlock otherwise: unlike the system keyboard, which stayed
     * accessible when the old password field was hidden, the keypad is the only way to type.
     */
    fun blockAccessibility() {
        if (Build.VERSION.SDK_INT >= 34)
            return
        val accessibility = pad.context.getSystemService(AccessibilityManager::class.java)
        if (accessibility?.isTouchExplorationEnabled == true)
            return
        pad.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    }

    /** Types on the keypad with a hardware keyboard; true if the key was the keypad's. */
    fun handleKey(keyCode: Int, event: KeyEvent): Boolean {
        val key = pinKeyFor(keyCode, event.unicodeChar) ?: return false
        if (!pad.isEnabled)
            return true
        when (key) {
            is PinKey.Digit -> type(key.digit)
            PinKey.Backspace -> backspace()
            PinKey.Submit -> confirm()
        }
        return true
    }

    private fun type(digit: Char) {
        if (!entry.append(digit))
            return
        update()
        if (entry.isComplete)
            submit()
    }

    private fun backspace() {
        if (entry.backspace())
            update()
    }

    private fun action() {
        if (pad.actionKey == PinPadView.ActionKey.BIOMETRIC)
            onBiometric?.invoke()
        else
            confirm()
    }

    private fun updateActionKey() {
        pad.actionKey = when {
            entry.length == null -> PinPadView.ActionKey.CONFIRM
            onBiometric != null -> PinPadView.ActionKey.BIOMETRIC
            else -> PinPadView.ActionKey.NONE
        }
        update()
    }

    private fun confirm() {
        if (entry.length == null && entry.canSubmit)
            submit()
    }

    private fun submit() {
        // The dots go on showing the digits; the entry itself is wiped.
        onSubmit?.invoke(entry.take())
    }

    private fun update() {
        dots.filled = entry.count
        // OK once enough digits are typed; the biometric key whenever no PIN is being checked.
        pad.actionEnabled = if (pad.actionKey == PinPadView.ActionKey.BIOMETRIC) !busy else entry.canSubmit
        onChanged?.invoke()
    }
}
