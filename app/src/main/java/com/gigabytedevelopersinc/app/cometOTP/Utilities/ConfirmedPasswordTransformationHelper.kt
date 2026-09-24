@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Utilities

import android.text.method.PasswordTransformationMethod
import android.widget.EditText
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

/**
 * Project - CometOTP
 * Created with Android Studio
 * Company: Gigabyte Developers
 * User: Emmanuel Nwokoma
 * Title: Founder and CEO
 * Day: Wednesday, 10
 * Month: February
 * Year: 2021
 * Date: 10 Feb, 2021
 * Time: 12:42 AM
 * Desc: ConfirmedPasswordTransformationHelper
 **/
object ConfirmedPasswordTransformationHelper {

    /** Sets up the specified password views for a toggleable obscure/view password text transformation. */
    fun setup(passwordLayout: TextInputLayout, passwordInput: TextInputEditText, passwordConfirmInput: EditText) {
        passwordLayout.setEndIconOnClickListener {
            val wasShowingPassword = passwordInput.transformationMethod is PasswordTransformationMethod
            // Dispatch password visibility change to both password and confirm inputs
            dispatchPasswordVisibilityChange(passwordInput, wasShowingPassword)
            dispatchPasswordVisibilityChange(passwordConfirmInput, wasShowingPassword)
            passwordLayout.refreshDrawableState()
        }
        passwordInput.transformationMethod = PasswordTransformationMethod.getInstance()
        passwordConfirmInput.transformationMethod = PasswordTransformationMethod.getInstance()
    }

    private fun dispatchPasswordVisibilityChange(editText: EditText, wasShowingPassword: Boolean) {
        val selection = editText.selectionEnd
        val newMethod = if (wasShowingPassword) null else PasswordTransformationMethod.getInstance()
        editText.transformationMethod = newMethod
        if (selection >= 0) {
            editText.setSelection(selection)
        }
    }
}
