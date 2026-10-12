@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.View

import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.util.TypedValue
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat
import com.gigabytedevelopersinc.app.cometOTP.R
import com.google.android.material.button.MaterialButton

/**
 * The 3×4 keypad PINs are typed on instead of the system keyboard: 1-9, then an action key, 0 and
 * backspace. The action key confirms a PIN of no fixed length (see [actionKey]) and is empty
 * otherwise. Long-pressing backspace clears everything.
 *
 * The keys keep no state; [PinInput] connects them to a [PinEntry] and a [PinDotsView]. They grow
 * to a comfortable size and shrink to fit when there is less room (landscape, split screen).
 */
class PinPadView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ViewGroup(context, attrs, defStyleAttr) {

    /** The bottom-left key: none, OK for a PIN of no fixed length, or a biometric's. */
    enum class ActionKey { NONE, CONFIRM, BIOMETRIC }

    var onDigit: ((Char) -> Unit)? = null
    var onBackspace: (() -> Unit)? = null
    var onClear: (() -> Unit)? = null
    var onAction: (() -> Unit)? = null

    private val digitKeys: List<MaterialButton>
    private val actionButton: MaterialButton
    private val backspaceButton: MaterialButton

    private val maxCellWidth = resources.getDimension(R.dimen.pin_key_cell_width)
    private val maxCellHeight = resources.getDimension(R.dimen.pin_key_cell_height)
    private val keyGap = resources.getDimension(R.dimen.pin_key_gap)
    private val maxDigitTextSize: Float

    private var cellWidth = 0
    private var cellHeight = 0

    var actionKey = ActionKey.NONE
        set(value) {
            field = value
            updateActionKey()
        }

    /** Whether the action key can be pressed, e.g. once enough digits are typed. */
    var actionEnabled = false
        set(value) {
            field = value
            updateActionKey()
        }

    init {
        LayoutInflater.from(context).inflate(R.layout.view_pin_pad, this, true)

        digitKeys = DIGIT_KEY_IDS.map { findViewById(it) }
        actionButton = findViewById(R.id.pinKeyAction)
        backspaceButton = findViewById(R.id.pinKeyBackspace)
        maxDigitTextSize = digitKeys[0].textSize

        digitKeys.forEachIndexed { digit, key ->
            key.setOnClickListener {
                tap(it)
                onDigit?.invoke('0' + digit)
            }
        }
        backspaceButton.setOnClickListener {
            tap(it)
            onBackspace?.invoke()
        }
        backspaceButton.setOnLongClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            onClear?.invoke()
            true
        }
        ViewCompat.replaceAccessibilityAction(backspaceButton, AccessibilityActionCompat.ACTION_LONG_CLICK,
            context.getString(R.string.auth_pin_key_clear), null)
        actionButton.setOnClickListener {
            tap(it)
            onAction?.invoke()
        }

        // Taps that reach the keys through another app's overlay (tapjacking) are dropped.
        filterTouchesWhenObscured = true
        // Only accessibility tools that help the user (TalkBack) may see the keys being pressed.
        if (Build.VERSION.SDK_INT >= 34)
            setAccessibilityDataSensitive(ACCESSIBILITY_DATA_SENSITIVE_YES)
        updateActionKey()
    }

    private fun tap(key: View) {
        key.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    private fun updateActionKey() {
        actionButton.visibility = if (actionKey == ActionKey.NONE) INVISIBLE else VISIBLE
        when (actionKey) {
            ActionKey.CONFIRM -> {
                actionButton.setIconResource(R.drawable.ic_check)
                actionButton.contentDescription = context.getString(R.string.auth_pin_key_confirm)
                actionButton.isEnabled = actionEnabled && isEnabled
            }
            ActionKey.BIOMETRIC -> {
                actionButton.setIconResource(R.drawable.ic_fingerprint)
                actionButton.contentDescription = context.getString(R.string.auth_biometric_key)
                // Not with the digits: a biometric still unlocks while wrong PINs lock the keypad.
                actionButton.isEnabled = actionEnabled
            }
            ActionKey.NONE -> actionButton.isEnabled = false
        }
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        for (key in digitKeys)
            key.isEnabled = enabled
        backspaceButton.isEnabled = enabled
        updateActionKey()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        var cellW = maxCellWidth
        var cellH = maxCellHeight
        if (MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED)
            cellW = minOf(cellW, (width - paddingLeft - paddingRight) / COLUMNS.toFloat())
        if (MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.UNSPECIFIED)
            cellH = minOf(cellH, (height - paddingTop - paddingBottom) / ROWS.toFloat())
        cellWidth = cellW.toInt().coerceAtLeast(0)
        cellHeight = cellH.toInt().coerceAtLeast(0)

        // Keys as large as the cell allows with a gap around them; their pill shape makes them round.
        val key = (minOf(cellWidth, cellHeight) - keyGap).toInt().coerceAtLeast(0)
        val keySpec = MeasureSpec.makeMeasureSpec(key, MeasureSpec.EXACTLY)
        val textSize = minOf(maxDigitTextSize, key * DIGIT_TEXT_RATIO)
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child is MaterialButton && child in digitKeys)
                child.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize)
            child.measure(keySpec, keySpec)
        }

        val wantedWidth = cellWidth * COLUMNS + paddingLeft + paddingRight
        val wantedHeight = cellHeight * ROWS + paddingTop + paddingBottom
        setMeasuredDimension(resolveSize(wantedWidth, widthMeasureSpec), resolveSize(wantedHeight, heightMeasureSpec))
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        // The grid is centred in whatever space the keypad was given.
        val left = paddingLeft + (width - paddingLeft - paddingRight - cellWidth * COLUMNS) / 2
        val top = paddingTop + (height - paddingTop - paddingBottom - cellHeight * ROWS) / 2
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            val column = i % COLUMNS
            val row = i / COLUMNS
            val x = left + column * cellWidth + (cellWidth - child.measuredWidth) / 2
            val y = top + row * cellHeight + (cellHeight - child.measuredHeight) / 2
            child.layout(x, y, x + child.measuredWidth, y + child.measuredHeight)
        }
    }

    private companion object {
        const val COLUMNS = 3
        const val ROWS = 4
        const val DIGIT_TEXT_RATIO = 0.42f

        /** The digit keys by their digit, 0 to 9. */
        val DIGIT_KEY_IDS = listOf(
            R.id.pinKey0, R.id.pinKey1, R.id.pinKey2, R.id.pinKey3, R.id.pinKey4,
            R.id.pinKey5, R.id.pinKey6, R.id.pinKey7, R.id.pinKey8, R.id.pinKey9
        )
    }
}
