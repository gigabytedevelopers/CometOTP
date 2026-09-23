@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Dialogs

import android.content.Context
import android.os.Build
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatDialog
import com.gigabytedevelopersinc.app.cometOTP.R
import com.gigabytedevelopersinc.app.cometOTP.Utilities.ConfirmedPasswordTransformationHelper
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Constants
import com.gigabytedevelopersinc.app.cometOTP.Utilities.EditorActionHelper
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Tools
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

/**
 * @author Created by Emmanuel Nwokoma (Founder and CEO at Gigabyte Developers) on 7/15/2018
 **/
class PasswordEntryDialog(
    context: Context,
    newMode: Mode,
    blockAccessibility: Boolean,
    blockAutofill: Boolean,
    newCallback: PasswordEnteredCallback?
) : AppCompatDialog(context, Tools.getThemeResource(context, R.attr.dialogTheme)),
    View.OnClickListener, TextWatcher, TextView.OnEditorActionListener {

    enum class Mode { ENTER, UPDATE }

    fun interface PasswordEnteredCallback {
        fun onPasswordEntered(newPassword: String)
    }

    private val dialogMode: Mode
    private val callback: PasswordEnteredCallback?

    private val passwordInput: TextInputEditText
    private val passwordConfirm: EditText
    private val passwordConfirmLayout: View
    private val okButton: Button
    private val tooShortWarning: TextView

    init {
        setTitle(R.string.dialog_title_enter_password)
        setContentView(R.layout.dialog_password_entry)

        val passwordLayout = findViewById<TextInputLayout>(R.id.passwordInputLayout)!!
        passwordInput = findViewById(R.id.passwordInput)!!
        passwordConfirm = findViewById(R.id.passwordConfirm)!!
        // The confirm field is wrapped, so the wrapper is what has to be shown or hidden.
        passwordConfirmLayout = findViewById(R.id.passwordConfirmLayout)!!
        tooShortWarning = findViewById(R.id.tooShortWarning)!!
        tooShortWarning.text = getContext().getString(R.string.settings_label_short_password, Constants.AUTH_MIN_PASSWORD_LENGTH)
        ConfirmedPasswordTransformationHelper.setup(passwordLayout, passwordInput, passwordConfirm)

        if (blockAccessibility) {
            passwordLayout.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            passwordConfirm.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && blockAutofill) {
            passwordLayout.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
            passwordConfirm.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
        }

        okButton = findViewById(R.id.buttonOk)!!
        okButton.setOnClickListener(this)
        okButton.isEnabled = false

        val cancelButton = findViewById<Button>(R.id.buttonCancel)!!
        cancelButton.setOnClickListener(this)

        this.callback = newCallback

        this.dialogMode = newMode

        if (this.dialogMode == Mode.UPDATE) {
            passwordConfirmLayout.visibility = View.VISIBLE

            passwordInput.addTextChangedListener(this)
            passwordConfirm.addTextChangedListener(this)

            passwordConfirm.setOnEditorActionListener(this)
        } else if (this.dialogMode == Mode.ENTER) {
            passwordConfirmLayout.visibility = View.GONE

            passwordInput.addTextChangedListener(this)

            passwordInput.setOnEditorActionListener(this)
        }
    }

    // TextWatcher
    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
        if (passwordInput.editableText.length >= Constants.AUTH_MIN_PASSWORD_LENGTH) {
            tooShortWarning.visibility = View.GONE

            okButton.isEnabled = dialogMode == Mode.ENTER || TextUtils.equals(passwordInput.editableText, passwordConfirm.editableText)
        }
        else {
            tooShortWarning.visibility = View.VISIBLE
            okButton.isEnabled = false
        }
    }

    override fun afterTextChanged(s: Editable?) {}
    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

    override fun onEditorAction(v: TextView?, actionId: Int, event: KeyEvent?): Boolean {
        if (EditorActionHelper.isActionDoneOrKeyboardEnter(actionId, event)) {
            if (okButton.isEnabled) okButton.performClick()
            return true
        } else if (EditorActionHelper.isActionUpKeyboardEnter(event!!)) {
            // Ignore action up after keyboard enter. Otherwise the cancel button would be selected
            // after pressing enter with an invalid password.
            return true
        }

        return false
    }

    // View.OnClickListener
    override fun onClick(view: View) {
        if (view.id == R.id.buttonOk) {
            callback?.onPasswordEntered(passwordInput.text.toString())
        }

        dismiss()
    }
}
