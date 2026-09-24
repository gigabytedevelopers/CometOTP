@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Activities

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.View
import android.view.ViewStub
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.gigabytedevelopersinc.app.cometOTP.R
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Constants
import com.gigabytedevelopersinc.app.cometOTP.Utilities.UIHelper
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

/**
 * "Set up Password" / "Set up PIN": the user enters the new credential, then confirms it. The
 * plain credential is handed back to the caller, which stores it and re-encrypts if needed.
 *
 * Neither entry is ever put into the saved instance state, which the system may write to disk:
 * the first entry is kept in memory only ([FirstEntry]), so the confirmation step survives a
 * configuration change (rotation, dark mode, window resize) but starts over at the first step
 * after the process was killed, and the input field does not save its text.
 */
class AuthSetupActivity : BaseActivity() {
    private var method = Constants.AuthMethod.PASSWORD
    private var minLength = Constants.AUTH_MIN_PASSWORD_LENGTH

    private lateinit var entry: FirstEntry
    private var firstEntry: String?
        get() = entry.value
        set(value) {
            entry.value = value
        }

    private lateinit var title: TextView
    private lateinit var hint: TextView
    private lateinit var layout: TextInputLayout
    private lateinit var input: TextInputEditText
    private lateinit var continueButton: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!settings.screenshotsEnabled)
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

        val methodName = intent.getStringExtra(EXTRA_METHOD)
        if (methodName != null)
            method = Constants.AuthMethod.valueOf(methodName)
        val isPin = method == Constants.AuthMethod.PIN
        minLength = if (isPin) Constants.AUTH_MIN_PIN_LENGTH else Constants.AUTH_MIN_PASSWORD_LENGTH

        setTitle(if (isPin) R.string.security_setup_title_pin else R.string.security_setup_title_password)
        setContentView(R.layout.activity_container)

        val toolbar = findViewById<Toolbar>(R.id.container_toolbar)
        setSupportActionBar(toolbar)

        val stub = findViewById<ViewStub>(R.id.container_stub)
        stub.layoutResource = R.layout.content_auth_setup
        val v = stub.inflate()

        title = v.findViewById(R.id.setupTitle)
        hint = v.findViewById(R.id.setupHint)
        layout = v.findViewById(R.id.setupLayout)
        input = v.findViewById(R.id.setupInput)
        continueButton = v.findViewById(R.id.setupContinue)

        input.inputType = if (isPin)
            (InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD)
        else
            (InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        input.setHint(if (isPin) R.string.auth_hint_pin else R.string.auth_hint_password)

        // The field would otherwise save the password typed so far in the instance state.
        input.isSaveEnabled = false

        if (settings.blockAccessibility)
            layout.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && settings.blockAutofill)
            layout.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS

        entry = ViewModelProvider(this)[FirstEntry::class.java]
        showStep()

        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            }

            override fun afterTextChanged(s: Editable) {
                layout.error = null
                continueButton.isEnabled = s.length >= minLength
            }
        })
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE && continueButton.isEnabled) {
                onContinue()
                return@setOnEditorActionListener true
            }
            false
        }
        continueButton.setOnClickListener { onContinue() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (firstEntry != null) {
                    firstEntry = null
                    showStep()
                } else {
                    setResult(RESULT_CANCELED)
                    finish()
                }
            }
        })

        input.requestFocus()
        UIHelper.showKeyboard(this, input)
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    private fun showStep() {
        val isPin = method == Constants.AuthMethod.PIN
        val confirming = firstEntry != null

        if (confirming) {
            title.setText(if (isPin) R.string.security_setup_confirm_pin else R.string.security_setup_confirm_password)
            hint.text = null
        } else {
            title.setText(if (isPin) R.string.security_setup_enter_pin else R.string.security_setup_enter_password)
            hint.text = getString(if (isPin) R.string.security_setup_hint_pin else R.string.security_setup_hint_password, minLength)
        }

        input.setText("")
        layout.error = null
        continueButton.isEnabled = false
    }

    private fun onContinue() {
        val text = input.text
        val value = text?.toString() ?: ""
        if (value.length < minLength)
            return

        val first = firstEntry
        if (first == null) {
            firstEntry = value
            showStep()
            return
        }

        if (first != value) {
            firstEntry = null
            showStep()
            layout.error = getString(R.string.security_setup_mismatch)
            return
        }

        UIHelper.hideKeyboard(this, input)
        val data = Intent()
        data.putExtra(EXTRA_RESULT_CREDENTIAL, value)
        data.putExtra(EXTRA_METHOD, method.name)
        setResult(RESULT_OK, data)
        finish()
    }

    override fun shouldDestroyOnScreenOff(): Boolean {
        return false
    }

    /** The first entry while it is being confirmed; kept across configuration changes only. */
    class FirstEntry : ViewModel() {
        var value: String? = null
    }

    companion object {
        const val EXTRA_METHOD = "auth_setup_method"
        const val EXTRA_RESULT_CREDENTIAL = "auth_setup_credential"
    }
}
