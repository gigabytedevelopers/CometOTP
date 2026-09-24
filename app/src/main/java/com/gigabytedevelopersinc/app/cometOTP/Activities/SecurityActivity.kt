@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Activities

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewStub
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import com.gigabytedevelopersinc.app.cometOTP.Dialogs.ResultDialog
import com.gigabytedevelopersinc.app.cometOTP.R
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Constants
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Constants.AuthMethod
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Constants.EncryptionType
import com.gigabytedevelopersinc.app.cometOTP.Utilities.EncryptionChangeHelper
import com.gigabytedevelopersinc.app.cometOTP.Utilities.EncryptionHelper
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Settings
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import javax.crypto.SecretKey

/**
 * Security screen: choose how CometOTP is locked (PIN, password or the device lock) and set up
 * the chosen credential. Returns the (possibly changed) encryption key to the caller exactly like
 * the settings screen does.
 */
class SecurityActivity : BaseActivity() {
    private var encryptionKey: SecretKey? = null
    private var encryptionChanged = false
    private var progress: AlertDialog? = null

    private lateinit var rowPin: View
    private lateinit var rowPassword: View
    private lateinit var rowDevice: View
    private lateinit var removeButton: MaterialButton

    private val setupLauncher: ActivityResultLauncher<Intent> = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (result.resultCode != RESULT_OK || data == null)
            return@registerForActivityResult

        val credential = data.getStringExtra(AuthSetupActivity.EXTRA_RESULT_CREDENTIAL)
        val methodName = data.getStringExtra(AuthSetupActivity.EXTRA_METHOD)
        if (credential == null || methodName == null)
            return@registerForActivityResult

        applyCredential(AuthMethod.valueOf(methodName), credential)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setTitle(R.string.security_activity_title)
        setContentView(R.layout.activity_container)

        val toolbar = findViewById<Toolbar>(R.id.container_toolbar)
        setSupportActionBar(toolbar)

        val stub = findViewById<ViewStub>(R.id.container_stub)
        stub.layoutResource = R.layout.content_security
        val v = stub.inflate()

        val keyMaterial = intent.getByteArrayExtra(Constants.EXTRA_SETTINGS_ENCRYPTION_KEY)
        if (keyMaterial != null && keyMaterial.isNotEmpty())
            encryptionKey = EncryptionHelper.generateSymmetricKey(keyMaterial)

        if (savedInstanceState != null) {
            encryptionChanged = savedInstanceState.getBoolean(Constants.EXTRA_SETTINGS_ENCRYPTION_CHANGED, false)
            val encKey = savedInstanceState.getByteArray(Constants.EXTRA_SETTINGS_ENCRYPTION_KEY)
            if (encKey != null)
                encryptionKey = EncryptionHelper.generateSymmetricKey(encKey)
        }

        rowPin = v.findViewById(R.id.row_pin)
        rowPassword = v.findViewById(R.id.row_password)
        rowDevice = v.findViewById(R.id.row_device)
        removeButton = v.findViewById(R.id.security_remove)

