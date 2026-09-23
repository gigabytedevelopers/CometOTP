@file:Suppress("PackageName", "DEPRECATION", "OVERRIDE_DEPRECATION")
package com.gigabytedevelopersinc.app.cometOTP.Preferences

import android.content.Context
import android.content.res.TypedArray
import android.preference.DialogPreference
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.NumberPicker
import com.gigabytedevelopersinc.app.cometOTP.R

/**
 * Project - CometOTP
 * Created with Android Studio
 * Company: Gigabyte Developers
 * User: Emmanuel Nwokoma
 * Title: Founder and CEO
 * Day: Saturday, 23
 * Month: October
 * Year: 2021
 * Date: 23 Oct, 2021
 * Time: 6:56 AM
 * Desc: NumberPickerPreference
 * A [android.preference.Preference] that displays a number picker as a dialog.
 */
class NumberPickerPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet?,
    defStyleAttr: Int = android.R.attr.dialogPreferenceStyle
) : DialogPreference(context, attrs, defStyleAttr) {

    private val minValue: Int
    private val maxValue: Int
    private val wrapSelectorWheel: Boolean

    private lateinit var picker: NumberPicker

    var value: Int = 0
        set(value) {
            field = value
            persistInt(field)
            summary = field.toString()
        }

    init {
        val a = context.obtainStyledAttributes(attrs, R.styleable.NumberPickerPreference)
        minValue = a.getInteger(R.styleable.NumberPickerPreference_minValue, DEFAULT_MIN_VALUE)
        maxValue = a.getInteger(R.styleable.NumberPickerPreference_maxValue, DEFAULT_MAX_VALUE)
        wrapSelectorWheel = a.getBoolean(R.styleable.NumberPickerPreference_wrapSelectorWheel, DEFAULT_WRAP_SELECTOR_WHEEL)
        a.recycle()
    }

    override fun onCreateDialogView(): View {
        val layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        layoutParams.gravity = Gravity.CENTER

        picker = NumberPicker(context)
        picker.layoutParams = layoutParams

        val dialogView = FrameLayout(context)
        dialogView.addView(picker)

        return dialogView
    }

    override fun onBindDialogView(view: View) {
        super.onBindDialogView(view)
        picker.minValue = minValue
        picker.maxValue = maxValue
        picker.wrapSelectorWheel = wrapSelectorWheel
        picker.value = value
    }

    override fun onDialogClosed(positiveResult: Boolean) {
        if (positiveResult) {
            picker.clearFocus()
            val newValue = picker.value
            if (callChangeListener(newValue)) {
                value = newValue
            }
        }
    }

    override fun onGetDefaultValue(a: TypedArray, index: Int): Any? {
        return a.getInt(index, minValue)
    }

    override fun onSetInitialValue(restorePersistedValue: Boolean, defaultValue: Any?) {
        val initialValue = if (restorePersistedValue) getPersistedInt(minValue) else defaultValue as Int

        value = initialValue
        summary = initialValue.toString()
    }

    companion object {
        const val DEFAULT_MAX_VALUE = 100
        const val DEFAULT_MIN_VALUE = 0
        const val DEFAULT_WRAP_SELECTOR_WHEEL = true
    }
}
