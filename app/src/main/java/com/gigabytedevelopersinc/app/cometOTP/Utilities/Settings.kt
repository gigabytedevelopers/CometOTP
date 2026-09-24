@file:Suppress("PackageName", "DEPRECATION")
package com.gigabytedevelopersinc.app.cometOTP.Utilities

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.preference.PreferenceManager
import android.util.Base64
import com.gigabytedevelopersinc.app.cometOTP.Preferences.CredentialsPreference
import com.gigabytedevelopersinc.app.cometOTP.R
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Constants.AuthMethod
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Constants.EncryptionType
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Constants.SortMode
import java.nio.charset.StandardCharsets
import java.security.NoSuchAlgorithmException
import java.security.spec.InvalidKeySpecException
import java.util.Collections
import java.util.Locale

class Settings(private val context: Context) {
    private val settings: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

    init {
        migrateDeprecatedSettings()
    }

    private fun migrateDeprecatedSettings() {
        if (settings.contains(getResString(R.string.settings_key_auth_password))) {
            setAuthCredentials(getString(R.string.settings_key_auth_password, ""))
            remove(R.string.settings_key_auth_password)
        }

        if (settings.contains(getResString(R.string.settings_key_auth_pin))) {
            setAuthCredentials(getString(R.string.settings_key_auth_pin, ""))
            remove(R.string.settings_key_auth_pin)
        }

        if (settings.contains(getResString(R.string.settings_key_tap_to_reveal))) {
            if (getBoolean(R.string.settings_key_tap_to_reveal, false)) {
                setString(R.string.settings_key_tap_single, Constants.TapMode.REVEAL.toString().lowercase(Locale.ENGLISH))
            }
            remove(R.string.settings_key_tap_to_reveal)
        }

        if (settings.contains(getResString(R.string.settings_key_label_scroll))) {
            if (getBoolean(R.string.settings_key_label_scroll, false)) {
                setString(R.string.settings_key_label_display, Constants.LabelDisplay.SCROLL.toString().lowercase(Locale.ENGLISH))
            }
            remove(R.string.settings_key_label_scroll)
        }

        if (settings.contains(getResString(R.string.settings_key_backup_password))) {
            val plainPassword = backupPassword

            try {
                val key = KeyStoreHelper.loadOrGenerateAsymmetricKeyPair(context, Constants.KEYSTORE_ALIAS_PASSWORD)
                val encPassword = EncryptionHelper.encrypt(key!!.public, plainPassword.toByteArray(StandardCharsets.UTF_8))

                setString(R.string.settings_key_backup_password_enc, Base64.encodeToString(encPassword, Base64.URL_SAFE))

                remove(R.string.settings_key_backup_password)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun getResString(resId: Int): String {
        return context.getString(resId)
    }

    private fun getResInt(resId: Int): Int {
        return context.resources.getInteger(resId)
    }

    // SharedPreferences.getString only returns null when the default is null, which it never is
    // here, so these two are non-null.
    private fun getString(keyId: Int, defaultId: Int): String {
        return settings.getString(getResString(keyId), getResString(defaultId))!!
    }

    private fun getString(keyId: Int, defaultValue: String): String {
        return settings.getString(getResString(keyId), defaultValue)!!
    }

    fun getBoolean(keyId: Int, defaultValue: Boolean): Boolean {
        return settings.getBoolean(getResString(keyId), defaultValue)
    }

    private fun getInt(keyId: Int, defaultId: Int): Int {
        return settings.getInt(getResString(keyId), getResInt(defaultId))
    }

    private fun getIntValue(keyId: Int, defaultValue: Int): Int {
        return settings.getInt(getResString(keyId), defaultValue)
    }

    @Suppress("SameParameterValue")
    private fun getLong(keyId: Int, defaultValue: Long): Long {
        return settings.getLong(getResString(keyId), defaultValue)
    }

    @Suppress("SameParameterValue")
    private fun getStringSet(keyId: Int, defaultValue: Set<String>): MutableSet<String> {
        return HashSet(settings.getStringSet(getResString(keyId), defaultValue)!!)
    }

    fun setBoolean(keyId: Int, value: Boolean) {
        settings.edit()
            .putBoolean(getResString(keyId), value)
            .apply()
    }

    @Suppress("SameParameterValue")
    private fun setInt(keyId: Int, value: Int) {
        settings.edit()
            .putInt(getResString(keyId), value)
            .apply()
    }

    private fun setString(keyId: Int, value: String) {
        settings.edit()
            .putString(getResString(keyId), value)
            .apply()
    }

    @Suppress("SameParameterValue")
    private fun setStringSet(keyId: Int, value: Set<String>) {
        settings.edit()
            .putStringSet(getResString(keyId), value)
            .apply()
    }

    private fun remove(keyId: Int) {
        settings.edit()
            .remove(getResString(keyId))
            .apply()
    }

    @Suppress("ApplySharedPref")
    fun clear(keep_auth: Boolean) {
        val authMethod = this.authMethod
        val authCredentials = this.authCredentials
        val authSalt = this.salt
        val authIterations = this.iterations

        val warningShown = this.firstTimeWarningShown

        val editor = settings.edit()
        editor.clear()

        editor.putBoolean(getResString(R.string.settings_key_security_backup_warning), warningShown)

        if (keep_auth) {
            editor.putString(getResString(R.string.settings_key_auth), authMethod.toString().lowercase(Locale.ENGLISH))

            if (authCredentials.isNotEmpty()) {
                editor.putString(getResString(R.string.settings_key_auth_credentials), authCredentials)
                editor.putInt(getResString(R.string.settings_key_auth_iterations), authIterations)

                val encodedSalt = Base64.encodeToString(authSalt, Base64.URL_SAFE)
                editor.putString(getResString(R.string.settings_key_auth_salt), encodedSalt)
            }
        }

        editor.apply()

        PreferenceManager.setDefaultValues(context, R.xml.preferences, true)
    }



    fun registerPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        settings.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        settings.unregisterOnSharedPreferenceChangeListener(listener)
    }

    val tapToReveal: Boolean
        get() = tapSingle == Constants.TapMode.REVEAL || tapDouble == Constants.TapMode.REVEAL

    val tapToRevealTimeout: Int
        get() = getInt(R.string.settings_key_tap_to_reveal_timeout, R.integer.settings_default_tap_to_reveal_timeout)

    var authMethod: AuthMethod
        get() {
            val authString = getString(R.string.settings_key_auth, CredentialsPreference.DEFAULT_VALUE.name.lowercase(Locale.ENGLISH))
            return AuthMethod.valueOf(authString.uppercase(Locale.ENGLISH))
        }
        set(authMethod) {
            setString(R.string.settings_key_auth, authMethod.name.lowercase(Locale.ENGLISH))
        }

    fun removeAuthPasswordHash() {
        remove(R.string.settings_key_auth_password_hash)
    }
    fun removeAuthPINHash() {
        remove(R.string.settings_key_auth_pin_hash)
    }

    fun getOldCredentials(method: AuthMethod?): String {
        return if (method == AuthMethod.PASSWORD)
            getString(R.string.settings_key_auth_password_hash, "")
        else if (method == AuthMethod.PIN)
            getString(R.string.settings_key_auth_pin_hash, "")
        else
            ""
    }

    val authCredentials: String
        get() = getString(R.string.settings_key_auth_credentials, "")

    fun setAuthCredentials(plainPassword: String): ByteArray? {
        val credentials = generateAuthCredentials(plainPassword) ?: return null
        saveAuthCredentials(credentials, null)
        return credentials.key
    }

    /**
     * Credentials derived from a new password or PIN by [generateAuthCredentials]. [key] is the
     * database key they derive (password encryption); nothing is stored until [saveAuthCredentials].
     */
    class NewAuthCredentials internal constructor(
        @JvmField val key: ByteArray,
        internal val password: String,
        internal val iterations: Int
    )

    /** Derives credentials from [plainPassword] (PBKDF2, slow) without storing them; null on failure. */
    fun generateAuthCredentials(plainPassword: String): NewAuthCredentials? {
        try {
            val iterations = EncryptionHelper.generateRandomIterations()
            val credentials = EncryptionHelper.generatePBKDF2Credentials(plainPassword, salt, iterations)
            val password = Base64.encodeToString(credentials.password, Base64.URL_SAFE)

            return NewAuthCredentials(credentials.key!!, password, iterations)
        } catch (e: NoSuchAlgorithmException) {
            e.printStackTrace()
        } catch (e: InvalidKeySpecException) {
            e.printStackTrace()
        }

        return null
    }

    /**
     * Stores [credentials] (hash and iteration count) and, if given, [method] in a single
     * synchronous write, so they are never stored half-way. With password encryption the database
     * key derives from the credentials: call this only after the database has been re-encrypted
     * with [NewAuthCredentials.key]. Returns false if the write failed.
     */
    @SuppressLint("ApplySharedPref")
    fun saveAuthCredentials(credentials: NewAuthCredentials, method: AuthMethod?): Boolean {
        val editor = settings.edit()
            .putInt(getResString(R.string.settings_key_auth_iterations), credentials.iterations)
            .putString(getResString(R.string.settings_key_auth_credentials), credentials.password)

        if (method != null)
            editor.putString(getResString(R.string.settings_key_auth), method.name.lowercase(Locale.ROOT))

        return editor.commit()
    }

    /** What [saveAuthCredentials] overwrites, to be put back with [restoreValues]. */
    fun storedAuthCredentials(): Map<String, Any?> {
        return storedValues(R.string.settings_key_auth_iterations, R.string.settings_key_auth_credentials,
            R.string.settings_key_auth)
    }

    /**
     * The stored values of [keyIds] (null for a key that is not set), to be put back with
     * [restoreValues] when a write that depends on them has to be undone.
     */
    fun storedValues(vararg keyIds: Int): Map<String, Any?> {
        val all = settings.all
        return keyIds.map { getResString(it) }.associateWith { all[it] }
    }

    /**
     * Puts back values taken by [storedValues]. After a commit() that failed this is what matters:
     * commit() changes the preferences in memory even when writing them to disk fails, and the
     * file on disk still holds the old values.
     */
    @SuppressLint("ApplySharedPref")
    fun restoreValues(values: Map<String, Any?>): Boolean {
        val editor = settings.edit()
        for ((key, value) in values) {
            when (value) {
                null -> editor.remove(key)
                is String -> editor.putString(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Float -> editor.putFloat(key, value)
            }
        }
        return editor.commit()
    }

    /** Reading this creates and stores a new random salt when none is stored yet. */
    var salt: ByteArray
        get() {
            val storedSalt = getString(R.string.settings_key_auth_salt, "")

            if (storedSalt.isEmpty()) {
                val newSalt = EncryptionHelper.generateRandom(Constants.PBKDF2_SALT_LENGTH)
                this.salt = newSalt

                return newSalt
            } else {
                return Base64.decode(storedSalt, Base64.URL_SAFE)
            }
        }
        set(bytes) {
            val encodedSalt = Base64.encodeToString(bytes, Base64.URL_SAFE)
            setString(R.string.settings_key_auth_salt, encodedSalt)
        }

    var iterations: Int
        get() = getIntValue(R.string.settings_key_auth_iterations, Constants.PBKDF2_DEFAULT_ITERATIONS)
        set(value) {
            setInt(R.string.settings_key_auth_iterations, value)
        }

    var encryption: EncryptionType
        get() {
            val encType = getString(R.string.settings_key_encryption, R.string.settings_default_encryption)
            return EncryptionType.valueOf(encType.uppercase(Locale.ENGLISH))
        }
        set(encryptionType) {
            setEncryption(encryptionType.name.lowercase(Locale.ENGLISH))
        }

    fun setEncryption(encryption: String) {
        setString(R.string.settings_key_encryption, encryption)
    }

    val panicResponse: Set<String>?
        get() = settings.getStringSet(getResString(R.string.settings_key_panic), Collections.emptySet())

    val relockOnScreenOff: Boolean
        get() = getBoolean(R.string.settings_key_relock_screen_off, true)

    val relockOnBackground: Boolean
        get() = getBoolean(R.string.settings_key_relock_background, false)

    val blockAccessibility: Boolean
        get() = getBoolean(R.string.settings_key_block_accessibility, false)

    /** Which of the selectable launcher icons the user picked. */
    val launcherIcon: String
        get() = getString(R.string.settings_key_launcher_icon, R.string.settings_default_launcher_icon)

    val theme: Int
        get() {
            var theme = R.style.AppTheme_NoActionBar
            val themeMode = getString(R.string.settings_key_theme_mode, R.string.settings_default_theme_mode)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && themeMode == "auto") {
                val blackTheme = getBoolean(R.string.settings_key_theme_black_auto, false)

                when (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
                    //Dark Mode
                    Configuration.UI_MODE_NIGHT_YES -> {
                        if (blackTheme)
                            theme = R.style.AppTheme_Black_NoActionBar
                        else
                            theme = R.style.AppTheme_Dark_NoActionBar
                    }
                    //Light Mode / Undefined mode / Default mode
                    Configuration.UI_MODE_NIGHT_NO, Configuration.UI_MODE_NIGHT_UNDEFINED -> theme = R.style.AppTheme_NoActionBar
                    else -> theme = R.style.AppTheme_NoActionBar
                }
            } else {
                val themeName = getString(R.string.settings_key_theme, R.string.settings_default_theme)

                when (themeName) {
                    "light" -> theme = R.style.AppTheme_NoActionBar
                    "dark" -> theme = R.style.AppTheme_Dark_NoActionBar
                    "black" -> theme = R.style.AppTheme_Black_NoActionBar
                }
            }

            return theme
        }

    val labelSize: Int
        get() = getInt(R.string.settings_key_label_size, R.integer.settings_default_label_size)

    var firstTimeWarningShown: Boolean
        get() = getBoolean(R.string.settings_key_security_backup_warning, false)
        set(value) {
            setBoolean(R.string.settings_key_security_backup_warning, value)
        }

    var coachMarksShown: Boolean
        get() = getBoolean(R.string.settings_key_coach_marks_shown, false)
        set(value) {
            setBoolean(R.string.settings_key_coach_marks_shown, value)
        }

    var specialFeatures: Boolean
        get() = getBoolean(R.string.settings_key_special_features, false)
        set(value) {
            setBoolean(R.string.settings_key_special_features, value)
        }

    var sortMode: SortMode
        get() {
            val modeStr = getString(R.string.settings_key_sort_mode, SortMode.UNSORTED.toString())
            return SortMode.valueOf(modeStr)
        }
        set(value) {
            setString(R.string.settings_key_sort_mode, value.toString())
        }

    val searchValues: List<Constants.SearchIncludes>
        get() {
            val stringValues = settings.getStringSet(getResString(R.string.settings_key_search_includes), HashSet(context.resources.getStringArray(R.array.settings_defaults_search_includes).asList()))

            val values: MutableList<Constants.SearchIncludes> = ArrayList()

            for (value in stringValues!!) {
                values.add(Constants.SearchIncludes.valueOf(value.uppercase(Locale.ENGLISH)))
            }

            return values
        }

    val backupAsk: Boolean
        get() = getBoolean(R.string.settings_key_backup_ask, true)

    val backupPassword: String
        get() = getString(R.string.settings_key_backup_password, "")

    val backupPasswordEnc: String
        get() {
            val base64Password = getString(R.string.settings_key_backup_password_enc, "")
            val encPassword = Base64.decode(base64Password, Base64.URL_SAFE)

            var password = ""

            try {
                val key = KeyStoreHelper.loadOrGenerateAsymmetricKeyPair(context, Constants.KEYSTORE_ALIAS_PASSWORD)
                password = String(EncryptionHelper.decrypt(key!!.private, encPassword), StandardCharsets.UTF_8)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            return password
        }

    val backupBroadcasts: Set<String>?
        get() = settings.getStringSet(getResString(R.string.settings_key_backup_broadcasts), Collections.emptySet())

    val isPlainTextBackupBroadcastEnabled: Boolean
        get() = backupBroadcasts!!.contains("plain")

    val isEncryptedBackupBroadcastEnabled: Boolean
        get() = backupBroadcasts!!.contains("encrypted")

    val openPGPProvider: String
        get() = getString(R.string.settings_key_openpgp_provider, "")

    val openPGPEncryptionUserIDs: String
        get() = getString(R.string.settings_key_openpgp_key_encrypt, "")

    val openPGPSigningKey: Long
        get() = getLong(R.string.settings_key_openpgp_key_sign, 0)

    val openPGPVerify: Boolean
        get() = getBoolean(R.string.settings_key_openpgp_verify, false)

    var allTagsToggle: Boolean
        get() = getBoolean(R.string.settings_key_all_tags_toggle, true)
        set(value) {
            setBoolean(R.string.settings_key_all_tags_toggle, value)
        }

    var noTagsToggle: Boolean
        get() = getBoolean(R.string.settings_key_no_tags_toggle, true)
        set(value) {
            setBoolean(R.string.settings_key_no_tags_toggle, value)
        }

    fun getTagToggle(tag: String): Boolean {
        //The tag toggle holds tags that are unchecked in order to default to checked.
        val toggledTags = getStringSet(R.string.settings_key_tags_toggles, HashSet())
        return !toggledTags.contains(tag)
    }

    fun setTagToggle(tag: String, value: Boolean) {
        // getStringSet always returns a fresh copy, so the Java null check here was dead code.
        val toggledTags: MutableSet<String> = getStringSet(R.string.settings_key_tags_toggles, HashSet())

        if (value)
            toggledTags.remove(tag)
        else
            toggledTags.add(tag)
        setStringSet(R.string.settings_key_tags_toggles, toggledTags)
    }

    val thumbnailVisible: Boolean
        get() = thumbnailSize > 0

    val thumbnailSize: Int
        get() {
            try {
                val dimen = getString(R.string.settings_key_thumbnail_size, context.resources.getString(R.string.settings_default_thumbnail_size))
                return DimensionConverter.stringToDimensionPixelSize(dimen, context.resources.displayMetrics)
            } catch (e: Exception) {
                e.printStackTrace()
                return context.resources.getDimensionPixelSize(R.dimen.card_thumbnail_size)
            }
        }

    val tokenSplitGroupSize: Int
        get() {
            // the setting is of type "String", because ListPreference does not support integer arrays for its entryValues
            // A stored value that is not a number falls back to the default instead of crashing
            // every card that shows a token.
            return getString(R.string.settings_key_split_group_size, R.string.settings_default_split_group_size).toIntOrNull()
                ?: getResString(R.string.settings_default_split_group_size).toInt()
        }

    val tagFunctionality: Constants.TagFunctionality
        get() {
            val tagFunctionality = getString(R.string.settings_key_tag_functionality, R.string.settings_default_tag_functionality)
            return Constants.TagFunctionality.valueOf(tagFunctionality.uppercase(Locale.ENGLISH))
        }


    @Suppress("BooleanMethodIsAlwaysInverted")
    val screenshotsEnabled: Boolean
        get() = getBoolean(R.string.settings_key_enable_screenshot, false)

    @Suppress("BooleanMethodIsAlwaysInverted")
    var usedTokensDialogShown: Boolean
        get() = getBoolean(R.string.settings_key_last_used_dialog_shown, false)
        set(value) {
            setBoolean(R.string.settings_key_last_used_dialog_shown, value)
        }

    var androidBackupServiceEnabled: Boolean
        get() = getBoolean(R.string.settings_key_enable_android_backup_service, true)
        set(value) {
            setBoolean(R.string.settings_key_enable_android_backup_service, value)
        }

    @get:JvmName("getIsAppendingDateTimeToBackups")
    val isAppendingDateTimeToBackups: Boolean
        get() = getBoolean(R.string.settings_key_backup_append_date_time, true)

    val authInactivityDelay: Int
        get() = getIntValue(R.string.settings_key_auth_inactivity_delay, 0)

    val authInactivity: Boolean
        get() = getBoolean(R.string.settings_key_auth_inactivity, false)

    val isMinimizeAppOnCopyEnabled: Boolean
        get() = getBoolean(R.string.settings_key_minimize_on_copy, false)

    private val autoBackupEncryptedSetting: Constants.AutoBackup
        get() {
            val stringValue = getString(R.string.settings_key_auto_backup_password_enc, R.string.settings_default_auto_backup_password_enc)
            return Constants.AutoBackup.valueOf(stringValue.uppercase(Locale.ENGLISH))
        }

    fun setAutoBackupEncrypted(value: Constants.AutoBackup) {
        setString(R.string.settings_key_auto_backup_password_enc, value.name.lowercase(Locale.ENGLISH))
    }

    val autoBackupEncryptedPasswordsEnabled: Boolean
        get() = autoBackupEncryptedSetting != Constants.AutoBackup.OFF

    val autoBackupEncryptedFullEnabled: Boolean
        get() = autoBackupEncryptedSetting == Constants.AutoBackup.ALL_EDITS

    val isHighlightTokenOptionEnabled: Boolean
        get() = getBoolean(R.string.settings_key_label_highlight_token, true)

    val isHideGlobalTimeoutEnabled: Boolean
        get() = getBoolean(R.string.settings_key_hide_global_timeout, false)

    val isShowIndividualTimeoutsEnabled: Boolean
        get() = getBoolean(R.string.settings_key_show_individual_timeouts, false)

    val isFocusSearchOnStartEnabled: Boolean
        get() = getBoolean(R.string.settings_key_focus_search_on_start, false)

    val isHideIssuerEnabled: Boolean
        get() = getBoolean(R.string.settings_key_hide_issuer, false)

    val tapSingle: Constants.TapMode
        get() {
            val singleTap = getString(R.string.settings_key_tap_single, R.string.settings_default_tap_single)
            return Constants.TapMode.valueOf(singleTap.uppercase(Locale.ENGLISH))
        }

    val tapDouble: Constants.TapMode
        get() {
            val doubleTap = getString(R.string.settings_key_tap_double, R.string.settings_default_tap_double)
            return Constants.TapMode.valueOf(doubleTap.uppercase(Locale.ENGLISH))
        }

    var backupLocation: Uri
        get() = Uri.parse(getString(R.string.settings_key_backup_location, ""))
        set(uri) {
            setString(R.string.settings_key_backup_location, uri.toString())
        }

    val isBackupLocationSet: Boolean
        get() = getString(R.string.settings_key_backup_location, "").isNotEmpty()

    val blockAutofill: Boolean
        get() = getBoolean(R.string.settings_key_block_autofill, false)

    val autoUnlockAfterAutofill: Boolean
        get() = getBoolean(R.string.settings_key_auto_unlock_after_autofill, false)

    var defaultBackupType: Constants.BackupType
        get() {
            val defaultType = getString(R.string.settings_key_backup_default_type, Constants.BackupType.ENCRYPTED.name)
            return Constants.BackupType.valueOf(defaultType.uppercase(Locale.ENGLISH))
        }
        set(type) {
            setString(R.string.settings_key_backup_default_type, type.name.lowercase(Locale.ENGLISH))
        }

    val labelDisplay: Constants.LabelDisplay
        get() {
            val labelDisplay = getString(R.string.settings_key_label_display, R.string.settings_default_label_display)
            return Constants.LabelDisplay.valueOf(labelDisplay.uppercase(Locale.ENGLISH))
        }

    val showPrevToken: Boolean
        get() = getBoolean(R.string.settings_key_show_prev_token, false)
}
