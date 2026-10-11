@file:Suppress("PackageName", "DEPRECATION")
package com.gigabytedevelopersinc.app.cometOTP.Utilities

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.preference.PreferenceManager
import android.util.Base64
import com.gigabytedevelopersinc.app.cometOTP.Preferences.CredentialsPreference
import com.gigabytedevelopersinc.app.cometOTP.R
import com.gigabytedevelopersinc.app.cometOTP.Tasks.upgradeAuthCredentials
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
        // The plain password or PIN is removed only once the credentials derived from it are
        // stored; otherwise it stays and the migration is tried again next time.
        if (settings.contains(getResString(R.string.settings_key_auth_password))) {
            upgradeAuthCredentials(getString(R.string.settings_key_auth_password, ""),
                ::generateAuthCredentials, { saveAuthCredentials(it, null) }) {
                remove(R.string.settings_key_auth_password)
            }
        }

        if (settings.contains(getResString(R.string.settings_key_auth_pin))) {
            upgradeAuthCredentials(getString(R.string.settings_key_auth_pin, ""),
                ::generateAuthCredentials, { saveAuthCredentials(it, null) }) {
                remove(R.string.settings_key_auth_pin)
            }
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

    private fun setInt(keyId: Int, value: Int) {
        settings.edit()
            .putInt(getResString(keyId), value)
            .apply()
    }

    private fun setLong(keyId: Int, value: Long) {
        settings.edit()
            .putLong(getResString(keyId), value)
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
        val pinLength = getString(R.string.settings_key_auth_pin_length, "")
        val authFailures = this.authFailures
        val authLockout = getString(R.string.settings_key_auth_lockout, "")

        val warningShown = this.firstTimeWarningShown

        val editor = settings.edit()
        editor.clear()

        editor.putBoolean(getResString(R.string.settings_key_security_backup_warning), warningShown)

        if (keep_auth) {
            editor.putString(getResString(R.string.settings_key_auth), authMethod.toString().lowercase(Locale.ENGLISH))

            if (authCredentials.isNotEmpty()) {
                editor.putString(getResString(R.string.settings_key_auth_credentials), authCredentials)
                editor.putInt(getResString(R.string.settings_key_auth_iterations), authIterations)
                if (pinLength.isNotEmpty())
                    editor.putString(getResString(R.string.settings_key_auth_pin_length), pinLength)
                // Resetting the settings must not reset how long guessing the credentials is locked.
                if (authFailures > 0)
                    editor.putInt(getResString(R.string.settings_key_auth_failures), authFailures)
                if (authLockout.isNotEmpty())
                    editor.putString(getResString(R.string.settings_key_auth_lockout), authLockout)

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

    /**
     * How many digits the stored PIN has, so the keypad can unlock as soon as they are typed; null
     * when unknown (a PIN stored before its length was), or when the credentials are a password's.
     *
     * The length is stored for the credentials it was stored with, and is unknown again once they
     * change without it (say, by a version of the app that stored no length). A wrong length would
     * have the keypad check every PIN at that length and never accept the right one.
     */
    var pinLength: Int?
        get() = decodePinLength(getString(R.string.settings_key_auth_pin_length, ""), authCredentials)
        set(value) {
            if (value != null && value > 0 && authCredentials.isNotEmpty())
                setString(R.string.settings_key_auth_pin_length, encodePinLength(value, authCredentials))
            else
                remove(R.string.settings_key_auth_pin_length)
        }

    /** Wrong passwords or PINs in a row since the last unlock, see [UnlockThrottle]. */
    val authFailures: Int
        get() = getIntValue(R.string.settings_key_auth_failures, 0)

    /**
     * Counts an unlock attempt as wrong before it is checked, and locks the unlock screen as a
     * wrong attempt would: closing the app while the attempt is checked then gives no free try.
     * [resetAuthFailures] takes it back once the attempt turns out right. Returns the count.
     */
    @SuppressLint("ApplySharedPref")
    fun recordAuthAttempt(): Int {
        val failures = authFailures + 1
        val editor = settings.edit()
            .putInt(getResString(R.string.settings_key_auth_failures), failures)
        putAuthLockout(editor, UnlockThrottle.lockoutAfter(failures))
        editor.commit()
        return failures
    }

    /** Starts the lockout of the last attempt over from now, once it is known to be wrong. */
    @SuppressLint("ApplySharedPref")
    fun restartAuthLockout() {
        val editor = settings.edit()
        putAuthLockout(editor, UnlockThrottle.lockoutAfter(authFailures))
        editor.commit()
    }

    /** After a successful unlock. */
    @SuppressLint("ApplySharedPref")
    fun resetAuthFailures() {
        settings.edit()
            .remove(getResString(R.string.settings_key_auth_failures))
            .remove(getResString(R.string.settings_key_auth_lockout))
            .commit()
    }

    /** How much longer the unlock screen is locked after wrong attempts, in milliseconds. */
    fun authLockoutRemaining(): Long {
        val stored = decodeLockout(getString(R.string.settings_key_auth_lockout, "")) ?: return 0
        val now = SystemClock.elapsedRealtime()
        val current = lockoutAt(stored, bootCount(), now)
        if (current != stored)
            setString(R.string.settings_key_auth_lockout, encodeLockout(current))
        return remainingLockout(current, now)
    }

    private fun putAuthLockout(editor: SharedPreferences.Editor, duration: Long) {
        val key = getResString(R.string.settings_key_auth_lockout)
        if (duration > 0)
            editor.putString(key, encodeLockout(Lockout(bootCount(), SystemClock.elapsedRealtime() + duration, duration)))
        else
            editor.remove(key)
    }

    /** Which boot this is, telling elapsed-realtime clocks apart; -1 where the system does not say. */
    private fun bootCount(): Int {
        return android.provider.Settings.Global.getInt(context.contentResolver, android.provider.Settings.Global.BOOT_COUNT, -1)
    }

    /**
     * Credentials derived from a new password or PIN by [generateAuthCredentials]. [key] is the
     * database key they derive (password encryption); nothing is stored until [saveAuthCredentials].
     */
    class NewAuthCredentials internal constructor(
        val key: ByteArray,
        internal val password: String,
        internal val iterations: Int,
        /** How many characters the password or PIN has; 0 if unknown. */
        internal val length: Int = 0
    )

    /** Derives credentials from [plainPassword] (PBKDF2, slow) without storing them; null on failure. */
    fun generateAuthCredentials(plainPassword: String): NewAuthCredentials? {
        try {
            val iterations = EncryptionHelper.generateRandomIterations()
            val credentials = EncryptionHelper.generatePBKDF2Credentials(plainPassword, salt, iterations)
            val password = Base64.encodeToString(credentials.password, Base64.URL_SAFE)

            return NewAuthCredentials(credentials.key!!, password, iterations, plainPassword.length)
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

        // The length of a PIN goes with the PIN, so a new password never keeps an old PIN's length.
        val pinLength = pinLengthToStore(method ?: authMethod, credentials.length)
        val pinLengthKey = getResString(R.string.settings_key_auth_pin_length)
        if (pinLength != null)
            editor.putString(pinLengthKey, encodePinLength(pinLength, credentials.password))
        else
            editor.remove(pinLengthKey)

        return editor.commit()
    }

    /** What [saveAuthCredentials] overwrites, to be put back with [restoreValues]. */
    fun storedAuthCredentials(): Map<String, Any?> {
        return storedValues(R.string.settings_key_auth_iterations, R.string.settings_key_auth_credentials,
            R.string.settings_key_auth, R.string.settings_key_auth_pin_length)
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

    /** The theme to draw, once "auto" has followed the system's dark mode; see [resolveTheme]. */
    val themeChoice: ThemeChoice
        get() = resolveTheme(
            getString(R.string.settings_key_theme_mode, R.string.settings_default_theme_mode),
            getString(R.string.settings_key_theme, R.string.settings_default_theme),
            getBoolean(R.string.settings_key_theme_black_auto, false),
            (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES,
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)

    val theme: Int
        get() = AppTheme.styleFor(themeChoice)

    /**
     * What the theme picker shows as chosen. Choosing stores it as the two settings the theme has
     * always been kept in: the mode (auto or manual) and, for manual, the theme.
     */
    var themePick: ThemePick
        get() = themePickFor(
            getString(R.string.settings_key_theme_mode, R.string.settings_default_theme_mode),
            getString(R.string.settings_key_theme, R.string.settings_default_theme),
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        set(pick) {
            val editor = settings.edit()
            if (pick == ThemePick.AUTO) {
                editor.putString(getResString(R.string.settings_key_theme_mode), "auto")
            } else {
                editor.putString(getResString(R.string.settings_key_theme_mode), "manual")
                editor.putString(getResString(R.string.settings_key_theme), pick.value)
            }
            editor.apply()
        }

    /** Colours taken from the wallpaper (Android 12 and later), see [AppTheme.apply]. */
    val dynamicColors: Boolean
        get() = getBoolean(R.string.settings_key_dynamic_colors, false)

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

    /** Encrypts [password] with the KeyStore key the backup password is kept under and stores it. */
    fun setBackupPassword(password: String): Boolean {
        return try {
            val key = KeyStoreHelper.loadOrGenerateAsymmetricKeyPair(context, Constants.KEYSTORE_ALIAS_PASSWORD)
            val encPassword = EncryptionHelper.encrypt(key!!.public, password.toByteArray(StandardCharsets.UTF_8))
            setString(R.string.settings_key_backup_password_enc, Base64.encodeToString(encPassword, Base64.URL_SAFE))
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    val isBackupPasswordSet: Boolean
        get() = getString(R.string.settings_key_backup_password_enc, "").isNotEmpty()

    var backupDestination: Constants.BackupDestinationType
        get() {
            val value = getString(R.string.settings_key_backup_destination, Constants.BackupDestinationType.LOCAL.name)
            return try {
                Constants.BackupDestinationType.valueOf(value.uppercase(Locale.ENGLISH))
            } catch (e: IllegalArgumentException) {
                Constants.BackupDestinationType.LOCAL
            }
        }
        set(value) {
            setString(R.string.settings_key_backup_destination, value.name.lowercase(Locale.ENGLISH))
        }

    /** The Google account backups go to in Drive; empty when Drive is not connected. */
    var driveAccount: String
        get() = getString(R.string.settings_key_backup_drive_account, "")
        set(value) {
            setString(R.string.settings_key_backup_drive_account, value)
        }

    /** The name of the Drive folder the app creates for backups. */
    var driveFolderName: String
        get() = getString(R.string.settings_key_backup_drive_folder_name, "").ifBlank { DEFAULT_DRIVE_FOLDER_NAME }
        set(value) {
            setString(R.string.settings_key_backup_drive_folder_name, value)
        }

    /** The Drive id of that folder, once it has been found or created. */
    var driveFolderId: String
        get() = getString(R.string.settings_key_backup_drive_folder_id, "")
        set(value) {
            setString(R.string.settings_key_backup_drive_folder_id, value)
        }

    var scheduledBackupOnlyUnmetered: Boolean
        get() = getBoolean(R.string.settings_key_sched_backup_unmetered, false)
        set(value) {
            setBoolean(R.string.settings_key_sched_backup_unmetered, value)
        }

    var scheduledBackupEnabled: Boolean
        get() = getBoolean(R.string.settings_key_sched_backup_enabled, false)
        set(value) {
            setBoolean(R.string.settings_key_sched_backup_enabled, value)
        }

    /** Minutes between scheduled backups. */
    var scheduledBackupInterval: Int
        get() = getInt(R.string.settings_key_sched_backup_interval, R.integer.settings_default_sched_backup_interval)
        set(value) {
            setInt(R.string.settings_key_sched_backup_interval, value)
        }

    /** Preferred time of day for daily and weekly backups, in minutes after midnight. */
    var scheduledBackupTime: Int
        get() = getInt(R.string.settings_key_sched_backup_time, R.integer.settings_default_sched_backup_time)
        set(value) {
            setInt(R.string.settings_key_sched_backup_time, value)
        }

    /** How many automatic backups are kept; 1 overwrites a single file. */
    var scheduledBackupKeep: Int
        get() = getInt(R.string.settings_key_sched_backup_keep, R.integer.settings_default_sched_backup_keep).coerceAtLeast(1)
        set(value) {
            setInt(R.string.settings_key_sched_backup_keep, value)
        }

    var scheduledBackupOnlyCharging: Boolean
        get() = getBoolean(R.string.settings_key_sched_backup_charging, false)
        set(value) {
            setBoolean(R.string.settings_key_sched_backup_charging, value)
        }

    var scheduledBackupNotifySuccess: Boolean
        get() = getBoolean(R.string.settings_key_sched_backup_notify_success, false)
        set(value) {
            setBoolean(R.string.settings_key_sched_backup_notify_success, value)
        }

    /** The schedule the periodic work was last enqueued with, see BackupScheduler.reconcile. */
    var scheduledBackupSignature: String
        get() = getString(R.string.settings_key_sched_backup_signature, "")
        set(value) {
            setString(R.string.settings_key_sched_backup_signature, value)
        }

    var lastBackupSuccess: Long
        get() = getLong(R.string.settings_key_sched_backup_last_success, 0)
        set(value) {
            setLong(R.string.settings_key_sched_backup_last_success, value)
        }

    var lastBackupFile: String
        get() = getString(R.string.settings_key_sched_backup_last_file, "")
        set(value) {
            setString(R.string.settings_key_sched_backup_last_file, value)
        }

    var lastBackupAttempt: Long
        get() = getLong(R.string.settings_key_sched_backup_last_attempt, 0)
        set(value) {
            setLong(R.string.settings_key_sched_backup_last_attempt, value)
        }

    /** Why the last automatic backup failed, empty when it succeeded. */
    var lastBackupError: String
        get() = getString(R.string.settings_key_sched_backup_last_error, "")
        set(value) {
            setString(R.string.settings_key_sched_backup_last_error, value)
        }

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

    /** Whether a card shows the next token while its current one is about to expire. */
    val showNextToken: Boolean
        get() = getBoolean(R.string.settings_key_show_next_token, true)

    companion object {
        const val DEFAULT_DRIVE_FOLDER_NAME = "CometOTP Backups"
    }
}

/** The PIN length [Settings.saveAuthCredentials] stores for a credential of [length] characters. */
internal fun pinLengthToStore(method: AuthMethod, length: Int): Int? {
    return if (method == AuthMethod.PIN && length > 0) length else null
}

/**
 * A PIN length as stored for [credentials] (the stored hash): "length:hash code of the hash". The
 * hash is Base64 with a line break at the end, which comes back from the preferences file with
 * more whitespace around it, so it is compared without.
 */
internal fun encodePinLength(length: Int, credentials: String): String {
    return "$length:${credentials.trim().hashCode()}"
}

/** The length [encodePinLength] stored, or null if it was stored for other [credentials] or is not valid. */
internal fun decodePinLength(stored: String, credentials: String): Int? {
    if (credentials.isBlank())
        return null
    val parts = stored.split(':')
    if (parts.size != 2 || parts[1] != credentials.trim().hashCode().toString())
        return null
    return parts[0].toIntOrNull()?.takeIf { it > 0 }
}

/** A [Lockout] as stored: "boot:end:duration". */
internal fun encodeLockout(lockout: Lockout): String {
    return "${lockout.boot}:${lockout.endsAt}:${lockout.duration}"
}

/** The lockout [encodeLockout] stored, or null if there is none or it is not valid. */
internal fun decodeLockout(stored: String): Lockout? {
    val parts = stored.split(':')
    if (parts.size != 3)
        return null
    val boot = parts[0].toIntOrNull() ?: return null
    val endsAt = parts[1].toLongOrNull() ?: return null
    val duration = parts[2].toLongOrNull()?.takeIf { it > 0 } ?: return null
    return Lockout(boot, endsAt, duration)
}
