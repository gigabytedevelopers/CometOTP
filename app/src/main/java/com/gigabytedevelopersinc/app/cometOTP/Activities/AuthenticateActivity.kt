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
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
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
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Constants.EncryptionType
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Settings
import com.gigabytedevelopersinc.app.cometOTP.View.AutoFillable.AutoFillableTextInputEditText
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout
import java.io.File

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

    /* Setting up a missing credential, see UnlockAction.SET_UP_CREDENTIAL. */
    private var settingUpCredential = false
    private var awaitingCredentialSetup = false
    private var progress: AlertDialog? = null

    private val credentialSetupLauncher: ActivityResultLauncher<Intent> = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        awaitingCredentialSetup = false
        // A screen recreated after the credential had been stored asks for it instead.
        if (!settingUpCredential)
            return@registerForActivityResult

        val credential = result.data?.getStringExtra(AuthSetupActivity.EXTRA_RESULT_CREDENTIAL)
        if (result.resultCode != RESULT_OK || credential == null) {
            finishWithResult(false, null)
            return@registerForActivityResult
        }
        storeNewCredential(credential)
    }

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

        val databaseExists = File(filesDir, Constants.FILENAME_DATABASE).exists() ||
            File(filesDir, Constants.FILENAME_DATABASE_BACKUP).exists()
        when (unlockActionFor(authMethod, settings.encryption, existingAuthCredentials.isNotEmpty(), databaseExists)) {
            UnlockAction.NOTHING_TO_UNLOCK -> {
                finishWithResult(true, null)
                return
            }
            UnlockAction.LOCKED_OUT -> {
                Toast.makeText(this, missingCredentialMessage(), Toast.LENGTH_LONG).show()
                finishWithResult(false, null)
                return
            }
            UnlockAction.SET_UP_CREDENTIAL -> {
                if (savedInstanceState == null)
                    Toast.makeText(this, missingCredentialMessage(), Toast.LENGTH_LONG).show()
                initCredentialSetup(savedInstanceState)
                return
            }
            UnlockAction.ASK_CREDENTIAL -> {}
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

    private fun missingCredentialMessage(): Int {
        return if (authMethod == AuthMethod.PASSWORD) R.string.auth_toast_password_missing else R.string.auth_toast_pin_missing
    }

    /** No credential is stored for the lock method: the user has to set one up before going on. */
    private fun initCredentialSetup(savedInstanceState: Bundle?) {
        settingUpCredential = true
        awaitingCredentialSetup = savedInstanceState?.getBoolean(STATE_AWAITING_SETUP, false) ?: false

        setTitle(R.string.auth_activity_title)
        setContentView(R.layout.activity_container)
        initToolbar()
        findViewById<Toolbar>(R.id.container_toolbar).setNavigationOnClickListener { cancelCredentialSetup() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                cancelCredentialSetup()
            }
        })
        // The setup screen is opened from onResume().
    }

    private fun cancelCredentialSetup() {
        // Once entered, the new credential is being stored and the outcome has to reach this screen.
        if (!credentialSetupJob.isBusy)
            finishWithResult(false, null)
    }

    private fun resumeCredentialSetup() {
        credentialSetupJob.onResume(this)
        if (isFinishing)
            return

        if (credentialSetupJob.isBusy) {
            showProgress()
        } else if (!awaitingCredentialSetup) {
            // First start, or the process was killed while the credential was being stored.
            awaitingCredentialSetup = true
            val intent = Intent(this, AuthSetupActivity::class.java)
            intent.putExtra(AuthSetupActivity.EXTRA_METHOD, authMethod.name)
            credentialSetupLauncher.launch(intent)
        }
    }

    private fun storeNewCredential(credential: String) {
        if (credentialSetupJob.isBusy)
            return

        showProgress()
        val appContext = applicationContext
        credentialSetupJob.start {
            // Deriving the credentials (PBKDF2) is slow. They are stored on this thread so that
            // they are stored even if no screen is left to show the outcome.
            val settings = Settings(appContext)
            val newCredentials = settings.generateAuthCredentials(credential)
            val key = if (newCredentials != null && settings.saveAuthCredentials(newCredentials, null))
                newCredentials.key else null

            val outcome: (AuthenticateActivity) -> Unit = { it.onNewCredentialStored(key) }
            outcome
        }
    }

    /** Runs on whichever instance is resumed when [storeNewCredential]'s job has finished. */
    private fun onNewCredentialStored(key: ByteArray?) {
        hideProgress()
        // With password encryption (and no database yet) the key derived from the new credential
        // is the database key; with KeyStore encryption the caller ignores it.
        finishWithResult(key != null, key)
    }

    private fun showProgress() {
        if (progress == null) {
            progress = MaterialAlertDialogBuilder(this)
                .setView(R.layout.dialog_progress)
                .setCancelable(false)
                .show()
        }
    }

    private fun hideProgress() {
        progress?.dismiss()
        progress = null
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_AWAITING_SETUP, awaitingCredentialSetup)
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
        if (settingUpCredential) {
            resumeCredentialSetup()
            return
        }
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
        // Not set up while setting up a credential.
        if (!::passwordInput.isInitialized)
            return
        if (settings.autoUnlockAfterAutofill) {
            passwordInput.setAutoFillTextListener(autoFillTextListener)
        }
    }

    override fun onPause() {
        super.onPause()
        credentialSetupJob.onPause(this)
        // We don't want the task to callback to a dead activity and cause a memory leak, so null it here.
        val taskFragment = findTaskFragment()
        if (taskFragment != null) {
            taskFragment.task!!.setCallback(null)
        }
    }

    override fun onStop() {
        if (::passwordInput.isInitialized)
            passwordInput.setAutoFillTextListener(null)
        super.onStop()
    }

    override fun onDestroy() {
        hideProgress()
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
        private const val STATE_AWAITING_SETUP = "AuthenticateActivity.awaitingSetup"

        /** Survives recreation of this screen; see [RetainedJob]. */
        private val credentialSetupJob = RetainedJob<AuthenticateActivity>()
    }
}