        bindRow(rowPin, R.drawable.ic_dialpad, R.string.security_row_pin) { select(AuthMethod.PIN) }
        bindRow(rowPassword, R.drawable.ic_lock_outline, R.string.security_row_password) { select(AuthMethod.PASSWORD) }
        bindRow(rowDevice, R.drawable.ic_fingerprint, R.string.security_row_device) { select(AuthMethod.DEVICE) }
        removeButton.setOnClickListener { confirmRemove() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finishWithResult()
            }
        })

        refresh()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(Constants.EXTRA_SETTINGS_ENCRYPTION_CHANGED, encryptionChanged)
        val encryptionKey = encryptionKey
        if (encryptionKey != null)
            outState.putByteArray(Constants.EXTRA_SETTINGS_ENCRYPTION_KEY, encryptionKey.encoded)
    }

    override fun onSupportNavigateUp(): Boolean {
        finishWithResult()
        return true
    }

    private fun finishWithResult() {
        val data = Intent()
        data.putExtra(Constants.EXTRA_SETTINGS_ENCRYPTION_CHANGED, encryptionChanged)
        val encryptionKey = encryptionKey
        if (encryptionKey != null)
            data.putExtra(Constants.EXTRA_SETTINGS_ENCRYPTION_KEY, encryptionKey.encoded)
        setResult(RESULT_OK, data)
        finish()
    }

    private fun bindRow(row: View, icon: Int, title: Int, action: Runnable) {
        row.findViewById<ImageView>(R.id.row_icon).setImageResource(icon)
        row.findViewById<TextView>(R.id.row_title).setText(title)
        row.setOnClickListener { action.run() }
    }

    private fun setSubtitle(row: View, textRes: Int) {
        val subtitle = row.findViewById<TextView>(R.id.row_subtitle)
        if (textRes == 0) {
            subtitle.visibility = View.GONE
        } else {
            subtitle.setText(textRes)
            subtitle.visibility = View.VISIBLE
        }
    }

    private fun refresh() {
        val current = settings.authMethod
        setSubtitle(rowPin, if (current == AuthMethod.PIN) R.string.security_status_pin else 0)
        setSubtitle(rowPassword, if (current == AuthMethod.PASSWORD) R.string.security_status_password else 0)
        setSubtitle(rowDevice, if (current == AuthMethod.DEVICE) R.string.security_status_device else R.string.security_row_device_hint)
        removeButton.visibility = if (current == AuthMethod.NONE) View.GONE else View.VISIBLE
    }

    private fun methodLabel(method: AuthMethod): Int {
        return when (method) {
            AuthMethod.PIN -> R.string.security_row_pin
            AuthMethod.PASSWORD -> R.string.security_row_password
            AuthMethod.DEVICE -> R.string.security_row_device
            else -> R.string.security_row_password
        }
    }

    private fun select(method: AuthMethod) {
        val current = settings.authMethod

        if (method == AuthMethod.DEVICE) {
            if (settings.encryption == EncryptionType.PASSWORD) {
                showError(R.string.settings_dialog_msg_auth_invalid_with_encryption)
                return
            }
            val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager?
            if (km == null || !km.isKeyguardSecure) {
                showError(R.string.security_dialog_msg_device_not_secure)
                return
            }
            if (current == AuthMethod.DEVICE)
                return
        }

        if (current != AuthMethod.NONE && current != method) {
            ResultDialog.showWarning(this, R.string.security_dialog_title_warning,
                getString(R.string.security_dialog_msg_switch, getString(methodLabel(current))),
                R.string.continue_on, { proceed(method) }, android.R.string.cancel, null)
        } else {
            proceed(method)
        }
    }

    private fun proceed(method: AuthMethod) {
        if (method == AuthMethod.DEVICE) {
            settings.authMethod = AuthMethod.DEVICE
            refresh()
            ResultDialog.showSuccess(this, R.drawable.ic_fingerprint,
                R.string.security_result_device_title, R.string.security_result_device_msg,
                R.string.continue_on, null)
            return
        }

        val intent = Intent(this, AuthSetupActivity::class.java)
        intent.putExtra(AuthSetupActivity.EXTRA_METHOD, method.name)
        setupLauncher.launch(intent)
    }

    private fun applyCredential(method: AuthMethod, credential: String) {
        if (credentialJob.isBusy)
            return

        // Deriving the credential hash (PBKDF2) and re-encrypting the database are slow; keep
        // them off the main thread and block the UI with a small progress dialog meanwhile.
        showProgress()

        val appContext = applicationContext
        val reEncrypt = settings.encryption == EncryptionType.PASSWORD
        val currentKey = encryptionKey

        credentialJob.start {
            val settings = Settings(appContext)

            // Nothing is stored before the database has been re-encrypted: with password
            // encryption the database key derives from the credentials, so storing them first
            // would lock the user out whenever the re-encryption fails.
            val newCredentials = settings.generateAuthCredentials(credential)
            var result: EncryptionChangeHelper.Result? = null
            if (newCredentials != null) {
                if (reEncrypt)
                    result = EncryptionChangeHelper.changeEncryption(appContext, currentKey, EncryptionType.PASSWORD, newCredentials.key)

                // Store the credentials and the method together, right after the re-encryption
                // and still on this thread, so they are stored even if no screen is left to
                // show the result.
                if (result == null || result.status == EncryptionChangeHelper.Status.SUCCESS)
                    settings.saveAuthCredentials(newCredentials, method)
            }

            val derived = newCredentials != null
            val outcome: (SecurityActivity) -> Unit = { it.onCredentialApplied(method, derived, result) }
            outcome
        }
    }

    /** Runs on whichever instance is resumed when [applyCredential]'s job has finished. */
    private fun onCredentialApplied(method: AuthMethod, derived: Boolean, result: EncryptionChangeHelper.Result?) {
        hideProgress()

        if (!derived || (result != null && result.status != EncryptionChangeHelper.Status.SUCCESS)) {
            val message = if (result?.status == EncryptionChangeHelper.Status.BACKUP_FAILED)
                R.string.settings_toast_encryption_backup_failed
            else
                R.string.settings_toast_encryption_change_failed
            Snackbar.make(findViewById<View>(R.id.container_content), message, Snackbar.LENGTH_LONG).show()
            return
        }

        if (result != null) {
            encryptionKey = result.newKey
            encryptionChanged = true
        }

        refresh()

        val isPin = method == AuthMethod.PIN
        ResultDialog.showSuccessWithSecondary(this, if (isPin) R.drawable.ic_dialpad else R.drawable.ic_lock_outline,
            if (isPin) R.string.security_result_pin_title else R.string.security_result_password_title,
            if (isPin) R.string.security_result_pin_msg else R.string.security_result_password_msg,
            R.string.continue_on, null,
            if (isPin) R.string.security_result_reset_pin else R.string.security_result_reset_password
        ) { proceed(method) }
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

    private fun confirmRemove() {
        if (settings.encryption == EncryptionType.PASSWORD) {
            showError(R.string.settings_dialog_msg_auth_invalid_with_encryption)
            return
        }

        ResultDialog.showWarning(this, R.string.security_dialog_title_warning,
            getString(R.string.security_dialog_msg_remove),
            R.string.continue_on, {
                settings.authMethod = AuthMethod.NONE
                refresh()
            }, android.R.string.cancel, null)
    }

    private fun showError(messageRes: Int) {
        ResultDialog.showFailure(this, R.drawable.ic_shield, R.string.settings_dialog_title_error, messageRes,
            android.R.string.ok, null, 0, null)
    }

    override fun onResume() {
        super.onResume()
        credentialJob.onResume(this)
        if (credentialJob.isBusy)
            showProgress()
    }

    override fun onPause() {
        credentialJob.onPause(this)
        super.onPause()
    }

    override fun onDestroy() {
        hideProgress()
        super.onDestroy()
    }

    override fun shouldDestroyOnScreenOff(): Boolean {
        return false
    }

    companion object {
        /** Survives recreation of this screen; see [RetainedJob]. */
        private val credentialJob = RetainedJob<SecurityActivity>()
    }
}
