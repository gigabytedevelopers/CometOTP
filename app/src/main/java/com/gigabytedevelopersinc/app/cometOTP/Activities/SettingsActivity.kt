@file:Suppress("PackageName", "DEPRECATION", "OVERRIDE_DEPRECATION")
package com.gigabytedevelopersinc.app.cometOTP.Activities

import android.Manifest
import android.app.Fragment
import android.app.backup.BackupManager
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.preference.CheckBoxPreference
import android.preference.ListPreference
import android.preference.Preference
import android.preference.PreferenceCategory
import android.preference.PreferenceFragment
import android.preference.PreferenceGroup
import android.preference.PreferenceManager
import android.provider.DocumentsContract
import android.text.format.DateFormat
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewStub
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.lifecycle.ViewModelProvider
import com.gigabytedevelopersinc.app.cometOTP.Preferences.TextFieldPreference
import com.gigabytedevelopersinc.app.cometOTP.R
import com.gigabytedevelopersinc.app.cometOTP.Utilities.AppTheme
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
import com.gigabytedevelopersinc.app.cometOTP.Utilities.ThemePick
import com.gigabytedevelopersinc.app.cometOTP.Utilities.UIHelper
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import org.openintents.openpgp.util.OpenPgpAppPreference
import org.openintents.openpgp.util.OpenPgpKeyPreference
import java.util.Date
import java.util.Locale
import javax.crypto.SecretKey

/**
 * Settings: a hub with a row for each group of settings (see [Screen]), each opening a screen of
 * its own, and rows for the Tags and Removed accounts screens. The screens are fragments in this
 * one activity, which holds the database key for all of them and returns it when it closes.
 */
class SettingsActivity : BaseActivity(), SharedPreferences.OnSharedPreferenceChangeListener {
    /** The group of settings on screen; null while the hub shows. */
    internal val fragment: SettingsFragment?
        get() = fragmentManager.findFragmentById(R.id.container_content) as? SettingsFragment

    private lateinit var retainedKey: RetainedDatabaseKey
    internal var encryptionKey: SecretKey?
        get() = retainedKey.key
        set(value) {
            retainedKey.key = value
        }
    internal var encryptionChanged = false
    /** Whether the key has been changed since the screen was started, see initialDatabaseKeyState(). */
    private var launchKeyStale = false
    /** Whether accounts were changed from here (restored, or tags edited): the list then reloads. */
    private var entriesChanged = false
    /** The preference to scroll to once, see [EXTRA_SCROLL_TO_KEY]. */
    private var scrollRequest: String? = null
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

