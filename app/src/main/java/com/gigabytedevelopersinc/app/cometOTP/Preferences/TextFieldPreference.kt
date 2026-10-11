@file:Suppress("PackageName", "DEPRECATION", "OVERRIDE_DEPRECATION")
package com.gigabytedevelopersinc.app.cometOTP.Preferences

import android.content.Context
import android.content.res.TypedArray
import android.preference.DialogPreference
import android.text.InputType
import android.util.AttributeSet
import android.view.View
import android.widget.EditText
import androidx.core.content.withStyledAttributes
import com.gigabytedevelopersinc.app.cometOTP.R
import com.gigabytedevelopersinc.app.cometOTP.Utilities.UIHelper
import com.google.android.material.textfield.TextInputLayout

/**
 * A text setting edited in a dialog, in the app's outlined field: what EditTextPreference does
 * with a plain, underlined EditText. The field's label is android:hint (the title if there is
 * none) and its keyboard android:inputType, both set on the preference.
 */
class TextFieldPreference(context: Context, attrs: AttributeSet?) : DialogPreference(context, attrs) {

    var text: String? = null
        set(value) {
            val wasBlocking = shouldDisableDependents()
            field = value
            persistString(value)
            if (shouldDisableDependents() != wasBlocking)
                notifyDependencyChange(!wasBlocking)
            notifyChanged()
        }

    private var input: EditText? = null
    private var hint: CharSequence? = null
    private var inputType = InputType.TYPE_CLASS_TEXT

    init {
        dialogLayoutResource = R.layout.preference_dialog_text_field
        context.withStyledAttributes(attrs, R.styleable.TextFieldPreference) {
            hint = getText(R.styleable.TextFieldPreference_android_hint)
            inputType = getInt(R.styleable.TextFieldPreference_android_inputType, InputType.TYPE_CLASS_TEXT)
        }
    }

    override fun onBindDialogView(view: View) {
        super.onBindDialogView(view)
        view.findViewById<TextInputLayout>(R.id.preference_field_layout).hint = hint ?: title
        val input = view.findViewById<EditText>(R.id.preference_field)
        input.inputType = inputType
        input.setText(text)
        input.setSelection(input.length())
        input.requestFocus()
        // DialogPreference only asks for the keyboard for its own EditTextPreference.
        input.post { UIHelper.showKeyboard(context, input) }
        this.input = input
    }

    override fun onDialogClosed(positiveResult: Boolean) {
        super.onDialogClosed(positiveResult)
        val value = input?.text?.toString()
        input = null
        if (positiveResult && value != null && callChangeListener(value))
            text = value
    }

    override fun onGetDefaultValue(a: TypedArray, index: Int): Any? {
        return a.getString(index)
    }

    override fun onSetInitialValue(restorePersistedValue: Boolean, defaultValue: Any?) {
        text = if (restorePersistedValue) getPersistedString(text) else defaultValue as String?
    }

    override fun shouldDisableDependents(): Boolean {
        return text.isNullOrEmpty() || super.shouldDisableDependents()
    }
}
