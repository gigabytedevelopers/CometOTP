@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Activities

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.text.method.PasswordTransformationMethod
import android.view.KeyEvent
import android.view.View
import android.view.ViewStub
import android.view.WindowManager.LayoutParams
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.gigabytedevelopersinc.app.cometOTP.R
import com.gigabytedevelopersinc.app.cometOTP.Tasks.AuthenticationTask
import com.gigabytedevelopersinc.app.cometOTP.Tasks.AuthenticationTask.Result
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Constants
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Constants.AuthMethod
import com.gigabytedevelopersinc.app.cometOTP.View.AutoFillable.AutoFillableTextInputEditText
import com.google.android.material.textfield.TextInputLayout

class AuthenticateActivity : BaseActivity(), TextView.OnEditorActionListener, View.OnClickListener {
    private val autoFillTextListener = AutoFillableTextInputEditText.AutoFillTextListener { text -> startAuthTask(text.toString()) }

    private lateinit var authMethod: AuthMethod
    private var newEncryption: String? = ""
    private lateinit var existingAuthCredentials: String
    private var isAuthUpgrade = false
    private lateinit var observer: ProcessLifecycleObserver

    private lateinit var passwordLayout: TextInputLayout
    internal lateinit var passwordInput: AutoFillableTextInputEditText
    private lateinit var unlockButton: Button
    private lateinit var unlockProgress: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!settings.screenshotsEnabled)
            window.setFlags(LayoutParams.FLAG_SECURE, LayoutParams.FLAG_SECURE)

        authMethod = settings.authMethod
        newEncryption = intent.getStringExtra(Constants.EXTRA_AUTH_NEW_ENCRYPTION)
        existingAuthCredentials = settings.authCredentials
        if (existingAuthCredentials.isEmpty()) {
            existingAuthCredentials = settings.getOldCredentials(authMethod)
            isAuthUpgrade = true
        }

        // If our password is still empty at this point, we can't do anything.
        if (existingAuthCredentials.isEmpty()) {
            val missingPwResId = if (authMethod == AuthMethod.PASSWORD)
                R.string.auth_toast_password_missing else R.string.auth_toast_pin_missing
            Toast.makeText(this, missingPwResId, Toast.LENGTH_LONG).show()
            finishWithResult(true, null)
            return
        }
        // If we're not using password or pin for auth method, we have nothing to authenticate here.
        if (authMethod != AuthMethod.PASSWORD && authMethod != AuthMethod.PIN) {
            finishWithResult(true, null)
            return
        }

        setTitle(R.string.auth_activity_title)
        setContentView(R.layout.activity_container)
        initToolbar()
        initPasswordViews()

        setBroadcastCallback {
            if (settings.relockOnScreenOff) {
                cancelBackgroundTask()
            }
        }

        observer = ProcessLifecycleObserver()
        ProcessLifecycleOwner.get().lifecycle
            .addObserver(observer)

        window.setSoftInputMode(LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)

        // Predictive back: onBackPressed() is no longer invoked when targeting Android 16+.
        // Leaving the screen without authenticating reports a cancelled result to the caller.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finishWithResult(false, null)
            }
        })
    }

    private fun initToolbar() {
        val toolbar = findViewById<Toolbar>(R.id.container_toolbar)
        toolbar.title = null
        toolbar.setNavigationIcon(R.drawable.ic_close)
        toolbar.setNavigationContentDescription(android.R.string.cancel)
        toolbar.setNavigationOnClickListener { finishWithResult(false, null) }
        findViewById<View>(R.id.container_brand).visibility = View.VISIBLE
    }

    private fun initPasswordViews() {
        val stub = findViewById<ViewStub>(R.id.container_stub)
        stub.layoutResource = R.layout.content_authenticate
        val v = stub.inflate()

        initPasswordLabelView(v)
        initPasswordLayoutView(v)
        initPasswordInputView(v)
        initUnlockViews(v)
    }

    private fun initPasswordLabelView(v: View) {
        val labelMsg = intent.getIntExtra(Constants.EXTRA_AUTH_MESSAGE, R.string.auth_msg_authenticate)
        val passwordLabel = v.findViewById<TextView>(R.id.passwordLabel)
        passwordLabel.setText(labelMsg)

        val passwordTitle = v.findViewById<TextView>(R.id.passwordTitle)
        passwordTitle.setText(if (authMethod == AuthMethod.PASSWORD) R.string.auth_title_password else R.string.auth_title_pin)
    }

    private fun initPasswordLayoutView(v: View) {
        passwordLayout = v.findViewById(R.id.passwordLayout)
        val hintResId = if (authMethod == AuthMethod.PASSWORD) R.string.auth_hint_password else R.string.auth_hint_pin
        passwordLayout.hint = getString(hintResId)
        passwordLayout.editText?.setHint(hintResId)
        if (settings.blockAccessibility) {
            passwordLayout.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && settings.blockAutofill) {
            passwordLayout.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        }
    }

    private fun initPasswordInputView(v: View) {
        passwordInput = v.findViewById(R.id.passwordEdit)
        val inputType = if (authMethod == AuthMethod.PASSWORD)
            (InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        else
            (InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD)
        passwordInput.inputType = inputType
        passwordInput.transformationMethod = PasswordTransformationMethod.getInstance()
        passwordInput.setOnEditorActionListener(this)
        passwordInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            }

            override fun afterTextChanged(s: Editable) {
                // The unlock button is created after this listener is attached.
                if (::unlockButton.isInitialized)
                    unlockButton.isEnabled = s.length > 0 && findTaskFragment() == null

                if (
                    passwordInput.transformationMethod === PasswordTransformationMethod.getInstance() &&
                    passwordLayout.endIconMode == TextInputLayout.END_ICON_PASSWORD_TOGGLE &&
                    s.length > 0
                ) {
                    passwordLayout.endIconMode = TextInputLayout.END_ICON_NONE
                } else if (
                    passwordLayout.endIconMode == TextInputLayout.END_ICON_NONE &&
                    s.length == 0
                ) {
                    passwordLayout.endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE
                }
            }
        })
    }

    private fun initUnlockViews(v: View) {
        unlockButton = v.findViewById(R.id.buttonUnlock)
        unlockButton.setOnClickListener(this)
        unlockButton.isEnabled = false
        unlockProgress = v.findViewById(R.id.unlockProgress)
        unlockProgress.visibility = View.GONE
    }

    private fun cancelBackgroundTask() {
        val taskFragment = findTaskFragment()
        if (taskFragment != null) {
            taskFragment.task!!.cancel()
        }
        setupUiForTaskState(false)
    }

    private inner class ProcessLifecycleObserver : DefaultLifecycleObserver {
        override fun onStop(owner: LifecycleOwner) {
            if (settings.relockOnBackground) {
                cancelBackgroundTask()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        checkBackgroundTask()
    }

    private fun checkBackgroundTask() {
        val taskFragment = findTaskFragment()
        if (taskFragment != null) {
            if (taskFragment.task!!.isCanceled) {
                // The task was canceled, so remove the task fragment and reset password input.
                supportFragmentManager.beginTransaction()
                    .remove(taskFragment)
                    .commit()
                resetPasswordInput()
            } else {
                taskFragment.task!!.setCallback(::handleResult)
                setupUiForTaskState(true)
            }
        }
    }

    private fun resetPasswordInput() {
        passwordInput.setText("")
        passwordInput.requestFocus()
        val keyboard = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        keyboard.showSoftInput(passwordInput, 0)
    }

    private fun findTaskFragment(): TaskFragment? {
        return supportFragmentManager.findFragmentByTag(TAG_TASK_FRAGMENT) as TaskFragment?
    }

    private fun setupUiForTaskState(isTaskRunning: Boolean) {
        passwordLayout.isEnabled = !isTaskRunning
        passwordInput.isEnabled = !isTaskRunning
        val text = passwordInput.text
        unlockButton.isEnabled = !isTaskRunning && text != null && text.length > 0
        unlockButton.text = if (isTaskRunning) "" else getString(R.string.auth_button_confirm)
        unlockProgress.visibility = if (isTaskRunning) View.VISIBLE else View.GONE
    }

    override fun onClick(view: View) {
        val text = passwordInput.text
        startAuthTask(text?.toString() ?: "")
    }

    override fun onEditorAction(v: TextView, actionId: Int, event: KeyEvent?): Boolean {
        if (actionId == EditorInfo.IME_ACTION_DONE) {
            startAuthTask(v.text.toString())
            return true
        }
        return false
    }

    private fun startAuthTask(plainPassword: String) {
        var taskFragment = findTaskFragment()
        // Don't start a task if we already have an active task running.
        if (taskFragment == null || taskFragment.task!!.isCanceled) {
            val task = AuthenticationTask(this, isAuthUpgrade, existingAuthCredentials, plainPassword)
            task.setCallback(::handleResult)

            if (taskFragment == null) {
                taskFragment = TaskFragment()
                supportFragmentManager
                    .beginTransaction()
                    .add(taskFragment, TAG_TASK_FRAGMENT)
                    .commit()
            }
            taskFragment.startTask(task)
            setupUiForTaskState(true)
        }
    }

    private fun handleResult(result: Result) {
        if (result.authUpgradeFailed) {
            Toast.makeText(this, R.string.settings_toast_auth_upgrade_failed, Toast.LENGTH_LONG).show()
        }
        // A failed upgrade still means the password matched the old hash, which is kept and
        // upgraded on the next unlock; turning the user away would keep them out for as long as
        // the upgrade keeps failing. Old-style credentials only exist with KeyStore encryption,
        // so no key is needed from them.
        finishWithResult(result.encryptionKey != null || result.authUpgradeFailed, result.encryptionKey)
    }

    private fun finishWithResult(success: Boolean, encryptionKey: ByteArray?) {
        val data = Intent()
        val newEncryption = newEncryption
        if (newEncryption != null && newEncryption.isNotEmpty())
            data.putExtra(Constants.EXTRA_AUTH_NEW_ENCRYPTION, newEncryption)
        if (encryptionKey != null)
            data.putExtra(Constants.EXTRA_AUTH_PASSWORD_KEY, encryptionKey)
        if (success)
            setResult(RESULT_OK, data)
        finish()
    }

    override fun onStart() {
        super.onStart()
        if (settings.autoUnlockAfterAutofill) {
            passwordInput.setAutoFillTextListener(autoFillTextListener)
        }
    }

    override fun onPause() {
        super.onPause()
        // We don't want the task to callback to a dead activity and cause a memory leak, so null it here.
        val taskFragment = findTaskFragment()
        if (taskFragment != null) {
            taskFragment.task!!.setCallback(null)
        }
    }

    override fun onStop() {
        passwordInput.setAutoFillTextListener(null)
        super.onStop()
    }

    override fun onDestroy() {
        // Not set when onCreate() finished the activity early.
        if (::observer.isInitialized)
            ProcessLifecycleOwner.get().lifecycle
                .removeObserver(observer)
        super.onDestroy()
    }

    override fun shouldDestroyOnScreenOff(): Boolean {
        return false
    }

    /** Retained instance fragment to hold a running [AuthenticationTask] between configuration changes.*/
    class TaskFragment : Fragment() {

        // Null only for a fragment the framework re-created without a task (the Java code would
        // throw a NullPointerException on it at the same points the `!!` operators do).
        var task: AuthenticationTask? = null

        init {
            @Suppress("DEPRECATION")
            retainInstance = true
        }

        fun startTask(task: AuthenticationTask) {
            this.task = task
            task.execute()
        }
    }

    companion object {
        private const val TAG_TASK_FRAGMENT = "AuthenticateActivity.TaskFragmentTag"
    }
}
