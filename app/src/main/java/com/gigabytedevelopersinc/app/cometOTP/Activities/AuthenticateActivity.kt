@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Activities

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.text.format.DateUtils
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
import com.gigabytedevelopersinc.app.cometOTP.Utilities.UnlockStatus
import com.gigabytedevelopersinc.app.cometOTP.Utilities.unlockStatus
import com.gigabytedevelopersinc.app.cometOTP.View.AutoFillable.AutoFillableTextInputEditText
import com.gigabytedevelopersinc.app.cometOTP.View.PinInput
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

    /* A PIN is typed on a keypad instead of the password field above. */
    private lateinit var pinInput: PinInput
    /** How many digits the PIN being checked has, to store when its length is not known yet. */
    private var submittedPinLength = 0

    /* Wrong attempts, see UnlockThrottle. */
    private lateinit var statusView: TextView
    /** Locked after too many wrong attempts: nothing can be entered until the lockout is over. */
    private var lockedOut = false
    private val lockoutTicker = Handler(Looper.getMainLooper())
    private val lockoutTick = Runnable { updateLockout() }

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

        // The keypad replaces the keyboard for a PIN.
        window.setSoftInputMode(
            if (::pinInput.isInitialized) LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
            else LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
        )

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
        val isPin = authMethod == AuthMethod.PIN
        val stub = findViewById<ViewStub>(R.id.container_stub)
        stub.layoutResource = if (isPin) R.layout.content_authenticate_pin else R.layout.content_authenticate
        val v = stub.inflate()

        initPasswordLabelView(v)
        statusView = v.findViewById(R.id.authStatus)
        if (isPin) {
            initPinViews(v)
        } else {
            initPasswordLayoutView(v)
            initPasswordInputView(v)
            initUnlockViews(v)
        }
        // Earlier wrong attempts are shown from onResume().
    }

    private fun initPinViews(v: View) {
        pinInput = PinInput(v.findViewById(R.id.pinPad), v.findViewById(R.id.pinDots))
        // Without a stored length (a PIN set up before it was stored) the user confirms with OK.
        val length = settings.pinLength
        val minLength = if (length != null) minOf(length, Constants.AUTH_MIN_PIN_LENGTH) else Constants.AUTH_MIN_PIN_LENGTH
        pinInput.setLength(length, minLength, Constants.AUTH_MAX_ENTERED_PIN_LENGTH)
        if (settings.blockAccessibility)
            pinInput.blockAccessibility()
        pinInput.onSubmit = { pin ->
            submittedPinLength = pin.length
            startAuthTask(pin)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (::pinInput.isInitialized && pinInput.handleKey(keyCode, event))
            return true
        return super.onKeyDown(keyCode, event)
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
        // The field would otherwise keep the password typed so far in the saved instance state,
        // which the system may write to disk.
        passwordInput.isSaveEnabled = false
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
        // Earlier wrong attempts, also from before the app was closed, and an attempt cancelled
        // while it was checked (it counts, see Settings.recordAuthAttempt()).
        showUnlockStatus(false)
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
        if (::pinInput.isInitialized) {
            pinInput.clear()
            return
        }
        passwordInput.setText("")
        passwordInput.requestFocus()
        val keyboard = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        keyboard.showSoftInput(passwordInput, 0)
    }

    private fun findTaskFragment(): TaskFragment? {
        return supportFragmentManager.findFragmentByTag(TAG_TASK_FRAGMENT) as TaskFragment?
    }

    private fun setupUiForTaskState(isTaskRunning: Boolean) {
        if (::pinInput.isInitialized) {
            pinInput.setBusy(isTaskRunning)
            if (!isTaskRunning && lockedOut)
                pinInput.setEnabled(false)
            return
        }
        val enabled = !isTaskRunning && !lockedOut
        passwordLayout.isEnabled = enabled
        passwordInput.isEnabled = enabled
        val text = passwordInput.text
        unlockButton.isEnabled = enabled && text != null && text.length > 0
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
        // Autofill can still hand over a password while the screen is locked.
        if (lockedOut || settings.authLockoutRemaining() > 0) {
            resetPasswordInput()
            showUnlockStatus(false)
            return
        }
        var taskFragment = findTaskFragment()
        // Don't start a task if we already have an active task running.
        if (taskFragment == null || taskFragment.task!!.isCanceled) {
            // Counted as wrong until it turns out right, see Settings.recordAuthAttempt().
            settings.recordAuthAttempt()
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
        val success = result.encryptionKey != null || result.authUpgradeFailed
        // Done with the task, so that another attempt can start a new one. Should the removal be
        // lost to a state save, onResume() removes the cancelled task's fragment instead.
        val taskFragment = findTaskFragment()
        if (taskFragment != null) {
            taskFragment.task?.cancel()
            supportFragmentManager.beginTransaction().remove(taskFragment).commitAllowingStateLoss()
        }
        if (!success) {
            onWrongCredential()
            return
        }

        settings.resetAuthFailures()
        // A PIN of unknown length unlocks as soon as it is typed from now on.
        if (::pinInput.isInitialized && settings.pinLength == null && !result.authUpgradeFailed)
            settings.pinLength = submittedPinLength
        finishWithResult(true, result.encryptionKey)
    }

    /**
     * Stays on the screen to try again, which used to close the app: says so, along with how many
     * attempts are left, or locks the input once there were too many (see UnlockThrottle).
     */
    private fun onWrongCredential() {
        // The lockout counts from now, not from when the attempt was started.
        settings.restartAuthLockout()
        showUnlockStatus(true)
        if (::pinInput.isInitialized) {
            pinInput.showError {
                // The keypad is back on after the shake unless the screen is locked.
                setupUiForTaskState(false)
            }
            return
        }
        passwordInput.setText("")
        setupUiForTaskState(false)
        if (!lockedOut)
            resetPasswordInput()
    }

    /** Whether an attempt is being checked, or its result is waiting for this screen. */
    private fun isCheckRunning(): Boolean {
        return findTaskFragment()?.task?.isCanceled == false
    }

    /**
     * Shows what earlier wrong attempts leave the user with, [justFailed] after one just now, and
     * locks the input while they have to wait.
     */
    private fun showUnlockStatus(justFailed: Boolean) {
        // An attempt is counted (and may lock) as soon as it starts; say nothing until it is checked.
        if (isCheckRunning())
            return
        val status = unlockStatus(settings.authFailures, settings.authLockoutRemaining())
        val isPin = authMethod == AuthMethod.PIN
        val lines = ArrayList<String>()
        if (justFailed)
            lines.add(getString(if (isPin) R.string.auth_error_wrong_pin else R.string.auth_error_wrong_password))
        when (status) {
            UnlockStatus.Clear -> {}
            is UnlockStatus.AttemptsLeft ->
                lines.add(resources.getQuantityString(R.plurals.auth_status_attempts_left, status.count, status.count))
            is UnlockStatus.NextLocks ->
                lines.add(getString(if (isPin) R.string.auth_status_next_lockout_pin else R.string.auth_status_next_lockout_password,
                    formatLockout(status.lockout)))
            is UnlockStatus.Locked ->
                lines.add(lockedMessage(status.remaining))
        }
        // Read out once; the countdown below changes the text every second without that.
        statusView.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        statusView.text = lines.joinToString("\n")

        lockedOut = status is UnlockStatus.Locked
        // Right after a wrong attempt onWrongCredential() turns the input back on, after the shake.
        if (!justFailed)
            setupUiForTaskState(false)
        lockoutTicker.removeCallbacks(lockoutTick)
        if (status is UnlockStatus.Locked)
            scheduleLockoutTick(status.remaining)
    }

    private fun updateLockout() {
        val remaining = settings.authLockoutRemaining()
        if (remaining <= 0) {
            // Over: the input comes back, along with what another wrong attempt would cost.
            showUnlockStatus(false)
            if (!::pinInput.isInitialized)
                resetPasswordInput()
            return
        }
        statusView.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_NONE
        val lines = statusView.text.split('\n').toMutableList()
        lines[lines.size - 1] = lockedMessage(remaining)
        statusView.text = lines.joinToString("\n")
        scheduleLockoutTick(remaining)
    }

    /** The next update of the countdown, when the next whole second is reached. */
    private fun scheduleLockoutTick(remaining: Long) {
        val untilNextSecond = (remaining - 1) % 1000 + 1
        lockoutTicker.postDelayed(lockoutTick, untilNextSecond)
    }

    private fun lockedMessage(remaining: Long): String {
        val seconds = (remaining + 999) / 1000
        return getString(R.string.auth_status_locked, DateUtils.formatElapsedTime(seconds))
    }

    private fun formatLockout(lockout: Long): String {
        val seconds = (lockout / 1000).toInt()
        return if (seconds < 60)
            resources.getQuantityString(R.plurals.auth_duration_seconds, seconds, seconds)
        else
            resources.getQuantityString(R.plurals.auth_duration_minutes, seconds / 60, seconds / 60)
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
        lockoutTicker.removeCallbacks(lockoutTick)
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
