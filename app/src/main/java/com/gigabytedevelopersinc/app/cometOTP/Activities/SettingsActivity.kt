@file:Suppress("PackageName", "DEPRECATION", "OVERRIDE_DEPRECATION")
package com.gigabytedevelopersinc.app.cometOTP.Activities

import android.Manifest
import android.app.backup.BackupManager
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.preference.CheckBoxPreference
import android.preference.EditTextPreference
import android.preference.ListPreference
import android.preference.PreferenceCategory
import android.preference.PreferenceFragment
import android.preference.PreferenceManager
import android.provider.DocumentsContract
import android.util.Log
import android.view.View
import android.view.ViewStub
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import com.gigabytedevelopersinc.app.cometOTP.Preferences.CredentialsPreference
import com.gigabytedevelopersinc.app.cometOTP.R
import com.gigabytedevelopersinc.app.cometOTP.Utilities.BackupHelper
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Constants
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Constants.AuthMethod
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Constants.EncryptionType
import com.gigabytedevelopersinc.app.cometOTP.Utilities.DatabaseHelper
import com.gigabytedevelopersinc.app.cometOTP.Utilities.EncryptionChangeHelper
import com.gigabytedevelopersinc.app.cometOTP.Utilities.EncryptionHelper
import com.gigabytedevelopersinc.app.cometOTP.Utilities.GeneralUtils
import com.gigabytedevelopersinc.app.cometOTP.Utilities.KeyStoreHelper
import com.gigabytedevelopersinc.app.cometOTP.Utilities.LauncherIcon
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Settings
import com.gigabytedevelopersinc.app.cometOTP.Utilities.UIHelper
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import org.openintents.openpgp.util.OpenPgpAppPreference
import org.openintents.openpgp.util.OpenPgpKeyPreference
import java.util.Locale
import javax.crypto.SecretKey

class SettingsActivity : BaseActivity(), SharedPreferences.OnSharedPreferenceChangeListener {
    internal var fragment: SettingsFragment? = null

    internal var encryptionKey: SecretKey? = null
    internal var encryptionChanged = false
    private var progress: AlertDialog? = null

    /* Activity result launchers (replace the request-code based onActivityResult()). */
    private val authenticateLauncher: ActivityResultLauncher<Intent> = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data

