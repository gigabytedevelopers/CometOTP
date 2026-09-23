@file:Suppress("PackageName", "DEPRECATION", "OVERRIDE_DEPRECATION")
package com.gigabytedevelopersinc.app.cometOTP.Preferences

import android.app.AlertDialog
import android.content.Context
import android.content.res.TypedArray
import android.preference.DialogPreference
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.text.method.PasswordTransformationMethod
import android.util.AttributeSet
import android.util.Base64
import android.view.View
import android.widget.Button
import android.widget.EditText
import com.gigabytedevelopersinc.app.cometOTP.R
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Constants
import com.gigabytedevelopersinc.app.cometOTP.Utilities.EncryptionHelper
import com.gigabytedevelopersinc.app.cometOTP.Utilities.GeneralUtils
import com.gigabytedevelopersinc.app.cometOTP.Utilities.KeyStoreHelper
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Settings
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.nio.charset.StandardCharsets
import java.security.KeyPair

class PasswordEncryptedPreference(context: Context, attrs: AttributeSet?) : DialogPreference(context, attrs),
    View.OnClickListener, TextWatcher {

    enum class Mode {
        PASSWORD, PIN
    }

    private var key: KeyPair? = null

    private var mode = Mode.PASSWORD

    private lateinit var passwordInput: TextInputEditText
    private lateinit var passwordConfirm: EditText

    private lateinit var btnSave: Button

    // Nullable because onSetInitialValue stores the framework's default value unchecked, as the
    // Java original did.
    private var value: String? = DEFAULT_VALUE

    init {
        try {
            key = KeyStoreHelper.loadOrGenerateAsymmetricKeyPair(context, Constants.KEYSTORE_ALIAS_PASSWORD)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        dialogLayoutResource = R.layout.component_password
    }

    fun setMode(mode: Mode) {
        this.mode = mode
    }

    override fun onPrepareDialogBuilder(builder: AlertDialog.Builder) {
        super.onPrepareDialogBuilder(builder)

        builder.setPositiveButton(null, null)
        builder.setNegativeButton(null, null)
        builder.setCancelable(false)
    }

    override fun onBindDialogView(view: View) {
        val settings = Settings(context)

        val passwordLayout = view.findViewById<TextInputLayout>(R.id.passwordLayout)
        passwordInput = view.findViewById(R.id.passwordEdit)
        passwordConfirm = view.findViewById(R.id.passwordConfirm)

        if (settings.blockAccessibility) {
            passwordLayout.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            passwordConfirm.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }

        if (GeneralUtils.isOreo() && settings.blockAutofill) {
            passwordLayout.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
            passwordConfirm.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
        }

        val btnCancel = view.findViewById<Button>(R.id.btnCancel)
        btnSave = view.findViewById(R.id.btnSave)
        btnSave.isEnabled = false

        btnCancel.setOnClickListener(this)
        btnSave.setOnClickListener(this)

        if (value!!.isNotEmpty()) {
            passwordInput.setText(value)
        }

        if (mode == Mode.PASSWORD) {
            passwordLayout.hint = context.getString(R.string.settings_hint_password)
            passwordConfirm.setHint(R.string.settings_hint_password_confirm)

            passwordInput.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            passwordConfirm.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        } else if (mode == Mode.PIN) {
            passwordLayout.hint = context.getString(R.string.settings_hint_pin)
            passwordConfirm.setHint(R.string.settings_hint_pin_confirm)

            passwordInput.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            passwordConfirm.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }

        passwordInput.transformationMethod = PasswordTransformationMethod()
        passwordConfirm.transformationMethod = PasswordTransformationMethod()

        passwordConfirm.addTextChangedListener(this)
        passwordInput.addTextChangedListener(this)

        super.onBindDialogView(view)
    }

    override fun onGetDefaultValue(a: TypedArray, index: Int): Any? {
        return a.getString(index)
    }

    private fun encryptAndPersist(value: String?) {
        try {
            val encBytes = EncryptionHelper.encrypt(key!!.public, value!!.toByteArray(StandardCharsets.UTF_8))
            persistString(Base64.encodeToString(encBytes, Base64.URL_SAFE))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun restoreAndDecrypt(encValue: String?) {
        try {
            val encBytes = Base64.decode(encValue, Base64.URL_SAFE)
            value = String(EncryptionHelper.decrypt(key!!.private, encBytes), StandardCharsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onSetInitialValue(restorePersistedValue: Boolean, defaultValue: Any?) {
        if (restorePersistedValue) {
            restoreAndDecrypt(getPersistedString(DEFAULT_VALUE))
        } else {
            value = defaultValue as String?
            encryptAndPersist(value)
        }
    }

    override fun onClick(view: View) {
        // Resource ids are not compile-time constants any more (AGP 9), so no switch here.
        val id = view.id
        if (id == R.id.btnCancel) {
            dialog.dismiss()
        } else if (id == R.id.btnSave) {
            value = passwordInput.text!!.toString()
            encryptAndPersist(value)

            dialog.dismiss()
        }
    }

    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
        btnSave.isEnabled = passwordConfirm.editableText.toString() == passwordInput.editableText.toString()
    }

    override fun afterTextChanged(s: Editable?) {}

    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

    companion object {
        private const val DEFAULT_VALUE = ""
    }
}
