@file:Suppress("PackageName", "DEPRECATION", "OVERRIDE_DEPRECATION")
package com.gigabytedevelopersinc.app.cometOTP.Preferences

import android.app.AlertDialog
import android.app.KeyguardManager
import android.content.Context
import android.content.Context.KEYGUARD_SERVICE
import android.preference.DialogPreference
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.text.method.PasswordTransformationMethod
import android.util.AttributeSet
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import com.gigabytedevelopersinc.app.cometOTP.R
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Constants
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Constants.AuthMethod
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Constants.EncryptionType
import com.gigabytedevelopersinc.app.cometOTP.Utilities.GeneralUtils
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Settings
import com.gigabytedevelopersinc.app.cometOTP.Utilities.UIHelper
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.util.Locale

class CredentialsPreference(context: Context, attrs: AttributeSet?) : DialogPreference(context, attrs),
    AdapterView.OnItemClickListener, View.OnClickListener, TextWatcher {

    fun interface EncryptionChangeCallback {
        fun testEncryptionChange(newKey: ByteArray): Boolean
    }

    private val entries: List<String>

    private var minLength = 0

    private val settings: Settings
    private var value = AuthMethod.NONE
    private var encryptionChangeCallback: EncryptionChangeCallback? = null

    private lateinit var credentialsLayout: LinearLayout
    private lateinit var passwordLayout: TextInputLayout
    private lateinit var passwordInput: TextInputEditText
    private lateinit var passwordConfirm: EditText
    private lateinit var tooShortWarning: TextView

    private lateinit var btnSave: Button

    init {
        settings = Settings(context)
        entries = context.resources.getStringArray(R.array.settings_entries_auth).asList()

        dialogLayoutResource = R.layout.component_authentication
    }

    fun setEncryptionChangeCallback(cb: EncryptionChangeCallback?) {
        this.encryptionChangeCallback = cb
    }

    override fun onPrepareDialogBuilder(builder: AlertDialog.Builder) {
        super.onPrepareDialogBuilder(builder)

        builder.setPositiveButton(null, null)
        builder.setNegativeButton(null, null)
        builder.setCancelable(false)
    }

    override fun onBindDialogView(view: View) {
        value = settings.authMethod

        val listView = view.findViewById<ListView>(R.id.credentialSelection)

        val adapter = ArrayAdapter(context, android.R.layout.simple_list_item_single_choice, entries)
        listView.adapter = adapter

        val index = entryValues.indexOf(value)
        listView.setSelection(index)
        listView.setItemChecked(index, true)
        listView.onItemClickListener = this

        credentialsLayout = view.findViewById(R.id.credentialsLayout)

        passwordLayout = view.findViewById(R.id.passwordLayout)
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

        tooShortWarning = view.findViewById(R.id.tooShortWarning)

        passwordInput.addTextChangedListener(this)
        passwordConfirm.addTextChangedListener(this)

        val btnCancel = view.findViewById<Button>(R.id.btnCancel)
        btnSave = view.findViewById(R.id.btnSave)

        btnCancel.setOnClickListener(this)
        btnSave.setOnClickListener(this)

        updateLayout()

        super.onBindDialogView(view)
    }

    override fun onSetInitialValue(restorePersistedValue: Boolean, defaultValue: Any?) {
        if (restorePersistedValue) {
            val stringValue = getPersistedString(DEFAULT_VALUE.name.lowercase(Locale.ENGLISH))
            value = AuthMethod.valueOf(stringValue.uppercase(Locale.ENGLISH))
        } else {
            value = DEFAULT_VALUE
            persistString(value.name.lowercase(Locale.ENGLISH))
        }

        summary = entries[entryValues.indexOf(value)]
    }

    private fun saveValues() {
        if (settings.encryption == EncryptionType.PASSWORD) {
            if (value == AuthMethod.NONE || value == AuthMethod.DEVICE) {
                UIHelper.showGenericDialog(context, R.string.settings_dialog_title_error, R.string.settings_dialog_msg_auth_invalid_with_encryption)
                return
            }
        }

        if (value == AuthMethod.DEVICE) {
            val km = context.getSystemService(KEYGUARD_SERVICE) as KeyguardManager?

            if (!km!!.isKeyguardSecure) {
                Toast.makeText(context, R.string.settings_toast_auth_device_not_secure, Toast.LENGTH_LONG).show()
                return
            }
        }

        if (value == AuthMethod.PASSWORD || value == AuthMethod.PIN) {
            val password = passwordInput.text!!.toString()
            if (password.isEmpty())
                return

            // Nothing is stored yet. With password encryption the database key derives from the
            // credentials, so they may only replace the old ones once the database has been
            // re-encrypted with the new key; otherwise the old ones stay in effect.
            val newCredentials = settings.generateAuthCredentials(password) ?: return

            if (settings.encryption == EncryptionType.PASSWORD) {
                val callback = encryptionChangeCallback ?: return

                if (!callback.testEncryptionChange(newCredentials.key))
                    return
            }

            settings.saveAuthCredentials(newCredentials, value)
        }

        // Default-locale lowercase, as in the Java original (the key is read back with an
        // English-locale uppercase in Settings).
        persistString(value.toString().lowercase(Locale.getDefault()))
        summary = entries[entryValues.indexOf(value)]
    }

    override fun onClick(view: View) {
        // Resource ids are not compile-time constants any more (AGP 9), so no switch here.
        val id = view.id
        if (id == R.id.btnCancel) {
            dialog.dismiss()
        } else if (id == R.id.btnSave) {
            saveValues()
            dialog.dismiss()
        }
    }

    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
        val password = passwordInput.editableText.toString()

        if (password.length >= minLength) {
            tooShortWarning.visibility = View.GONE

            val confirm = passwordConfirm.editableText.toString()

            btnSave.isEnabled = password.isNotEmpty() && confirm.isNotEmpty() && password == confirm
        } else {
            tooShortWarning.visibility = View.VISIBLE
        }
    }

    private fun updateLayout() {
        if (value == AuthMethod.NONE) {
            credentialsLayout.visibility = View.GONE

            if (dialog != null)
                UIHelper.hideKeyboard(context, dialog.currentFocus)

            btnSave.isEnabled = true
        } else if (value == AuthMethod.PASSWORD) {
            credentialsLayout.visibility = View.VISIBLE

            passwordLayout.hint = context.getString(R.string.settings_hint_password)
            passwordConfirm.setHint(R.string.settings_hint_password_confirm)

            passwordInput.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            passwordConfirm.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD

            passwordInput.transformationMethod = PasswordTransformationMethod()
            passwordConfirm.transformationMethod = PasswordTransformationMethod()

            minLength = Constants.AUTH_MIN_PASSWORD_LENGTH
            tooShortWarning.text = context.getString(R.string.settings_label_short_password, minLength)

            passwordInput.requestFocus()
            UIHelper.showKeyboard(context, passwordInput)

            btnSave.isEnabled = false
        } else if (value == AuthMethod.PIN) {
            credentialsLayout.visibility = View.VISIBLE

            passwordLayout.hint = context.getString(R.string.settings_hint_pin)
            passwordConfirm.setHint(R.string.settings_hint_pin_confirm)

            passwordInput.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            passwordConfirm.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD

            passwordInput.transformationMethod = PasswordTransformationMethod()
            passwordConfirm.transformationMethod = PasswordTransformationMethod()

            minLength = Constants.AUTH_MIN_PIN_LENGTH
            tooShortWarning.text = context.getString(R.string.settings_label_short_pin, minLength)

            passwordInput.requestFocus()
            UIHelper.showKeyboard(context, passwordInput)

            btnSave.isEnabled = false
        } else if (value == AuthMethod.DEVICE) {
            credentialsLayout.visibility = View.GONE

            if (dialog != null)
                UIHelper.hideKeyboard(context, dialog.currentFocus)

            btnSave.isEnabled = true
        }
    }

    override fun onItemClick(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
        value = entryValues[position]
        updateLayout()
    }

    // Needed stub functions
    override fun afterTextChanged(s: Editable?) {}

    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

    companion object {
        @JvmField
        val DEFAULT_VALUE = AuthMethod.NONE

        private val entryValues = listOf(
            AuthMethod.NONE,
            AuthMethod.PASSWORD,
            AuthMethod.PIN,
            AuthMethod.DEVICE
        )
    }
}