        if (result.resultCode == RESULT_OK && data != null) {
            val authKey = data.getByteArrayExtra(Constants.EXTRA_AUTH_PASSWORD_KEY)
            val newEnc = data.getStringExtra(Constants.EXTRA_AUTH_NEW_ENCRYPTION)

            if (authKey != null && authKey.isNotEmpty() && newEnc != null && newEnc.isNotEmpty()) {
                val newEncType = EncryptionType.valueOf(newEnc)
                tryEncryptionChange(newEncType, authKey)
            } else {
                Snackbar.make(findViewById<View>(R.id.container_content), R.string.settings_toast_encryption_no_key, Snackbar.LENGTH_LONG).show()
            }
        } else {
            Snackbar.make(findViewById<View>(R.id.container_content), R.string.settings_toast_encryption_auth_failed, Snackbar.LENGTH_LONG).show()
        }
    }

    private val backupLocationLauncher: ActivityResultLauncher<Intent> = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data

        if (result.resultCode == RESULT_OK && data != null && data.data != null) {
            val treeUri = data.data!!
            // Both flags were requested in requestBackupAccess(); persist exactly those.
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            contentResolver.takePersistableUriPermission(treeUri, takeFlags)
            settings.backupLocation = treeUri
        }
    }

    // Android 13+ (API 33): the results of broadcast-triggered backups are reported through
    // notifications, which need the POST_NOTIFICATIONS runtime permission.
    private val notificationPermissionLauncher: ActivityResultLauncher<String> = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted)
            Snackbar.make(findViewById<View>(R.id.container_content), R.string.settings_toast_notifications_denied, Snackbar.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setTitle(R.string.settings_activity_title)
        setContentView(R.layout.activity_container)

        val toolbar = findViewById<Toolbar>(R.id.container_toolbar)
        setSupportActionBar(toolbar)

        val stub = findViewById<ViewStub>(R.id.container_stub)
        stub.inflate()

        val callingIntent = intent
        val keyMaterial = callingIntent.getByteArrayExtra(Constants.EXTRA_SETTINGS_ENCRYPTION_KEY)
        if (keyMaterial != null && keyMaterial.isNotEmpty())
            encryptionKey = EncryptionHelper.generateSymmetricKey(keyMaterial)

        if (savedInstanceState != null) {
            encryptionChanged = savedInstanceState.getBoolean(Constants.EXTRA_SETTINGS_ENCRYPTION_CHANGED, false)

            val encKey = savedInstanceState.getByteArray(Constants.EXTRA_SETTINGS_ENCRYPTION_KEY)
            if (encKey != null) {
                encryptionKey = EncryptionHelper.generateSymmetricKey(encKey)
            }
        }

        val fragment = SettingsFragment()
        this.fragment = fragment

        fragmentManager.beginTransaction()
            .replace(R.id.container_content, fragment)
            .commit()

        val sharedPref = PreferenceManager.getDefaultSharedPreferences(this)
        sharedPref.registerOnSharedPreferenceChangeListener(this)

        // Predictive back: onBackPressed() is no longer invoked when targeting Android 16+.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finishWithResult()
            }
        })
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        outState.putBoolean(Constants.EXTRA_SETTINGS_ENCRYPTION_CHANGED, encryptionChanged)
        val encryptionKey = encryptionKey
        if (encryptionKey != null) {
            outState.putByteArray(Constants.EXTRA_SETTINGS_ENCRYPTION_KEY, encryptionKey.encoded)
        }
    }

    fun finishWithResult() {
        val data = Intent()

        data.putExtra(Constants.EXTRA_SETTINGS_ENCRYPTION_CHANGED, encryptionChanged)
        val encryptionKey = encryptionKey
        if (encryptionKey != null)
            data.putExtra(Constants.EXTRA_SETTINGS_ENCRYPTION_KEY, encryptionKey.encoded)

        setResult(RESULT_OK, data)
        finish()
    }

    override fun onSupportNavigateUp(): Boolean {
        finishWithResult()
        return true
    }

    override fun onDestroy() {
        hideProgress()

        // Without this the activity stays registered for the life of the process. Every later
        // preference write from any other screen would then re-enter the callback below on a
        // destroyed activity whose fragment is detached, which crashed the app.
        PreferenceManager.getDefaultSharedPreferences(this)
            .unregisterOnSharedPreferenceChangeListener(this)
        super.onDestroy()
    }

    override fun onSharedPreferenceChanged(prefs: SharedPreferences?, key: String?) {
        val fragment = fragment
        if (isFinishing || isDestroyed || fragment == null || !fragment.isAdded)
            return

        val backupManager = BackupManager(this)
        backupManager.dataChanged()

        if (key == null)
            return

        if (key == getString(R.string.settings_key_theme) ||
            key == getString(R.string.settings_key_special_features) ||
            key == getString(R.string.settings_key_backup_location) ||
            key == getString(R.string.settings_key_theme_mode) ||
            key == getString(R.string.settings_key_theme_black_auto)) {
            recreate()
        } else if (key == getString(R.string.settings_key_encryption)) {
            if (settings.encryption != EncryptionType.PASSWORD) {
                if (settings.androidBackupServiceEnabled) {
                    UIHelper.showGenericDialog(this,
                        R.string.settings_dialog_title_android_sync,
                        R.string.settings_dialog_msg_android_sync_disabled_encryption
                    )
                }

                settings.androidBackupServiceEnabled = false
                val useAndroidSync = fragment.useAndroidSync
                if (useAndroidSync != null) {
                    useAndroidSync.isEnabled = false
                    useAndroidSync.isChecked = false
                }
            } else {
                fragment.useAndroidSync?.isEnabled = true
            }
        } else if (key == getString(R.string.settings_key_enable_android_backup_service)) {
            Log.d(SettingsActivity::class.java.simpleName,
                "onSharedPreferenceChanged called modifying settings_key_enable_android_backup_service service is now: " +
                    (if (settings.androidBackupServiceEnabled) "enabled" else "disabled"))

            val message = if (settings.androidBackupServiceEnabled) R.string.settings_toast_android_sync_enabled else R.string.settings_toast_android_sync_disabled
            Snackbar.make(findViewById<View>(R.id.container_content), message, Snackbar.LENGTH_SHORT).show()
        } else if (key == getString(R.string.settings_key_launcher_icon)) {
            LauncherIcon.apply(applicationContext, settings.launcherIcon)
            Snackbar.make(findViewById<View>(R.id.container_content),
                R.string.settings_toast_launcher_icon, Snackbar.LENGTH_LONG).show()
        } else if (key == getString(R.string.settings_key_backup_broadcasts)) {
            if (!settings.backupBroadcasts!!.isEmpty())
                ensureNotificationPermission()
        }
        fragment.updateAutoBackup()
    }

    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU)
            return

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun generateNewEncryptionKey() {
        if (settings.encryption == EncryptionType.KEYSTORE) {
            encryptionKey = KeyStoreHelper.loadEncryptionKeyFromKeyStore(this, false)
            encryptionChanged = true
        }
    }

    private fun tryEncryptionChangeWithAuth(newEnc: EncryptionType) {
        val authIntent = Intent(this, AuthenticateActivity::class.java)
        authIntent.putExtra(Constants.EXTRA_AUTH_NEW_ENCRYPTION, newEnc.name)
        authIntent.putExtra(Constants.EXTRA_AUTH_MESSAGE, R.string.auth_msg_confirm_encryption)
        authenticateLauncher.launch(authIntent)
    }

    private fun tryEncryptionChange(newEnc: EncryptionType, newKey: ByteArray?) {
        startEncryptionChange(newEnc, newKey, null, null)
    }

    /** Password encryption: re-encrypts for a new password or PIN, see [CredentialsPreference]. */
    internal fun changeCredentials(method: AuthMethod, password: String) {
        startEncryptionChange(EncryptionType.PASSWORD, null, method, password)
    }

    /**
     * Re-encrypts the database for [newEnc] on a background thread; deriving a key (PBKDF2) and
     * re-encrypting are too slow for the main thread. The key is [newKey], or the one derived
     * from [password] when a new credential for [method] is being set.
     *
     * On success whatever the new key depends on is stored right away on that thread: the new
     * credentials and method, or the new encryption type. Stored state then always matches the
     * key the database is encrypted with, even if no screen is left to show the result. On
     * failure nothing is stored and the old key stays in effect.
     */
    private fun startEncryptionChange(newEnc: EncryptionType, newKey: ByteArray?, method: AuthMethod?, password: String?) {
        if (encryptionJob.isBusy)
            return

        Snackbar.make(findViewById<View>(R.id.container_content), R.string.settings_toast_encryption_changing, Snackbar.LENGTH_LONG).show()
        showProgress()

        val appContext = applicationContext
        val currentKey = encryptionKey
        val encryptionPreferenceKey = getString(R.string.settings_key_encryption)

        encryptionJob.start {
            val settings = Settings(appContext)
            val newCredentials = if (password != null) settings.generateAuthCredentials(password) else null
            val keyBytes = if (password != null) newCredentials?.key else newKey

            // No key (e.g. the credential could not be derived) ends in Status.NO_KEY.
            val result = EncryptionChangeHelper.changeEncryption(appContext, currentKey, newEnc, keyBytes)

            if (result.status == EncryptionChangeHelper.Status.SUCCESS) {
                if (newCredentials != null) {
                    settings.saveAuthCredentials(newCredentials, method)
                } else {
                    // Stored the way the encryption ListPreference persists its value.
                    PreferenceManager.getDefaultSharedPreferences(appContext).edit()
                        .putString(encryptionPreferenceKey, newEnc.name.lowercase(Locale.ROOT))
                        .commit()
                }
            }

            val credentialsChanged = newCredentials != null
            val outcome: (SettingsActivity) -> Unit = { it.onEncryptionChangeDone(newEnc, credentialsChanged, result) }
            outcome
        }
    }

    /** Runs on whichever instance is resumed when [startEncryptionChange]'s job has finished. */
    private fun onEncryptionChangeDone(newEnc: EncryptionType, credentialsChanged: Boolean, result: EncryptionChangeHelper.Result) {
        hideProgress()

        when (result.status) {
            EncryptionChangeHelper.Status.SUCCESS -> {
                encryptionKey = result.newKey
                encryptionChanged = true

                // Already stored; this updates the preferences shown on screen.
                fragment?.encryption?.value = newEnc.name.lowercase(Locale.ROOT)
                if (credentialsChanged)
                    fragment?.credentials?.updateSummary()

                Snackbar.make(findViewById<View>(R.id.container_content), R.string.settings_toast_encryption_change_success, Snackbar.LENGTH_LONG).show()
            }
            EncryptionChangeHelper.Status.BACKUP_FAILED -> {
                Snackbar.make(findViewById<View>(R.id.container_content), R.string.settings_toast_encryption_backup_failed, Snackbar.LENGTH_LONG).show()
            }
            EncryptionChangeHelper.Status.NO_KEY, EncryptionChangeHelper.Status.SAVE_FAILED -> {
                Snackbar.make(findViewById<View>(R.id.container_content), R.string.settings_toast_encryption_change_failed, Snackbar.LENGTH_LONG).show()
            }
        }
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

    override fun onResume() {
        super.onResume()
        encryptionJob.onResume(this)
        if (encryptionJob.isBusy)
            showProgress()
    }

    override fun onPause() {
        encryptionJob.onPause(this)
        super.onPause()
    }

    // Finishing on screen off would lose the result of a running encryption change.
    override fun shouldDestroyOnScreenOff(): Boolean {
        return !encryptionJob.isBusy
    }

    private fun requestBackupAccess() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
            or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)

        if (GeneralUtils.isOreo() && settings.isBackupLocationSet)
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, settings.backupLocation)

        backupLocationLauncher.launch(intent)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        // OpenPgpKeyPreference (openpgp-api) still starts its key chooser with the request-code
        // based API, so this is the only result that has to be routed by hand.
        val fragment = fragment
        if (fragment != null && fragment.pgpSigningKey != null)
            fragment.pgpSigningKey!!.handleOnActivityResult(requestCode, resultCode, data)
    }

    companion object {
        /** Survives recreation of this screen; see [RetainedJob]. */
        private val encryptionJob = RetainedJob<SettingsActivity>()
    }

    class SettingsFragment : PreferenceFragment() {
        internal var catUI: PreferenceCategory? = null

        internal lateinit var settings: Settings
        internal var encryption: ListPreference? = null
        internal var credentials: CredentialsPreference? = null
        internal var useAutoBackup: ListPreference? = null
        internal var useAndroidSync: CheckBoxPreference? = null

        internal var pgpEncryptionKey: EditTextPreference? = null
        internal var pgpSigningKey: OpenPgpKeyPreference? = null
        internal var themeMode: ListPreference? = null
        internal var themeBlack: CheckBoxPreference? = null
        internal var theme: ListPreference? = null

        fun encryptionChangeWithDialog(encryptionType: EncryptionType) {
            val builder = MaterialAlertDialogBuilder(activity)
            builder.setTitle(R.string.settings_dialog_title_warning)
                .setMessage(R.string.settings_dialog_msg_encryption_change)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    if (encryptionType == EncryptionType.PASSWORD)
                        (activity as SettingsActivity).tryEncryptionChangeWithAuth(encryptionType)
                    else if (encryptionType == EncryptionType.KEYSTORE)
                        (activity as SettingsActivity).tryEncryptionChange(encryptionType, null)
                }
                .setNegativeButton(android.R.string.cancel) { _, _ ->
                }
                .setCancelable(false)
                .create()
                .show()
        }

        fun updateAutoBackup() {
            val useAutoBackup = useAutoBackup
            if (useAutoBackup != null && activity != null) {
                useAutoBackup.isEnabled = BackupHelper.autoBackupType(activity) == Constants.BackupType.ENCRYPTED
                if (!useAutoBackup.isEnabled)
                    useAutoBackup.value = Constants.AutoBackup.OFF.toString().lowercase(Locale.ENGLISH)

                if (useAutoBackup.isEnabled) {
                    useAutoBackup.setSummary(R.string.settings_desc_auto_backup_password_enc)
                } else {
                    useAutoBackup.setSummary(R.string.settings_desc_auto_backup_requirements)
                }
            }
        }

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)

            settings = Settings(activity)

            val sharedPref = PreferenceManager.getDefaultSharedPreferences(activity.baseContext)

            addPreferencesFromResource(R.xml.preferences)

            val credentialsPreference = findPreference(getString(R.string.settings_key_auth)) as CredentialsPreference
            credentials = credentialsPreference
            credentialsPreference.setEncryptionChangeCallback { method, password -> (activity as SettingsActivity).changeCredentials(method, password) }

            val blockAutofill = findPreference(getString(R.string.settings_key_block_autofill)) as CheckBoxPreference
            val autoUnlockAfterAutofill = findPreference(getString(R.string.settings_key_auto_unlock_after_autofill)) as CheckBoxPreference
            if (GeneralUtils.isOreo()) {
                blockAutofill.isEnabled = true
                blockAutofill.setSummary(R.string.settings_desc_block_autofill)
                autoUnlockAfterAutofill.isEnabled = true
                autoUnlockAfterAutofill.setSummary(R.string.settings_desc_auto_unlock_after_autofill)
            } else {
                blockAutofill.isEnabled = false
                blockAutofill.setSummary(R.string.settings_desc_autofill_requires_android_o)
                autoUnlockAfterAutofill.isEnabled = false
                autoUnlockAfterAutofill.setSummary(R.string.settings_desc_autofill_requires_android_o)
            }

            // Authentication
            catUI = findPreference(getString(R.string.settings_key_cat_ui)) as PreferenceCategory?
            encryption = findPreference(getString(R.string.settings_key_encryption)) as ListPreference?
            themeMode = findPreference(getString(R.string.settings_key_theme_mode)) as ListPreference?
            themeBlack = findPreference(getString(R.string.settings_key_theme_black_auto)) as CheckBoxPreference?
            theme = findPreference(getString(R.string.settings_key_theme)) as ListPreference?

            encryption!!.setOnPreferenceChangeListener { _, o ->
                val newEncryption = o as String
                val encryptionType = EncryptionType.valueOf(newEncryption.uppercase(Locale.ROOT))
                val oldEncryptionType = settings.encryption
                val authMethod = settings.authMethod

                if (encryptionType != oldEncryptionType) {
                    if (encryptionType == EncryptionType.PASSWORD) {
                        if (authMethod != AuthMethod.PASSWORD && authMethod != AuthMethod.PIN) {
                            UIHelper.showGenericDialog(activity, R.string.settings_dialog_title_error, R.string.settings_dialog_msg_encryption_invalid_with_auth)
                            return@setOnPreferenceChangeListener false
                        } else {
                            if (settings.authCredentials.isEmpty()) {
                                UIHelper.showGenericDialog(activity, R.string.settings_dialog_title_error, R.string.settings_dialog_msg_encryption_invalid_without_credentials)
                                return@setOnPreferenceChangeListener false
                            }
                        }

                        encryptionChangeWithDialog(EncryptionType.PASSWORD)
                    } else if (encryptionType == EncryptionType.KEYSTORE) {
                        encryptionChangeWithDialog(EncryptionType.KEYSTORE)
                    }
                }

                false
            }

            // Backup location
            val backupLocation = findPreference(getString(R.string.settings_key_backup_location))!!

            if (settings.isBackupLocationSet) {
                backupLocation.setSummary(R.string.settings_desc_backup_location_set)
            } else {
                backupLocation.setSummary(R.string.settings_desc_backup_location)
            }

            backupLocation.setOnPreferenceClickListener {
                (activity as SettingsActivity).requestBackupAccess()
                true
            }

            // OpenPGP
            val pgpProvider = findPreference(getString(R.string.settings_key_openpgp_provider)) as OpenPgpAppPreference
            pgpEncryptionKey = findPreference(getString(R.string.settings_key_openpgp_key_encrypt)) as EditTextPreference?
            pgpSigningKey = findPreference(getString(R.string.settings_key_openpgp_key_sign)) as OpenPgpKeyPreference?

            pgpSigningKey!!.setOpenPgpProvider(pgpProvider.value)

            pgpEncryptionKey!!.isEnabled = pgpProvider.value != null && !pgpProvider.value.isEmpty()

            pgpProvider.setOnPreferenceChangeListener { _, newValue ->
                pgpEncryptionKey!!.isEnabled = newValue != null && !(newValue as String).isEmpty()
                pgpSigningKey!!.setOpenPgpProvider(newValue as String?)

                true
            }

            useAutoBackup = findPreference(getString(R.string.settings_key_auto_backup_password_enc)) as ListPreference?
            updateAutoBackup()

            val useAndroidSync = findPreference(getString(R.string.settings_key_enable_android_backup_service)) as CheckBoxPreference
            this.useAndroidSync = useAndroidSync
            useAndroidSync.isEnabled = settings.encryption == EncryptionType.PASSWORD
            if (!useAndroidSync.isEnabled)
                useAndroidSync.isChecked = false

            if (sharedPref.contains(getString(R.string.settings_key_special_features)) &&
                sharedPref.getBoolean(getString(R.string.settings_key_special_features), false)) {
                addPreferencesFromResource(R.xml.preferences_special)

                val clearKeyStore = findPreference(getString(R.string.settings_key_clear_keystore))!!
                clearKeyStore.setOnPreferenceClickListener {
                    val builder = MaterialAlertDialogBuilder(activity)

                    builder.setTitle(R.string.settings_dialog_title_clear_keystore)
                    if (settings.encryption == EncryptionType.PASSWORD)
                        builder.setMessage(R.string.settings_dialog_msg_clear_keystore_password)
                    else if (settings.encryption == EncryptionType.KEYSTORE)
                        builder.setMessage(R.string.settings_dialog_msg_clear_keystore_keystore)

                    builder.setPositiveButton(android.R.string.ok) { _, _ ->
                        KeyStoreHelper.wipeKeys(activity)
                        if (settings.encryption == EncryptionType.KEYSTORE) {
                            DatabaseHelper.wipeDatabase(activity)
                            (activity as SettingsActivity).generateNewEncryptionKey()
                        }
                    }
                    builder.setNegativeButton(android.R.string.cancel) { _, _ ->
                    }

                    builder.setCancelable(false).create().show()
                    false
                }
            }

            // Remove Theme Mode selection option for devices below Android 10. Disable theme selection if Theme Mode is set auto
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                catUI!!.removePreference(themeMode)
                catUI!!.removePreference(themeBlack)
            } else {
                if (sharedPref.getString(getString(R.string.settings_key_theme_mode), getString(R.string.settings_default_theme_mode))!! == "auto")
                    catUI!!.removePreference(theme)
                else
                    catUI!!.removePreference(themeBlack)
            }

            val clearCache = findPreference(getString(R.string.settings_key_clear_cache))!!
            clearCache.setOnPreferenceClickListener {
                val clearCacheIntent = Intent(activity, CacheActivity::class.java)
                startActivity(clearCacheIntent)
                false
            }
        }
    }
}
