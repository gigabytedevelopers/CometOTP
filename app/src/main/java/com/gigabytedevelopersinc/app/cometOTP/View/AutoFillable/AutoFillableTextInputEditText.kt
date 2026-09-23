@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.View.AutoFillable

import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.view.autofill.AutofillValue
import com.google.android.material.textfield.TextInputEditText

/**
 * Project - CometOTP
 * Created with Android Studio
 * Company: Gigabyte Developers
 * User: Emmanuel Nwokoma
 * Title: Founder and CEO
 * Day: Monday, 26
 * Month: October
 * Year: 2020
 * Date: 26 Oct, 2020
 * Time: 9:16 AM
 * Desc: AutoFillableTextInputEditText
 **/
class AutoFillableTextInputEditText : TextInputEditText {

    private var listener: AutoFillTextListener? = null

    // Kept as three separate constructors (not @JvmOverloads): TextInputEditText's
    // (Context, AttributeSet) constructor supplies R.attr.editTextStyle as the default style
    // attribute, whereas @JvmOverloads would pass 0 and lose the EditText style.
    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    override fun autofill(value: AutofillValue?) {
        super.autofill(value)
        val listener = listener
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || listener == null) {
            return
        }

        if (value != null && value.isText) {
            listener.onTextAutoFilled(value.textValue)
        }
    }

    fun setAutoFillTextListener(listener: AutoFillTextListener?) {
        this.listener = listener
    }

    fun interface AutoFillTextListener {

        fun onTextAutoFilled(text: CharSequence)
    }
}