/** What the unlock screen does, see [unlockActionFor]. */
internal enum class UnlockAction {
    /** The lock method is not a password or PIN: nothing for this screen to check. */
    NOTHING_TO_UNLOCK,
    ASK_CREDENTIAL,
    /** Have the user set up a new password or PIN, then unlock. */
    SET_UP_CREDENTIAL,
    /** Refuse to unlock. */
    LOCKED_OUT
}

/**
 * Decides how to unlock with the stored lock settings. [hasCredential] is whether a credential
 * (new or old-style hash) is stored for the password or PIN.
 *
 * Without a stored credential there is nothing to check a password against, and unlocking anyway
 * would open the app without any authentication:
 * - With password encryption the database key derives from the lost credential. A new credential
 *   would derive a different key, under which the existing database could not be read (and would
 *   be overwritten on the next save), so an existing database stays locked. Without one (the setup
 *   failed before anything was saved) there is nothing to lose and a new credential is set up.
 * - With KeyStore encryption the database does not depend on the credential. Refusing would lock
 *   the owner out of their accounts for good, with no way back short of deleting all app data, so
 *   a new credential is set up; from then on the app is locked again.
 */
internal fun unlockActionFor(
    authMethod: AuthMethod,
    encryption: EncryptionType,
    hasCredential: Boolean,
    databaseExists: Boolean
): UnlockAction {
    if (authMethod != AuthMethod.PASSWORD && authMethod != AuthMethod.PIN)
        return UnlockAction.NOTHING_TO_UNLOCK
    if (hasCredential)
        return UnlockAction.ASK_CREDENTIAL
    if (encryption == EncryptionType.PASSWORD && databaseExists)
        return UnlockAction.LOCKED_OUT
    return UnlockAction.SET_UP_CREDENTIAL
}