    // The Security screen re-encrypts the database when the password it is encrypted with changes.
    private val securityLauncher: ActivityResultLauncher<Intent> = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (result.resultCode == RESULT_OK && data != null &&
            data.getBooleanExtra(Constants.EXTRA_SETTINGS_ENCRYPTION_CHANGED, false)) {
            val keyMaterial = data.getByteArrayExtra(Constants.EXTRA_SETTINGS_ENCRYPTION_KEY)
            encryptionKey = if (keyMaterial != null && keyMaterial.isNotEmpty())
                EncryptionHelper.generateSymmetricKey(keyMaterial) else null
            encryptionChanged = true
        }
    }

    // Tags and Removed accounts ask for the list to reload, as they do when opened from the list.
    private val manageLauncher: ActivityResultLauncher<Intent> = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data?.getBooleanExtra(EXTRA_RELOAD, false) == true)
            entriesChanged = true
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

        // The key is kept across configuration changes in memory only, see RetainedDatabaseKey.
        retainedKey = ViewModelProvider(this)[RetainedDatabaseKey::class.java]
        launchKeyStale = savedInstanceState?.getBoolean(RetainedDatabaseKey.STATE_LAUNCH_KEY_STALE, false) ?: false
        val (keySource, changed) = initialDatabaseKeyState(retainedKey.initialized,
            savedInstanceState?.getBoolean(Constants.EXTRA_SETTINGS_ENCRYPTION_CHANGED, false), launchKeyStale)
        encryptionChanged = changed
        if (keySource != DatabaseKeySource.RETAINED) {
            val keyMaterial = if (keySource == DatabaseKeySource.LAUNCH_INTENT)
                intent.getByteArrayExtra(Constants.EXTRA_SETTINGS_ENCRYPTION_KEY) else null
            retainedKey.key = if (keyMaterial != null && keyMaterial.isNotEmpty())
                EncryptionHelper.generateSymmetricKey(keyMaterial) else null
            retainedKey.initialized = true
        }

        entriesChanged = savedInstanceState?.getBoolean(STATE_ENTRIES_CHANGED, false) ?: false
        // Taken here, once, for the screen the system restores; see takeScrollRequest().
        scrollRequest = intent.getStringExtra(EXTRA_SCROLL_TO_KEY)
        intent.removeExtra(EXTRA_SCROLL_TO_KEY)

        // After a recreate() the system restores the hub and the open screen, back stack and all.
        if (savedInstanceState == null) {
            fragmentManager.beginTransaction()
                .replace(R.id.container_content, HubFragment())
                .commit()
        }

        val sharedPref = PreferenceManager.getDefaultSharedPreferences(this)
        sharedPref.registerOnSharedPreferenceChangeListener(this)

        // Predictive back: onBackPressed() is no longer invoked when targeting Android 16+.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                goBack()
            }
        })
    }

    /** From a screen back to the hub, from the hub back to where Settings was opened. */
    private fun goBack() {
        if (fragmentManager.backStackEntryCount > 0)
            fragmentManager.popBackStack()
        else
            finishWithResult()
    }

    internal fun openScreen(screen: Screen) {
        val fragment = SettingsFragment()
        fragment.arguments = Bundle().apply { putString(ARG_SCREEN, screen.name) }
        fragmentManager.beginTransaction()
            .replace(R.id.container_content, fragment)
            .addToBackStack(null)
            .commit()
    }

    /** The preference to scroll to, once: see [EXTRA_SCROLL_TO_KEY]. */
    internal fun takeScrollRequest(): String? {
        val key = scrollRequest
        scrollRequest = null
        return key
    }

    private fun intentWithKey(screen: Class<*>): Intent {
        val intent = Intent(this, screen)
        val encryptionKey = encryptionKey
        if (encryptionKey != null)
            intent.putExtra(Constants.EXTRA_SETTINGS_ENCRYPTION_KEY, encryptionKey.encoded)
        return intent
    }

    /** The Security screen, the one place the lock method is set. */
    internal fun openSecurity() {
        securityLauncher.launch(intentWithKey(SecurityActivity::class.java))
    }

    internal fun openTags() {
        manageLauncher.launch(intentWithKey(TagsActivity::class.java))
    }

    internal fun openRemovedAccounts() {
        manageLauncher.launch(intentWithKey(RemovedAccountsActivity::class.java))
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        outState.putBoolean(Constants.EXTRA_SETTINGS_ENCRYPTION_CHANGED, encryptionChanged)
        outState.putBoolean(RetainedDatabaseKey.STATE_LAUNCH_KEY_STALE, launchKeyStale || encryptionChanged)
        outState.putBoolean(STATE_ENTRIES_CHANGED, entriesChanged)
    }

    fun finishWithResult() {
        val data = Intent()

        data.putExtra(Constants.EXTRA_SETTINGS_ENCRYPTION_CHANGED, encryptionChanged)
        val encryptionKey = encryptionKey
        if (encryptionKey != null)
            data.putExtra(Constants.EXTRA_SETTINGS_ENCRYPTION_KEY, encryptionKey.encoded)
        if (entriesChanged)
            data.putExtra(EXTRA_RELOAD, true)

        setResult(RESULT_OK, data)
        finish()
    }

    override fun onSupportNavigateUp(): Boolean {
        goBack()
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
            key == getString(R.string.settings_key_theme_black_auto) ||
            key == getString(R.string.settings_key_dynamic_colors)) {
            // Drawn again in the new look, still at the theme picker rather than at the top.
            if (key != getString(R.string.settings_key_special_features) && key != getString(R.string.settings_key_backup_location))
                intent.putExtra(EXTRA_SCROLL_TO_KEY, getString(R.string.settings_key_theme_picker))
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
        startEncryptionChange(newEnc, newKey)
    }

    /**
     * Re-encrypts the database for [newEnc] with [newKey] on a background thread; re-encrypting
     * is too slow for the main thread. (A new password or PIN is set on the Security screen.)
     *
     * On success the new encryption type is stored right away on that thread. Stored state then
     * always matches the key the database is encrypted with, even if no screen is left to show
     * the result. On failure nothing is stored and the old key stays in effect.
     */
    private fun startEncryptionChange(newEnc: EncryptionType, newKey: ByteArray?) {
        if (encryptionJob.isBusy)
            return

        Snackbar.make(findViewById<View>(R.id.container_content), R.string.settings_toast_encryption_changing, Snackbar.LENGTH_LONG).show()
        showProgress()

        val appContext = applicationContext
        val currentKey = encryptionKey
        val encryptionPreferenceKey = getString(R.string.settings_key_encryption)

        encryptionJob.start {
            val settings = Settings(appContext)

            // Stored by changeEncryption() right after the re-encryption; if that write fails the
            // old value is put back and the database returns to the old key.
            val before = settings.storedValues(R.string.settings_key_encryption)
            val store = {
                // Stored the way the encryption ListPreference persists its value.
                PreferenceManager.getDefaultSharedPreferences(appContext).edit()
                    .putString(encryptionPreferenceKey, newEnc.name.lowercase(Locale.ROOT))
                    .commit()
            }
            val revert = { settings.restoreValues(before); Unit }

            // No key ends in Status.NO_KEY.
            val result = EncryptionChangeHelper.changeEncryption(appContext, currentKey, newEnc, newKey, store, revert)

            val outcome: (SettingsActivity) -> Unit = { it.onEncryptionChangeDone(newEnc, result) }
            outcome
        }
    }

    /** Runs on whichever instance is resumed when [startEncryptionChange]'s job has finished. */
    private fun onEncryptionChangeDone(newEnc: EncryptionType, result: EncryptionChangeHelper.Result) {
        hideProgress()

        when (result.status) {
            EncryptionChangeHelper.Status.SUCCESS -> {
                encryptionKey = result.newKey
                encryptionChanged = true

                // Already stored; this updates the preferences shown on screen.
                fragment?.encryption?.value = newEnc.name.lowercase(Locale.ROOT)

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
        /** A preference key: after a recreate(), the settings list scrolls to it. */
        const val EXTRA_SCROLL_TO_KEY = "settings_scroll_to_key"
        /** Asks the list to reload its accounts, as the Tags screen does. */
        private const val EXTRA_RELOAD = "reload"
        private const val STATE_ENTRIES_CHANGED = "settings_entries_changed"
        private const val ARG_SCREEN = "settings_screen"
        private const val SCROLL_ATTEMPTS = 10
        private const val SCROLL_RETRY_MS = 50L
        /** Survives recreation of this screen; see [RetainedJob]. */
        private val encryptionJob = RetainedJob<SettingsActivity>()
    }

    /** The groups of settings, each a screen: its title, and the categories of preferences.xml it shows. */
    internal enum class Screen(val title: Int, val categories: List<Int>) {
        SECURITY(R.string.settings_category_title_security,
            listOf(R.string.settings_key_cat_security, R.string.settings_key_cat_special)),
        APPEARANCE(R.string.settings_category_title_appearance, listOf(R.string.settings_key_cat_ui)),
        BEHAVIOUR(R.string.settings_category_title_behaviour, listOf(R.string.settings_key_cat_behaviour)),
        BACKUP(R.string.settings_category_title_backup, listOf(R.string.settings_key_cat_backup)),
        STORAGE(R.string.settings_hub_storage, listOf(R.string.settings_key_cat_storage))
    }

    /** The hub: a row for each [Screen], then the screens the settings work with. */
    class HubFragment : Fragment() {
        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
            val v = inflater.inflate(R.layout.content_settings_hub, container, false)
            val settings = activity as SettingsActivity

            bindRow(v, R.id.hub_security, R.drawable.ic_shield, Screen.SECURITY.title, R.string.settings_hub_security_desc) {
                settings.openScreen(Screen.SECURITY)
            }
            bindRow(v, R.id.hub_appearance, R.drawable.ic_palette, Screen.APPEARANCE.title, R.string.settings_hub_appearance_desc) {
                settings.openScreen(Screen.APPEARANCE)
            }
            bindRow(v, R.id.hub_behaviour, R.drawable.ic_touch_app, Screen.BEHAVIOUR.title, R.string.settings_hub_behaviour_desc) {
                settings.openScreen(Screen.BEHAVIOUR)
            }
            bindRow(v, R.id.hub_backup, R.drawable.ic_backup_cloud, Screen.BACKUP.title, R.string.settings_hub_backup_desc) {
                settings.openScreen(Screen.BACKUP)
            }
            bindRow(v, R.id.hub_storage, R.drawable.ic_storage, Screen.STORAGE.title, R.string.settings_hub_storage_desc) {
                settings.openScreen(Screen.STORAGE)
            }
            bindRow(v, R.id.hub_tags, R.drawable.ic_tag, R.string.nav_tags, R.string.settings_hub_tags_desc) {
                settings.openTags()
            }
            bindRow(v, R.id.hub_removed, R.drawable.ic_restore_from_trash, R.string.nav_removed, R.string.settings_hub_removed_desc) {
                settings.openRemovedAccounts()
            }
            ViewCompat.setAccessibilityHeading(v.findViewById(R.id.hub_section_manage), true)
            return v
        }

        override fun onResume() {
            super.onResume()
            activity.setTitle(R.string.settings_activity_title)
        }

        private fun bindRow(parent: View, id: Int, icon: Int, title: Int, subtitle: Int, action: () -> Unit) {
            val row = parent.findViewById<View>(id)
            row.findViewById<ImageView>(R.id.row_icon).setImageResource(icon)
            row.findViewById<TextView>(R.id.row_title).setText(title)
            val subtitleView = row.findViewById<TextView>(R.id.row_subtitle)
            subtitleView.setText(subtitle)
            subtitleView.visibility = View.VISIBLE
            row.setOnClickListener { action() }
        }
    }

    /** The settings of one [Screen], taken from preferences.xml. */
    class SettingsFragment : PreferenceFragment() {
        internal var catUI: PreferenceCategory? = null

        internal lateinit var settings: Settings
        internal var encryption: ListPreference? = null
        internal var appLock: Preference? = null
        private var screenshots: CheckBoxPreference? = null
        private val screenshotExpiry = Runnable { updateScreenshots() }
        internal var useAutoBackup: ListPreference? = null
        internal var useAndroidSync: CheckBoxPreference? = null

        internal var pgpEncryptionKey: TextFieldPreference? = null
        internal var pgpSigningKey: OpenPgpKeyPreference? = null
        internal var themeBlack: CheckBoxPreference? = null

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

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            scrollToRequestedPreference(view)
        }

        /**
         * After the screen was drawn again for a change it shows (a theme, a list style), goes
         * back to the setting that was changed, see [EXTRA_SCROLL_TO_KEY].
         */
        private fun scrollToRequestedPreference(view: View) {
            val key = (activity as SettingsActivity).takeScrollRequest() ?: return
            val target = findPreference(key) ?: return
            val list = view.findViewById<ListView>(android.R.id.list) ?: return
            scrollWhenBound(list, target, SCROLL_ATTEMPTS)
        }

        /** The preferences are bound to the list a moment after the view is created: waits for them. */
        private fun scrollWhenBound(list: ListView, target: Preference, attemptsLeft: Int) {
            list.postDelayed({
                val adapter = list.adapter
                val index = if (adapter == null) -1 else (0 until adapter.count).firstOrNull { adapter.getItem(it) === target } ?: -1
                if (index >= 0)
                    list.setSelection(index)
                else if (attemptsLeft > 1)
                    scrollWhenBound(list, target, attemptsLeft - 1)
            }, SCROLL_RETRY_MS)
        }

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)

            settings = Settings(activity)

            val sharedPref = PreferenceManager.getDefaultSharedPreferences(activity.baseContext)

            addPreferencesFromResource(R.xml.preferences)

            val appLock = findPreference(getString(R.string.settings_key_app_lock))!!
            this.appLock = appLock
            appLock.setOnPreferenceClickListener {
                (activity as SettingsActivity).openSecurity()
                true
            }

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

            // Screenshots: allowed from when they are switched on, for the time chosen below.
            val screenshots = findPreference(getString(R.string.settings_key_enable_screenshot)) as CheckBoxPreference
            this.screenshots = screenshots
            screenshots.setOnPreferenceChangeListener { _, newValue ->
                if (newValue as Boolean)
                    settings.allowScreenshots(settings.screenshotDurationMinutes)
                else
                    settings.blockScreenshots()
                // Once the switch has taken the new value.
                view?.post { updateScreenshots() }
                true
            }
            findPreference(getString(R.string.settings_key_screenshot_duration))!!.setOnPreferenceChangeListener { _, newValue ->
                // A new time limit counts from now.
                if (settings.screenshotsAllowed())
                    settings.allowScreenshots((newValue as String).toIntOrNull() ?: 0)
                view?.post { updateScreenshots() }
                true
            }

            // Authentication
            catUI = findPreference(getString(R.string.settings_key_cat_ui)) as PreferenceCategory?
            encryption = findPreference(getString(R.string.settings_key_encryption)) as ListPreference?
            themeBlack = findPreference(getString(R.string.settings_key_theme_black_auto)) as CheckBoxPreference?

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
            pgpEncryptionKey = findPreference(getString(R.string.settings_key_openpgp_key_encrypt)) as TextFieldPreference?
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

            // "Black theme in auto mode" only means something while the theme picker is on Auto,
            // which needs the system dark mode of Android 10 and later.
            if (Settings(activity).themePick != ThemePick.AUTO)
                catUI!!.removePreference(themeBlack)
            // Wallpaper colours are there from Android 12 on.
            if (!AppTheme.dynamicColorsAvailable)
                catUI!!.removePreference(findPreference(getString(R.string.settings_key_dynamic_colors)))

            val clearCache = findPreference(getString(R.string.settings_key_clear_cache))!!
            clearCache.setOnPreferenceClickListener {
                val clearCacheIntent = Intent(activity, CacheActivity::class.java)
                startActivity(clearCacheIntent)
                false
            }

            val screen = arguments?.getString(ARG_SCREEN)
            if (screen != null)
                showOnly(Screen.valueOf(screen))
        }

        /**
         * Keeps the settings of [screen] and drops the other groups. The first category's settings
         * show without its heading, which is the screen's title already; a further category (the
         * special features on the Security screen) keeps its heading, below them.
         */
        private fun showOnly(screen: Screen) {
            val root = preferenceScreen
            val keys = screen.categories.map { getString(it) }
            val groups = (0 until root.preferenceCount).map { root.getPreference(it) }
            for (group in groups) {
                if (group.key !in keys)
                    root.removePreference(group)
            }

            val main = findPreference(keys.first()) as PreferenceGroup
            root.removePreference(main)
            val children = (0 until main.preferenceCount).map { main.getPreference(it) }
            for (i in 0 until root.preferenceCount)
                root.getPreference(i).order = children.size + i
            // In their order, so that each comes back after the setting it depends on.
            children.forEachIndexed { i, child ->
                main.removePreference(child)
                child.order = i
                root.addPreference(child)
            }
        }

        override fun onResume() {
            super.onResume()
            val screen = arguments?.getString(ARG_SCREEN)
            if (screen != null)
                activity.setTitle(Screen.valueOf(screen).title)
            updateAppLockSummary()
            updateScreenshots()
        }

        override fun onPause() {
            view?.removeCallbacks(screenshotExpiry)
            super.onPause()
        }

        /** Shows whether screenshots are allowed and until when, and switches off when the time is up. */
        private fun updateScreenshots() {
            val screenshots = screenshots ?: return
            view?.removeCallbacks(screenshotExpiry)

            val allowed = settings.screenshotsAllowed()
            if (screenshots.isChecked != allowed)
                screenshots.isChecked = allowed
            val until = if (allowed) settings.screenshotsAllowedUntil else 0
            screenshots.summary = when {
                !allowed -> getString(R.string.settings_desc_enable_screenshot)
                until > 0 -> getString(R.string.settings_desc_screenshots_until,
                    DateFormat.getTimeFormat(activity).format(Date(until)))
                else -> getString(R.string.settings_desc_screenshots_until_off)
            }
            if (until > 0)
                view?.postDelayed(screenshotExpiry, until - System.currentTimeMillis())
        }

        /** Shows the lock method as the Security screen does; it can have changed there. */
        private fun updateAppLockSummary() {
            appLock?.setSummary(when (settings.authMethod) {
                AuthMethod.PIN -> R.string.security_status_pin
                AuthMethod.PASSWORD -> R.string.security_status_password
                AuthMethod.DEVICE -> R.string.security_status_device
                AuthMethod.NONE -> R.string.settings_app_lock_off
            })
        }
    }
}
