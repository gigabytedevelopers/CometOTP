@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Utilities

import android.util.DisplayMetrics
import android.view.inputmethod.EditorInfo
import com.gigabytedevelopersinc.app.cometOTP.Database.Entry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * Names and values that are written to preferences, files, intents or the manifest, or read back
 * from them with valueOf(). Changing any of them breaks existing installs, so they are pinned.
 */
class PersistedNamesTest {

    private fun <E : Enum<E>> names(values: Array<E>) = values.map { it.name }

    @Test
    fun enumConstants() {
        assertEquals(listOf("FIRST_TIME", "FIRST_TIME_VERSION", "NORMAL"), names(Constants.AppStart.values()))
        assertEquals(listOf("NONE", "PASSWORD", "PIN", "DEVICE"), names(Constants.AuthMethod.values()))
        assertEquals(listOf("KEYSTORE", "PASSWORD"), names(Constants.EncryptionType.values()))
        assertEquals(listOf("UNSORTED", "ISSUER", "LABEL", "LAST_USED", "MOST_USED"), names(Constants.SortMode.values()))
        assertEquals(listOf("PLAIN_TEXT", "ENCRYPTED", "OPEN_PGP", "UNAVAILABLE"), names(Constants.BackupType.values()))
        assertEquals(listOf("OR", "AND", "SINGLE"), names(Constants.TagFunctionality.values()))
        assertEquals(listOf("BACKUP_FAILED", "BACKUP_SUCCESS"), names(Constants.NotificationChannel.values()))
        assertEquals(listOf("LABEL", "ISSUER", "TAGS"), names(Constants.SearchIncludes.values()))
        assertEquals(listOf("OFF", "NEW_ENTRIES", "ALL_EDITS"), names(Constants.AutoBackup.values()))
        assertEquals(listOf("NOTHING", "REVEAL", "COPY", "COPY_BACKGROUND", "SEND_KEYSTROKES"), names(Constants.TapMode.values()))
        assertEquals(listOf("TRUNCATE", "SCROLL", "MULTILINE"), names(Constants.LabelDisplay.values()))
        assertEquals(listOf("TOTP", "HOTP", "MOTP", "STEAM"), names(Entry.OTPType.values()))
        assertEquals(listOf("SUCCESS", "BACKUP_FAILED", "NO_KEY", "SAVE_FAILED"), names(EncryptionChangeHelper.Status.values()))
    }

    @Test
    fun stringConstants() {
        assertEquals("password_key", Constants.EXTRA_AUTH_PASSWORD_KEY)
        assertEquals("new_encryption", Constants.EXTRA_AUTH_NEW_ENCRYPTION)
        assertEquals("message", Constants.EXTRA_AUTH_MESSAGE)
        assertEquals("encryption_key", Constants.EXTRA_BACKUP_ENCRYPTION_KEY)
        assertEquals("setup_finished", Constants.EXTRA_INTRO_FINISHED)
        assertEquals("encryption_changed", Constants.EXTRA_SETTINGS_ENCRYPTION_CHANGED)
        assertEquals("encryption_key", Constants.EXTRA_SETTINGS_ENCRYPTION_KEY)
        assertEquals(4, Constants.AUTH_MIN_PIN_LENGTH)
        assertEquals(6, Constants.AUTH_MIN_PASSWORD_LENGTH)
        assertEquals("password", Constants.KEYSTORE_ALIAS_PASSWORD)
        assertEquals("settings", Constants.KEYSTORE_ALIAS_WRAPPING)
        assertEquals("otp.key", Constants.FILENAME_ENCRYPTED_KEY)
        assertEquals("secrets.dat", Constants.FILENAME_DATABASE)
        assertEquals("secrets.dat.bck", Constants.FILENAME_DATABASE_BACKUP)
        assertEquals("otp_accounts.json", Constants.BACKUP_FILENAME_PLAIN)
        assertEquals("otp_accounts.json.aes", Constants.BACKUP_FILENAME_CRYPT)
        assertEquals("otp_accounts.json.gpg", Constants.BACKUP_FILENAME_PGP)
        assertEquals("otp_accounts_%s.json", Constants.BACKUP_FILENAME_PLAIN_FORMAT)
        assertEquals("otp_accounts_%s.json.aes", Constants.BACKUP_FILENAME_CRYPT_FORMAT)
        assertEquals("otp_accounts_%s.json.gpg", Constants.BACKUP_FILENAME_PGP_FORMAT)
        assertEquals("application/json", Constants.BACKUP_MIMETYPE_PLAIN)
        assertEquals("binary/aes", Constants.BACKUP_MIMETYPE_CRYPT)
        assertEquals("application/pgp-encrypted", Constants.BACKUP_MIMETYPE_PGP)
        assertEquals(1, Entry.COLOR_RED)
    }

    @Test
    fun backupAgentAndLauncherIcons() {
        assertEquals("prefs", BackupAgent.PREFS_BACKUP_KEY)
        assertEquals("files", BackupAgent.FILES_BACKUP_KEY)

        assertEquals("blue", LauncherIcon.BLUE)
        assertEquals("mono", LauncherIcon.MONO)
        assertEquals("white", LauncherIcon.WHITE)
        assertEquals("classic", LauncherIcon.CLASSIC)
        assertEquals("blue", LauncherIcon.DEFAULT)
    }

    @Test
    fun backupFileDefaults() {
        val file = BackupHelper.BackupFile()
        assertNull(file.file)
        assertEquals(0, file.errorMessage)
    }

    @Test
    fun dimensionUnits() {
        assertEquals(
            mapOf("px" to 0, "dip" to 1, "dp" to 1, "sp" to 2, "pt" to 3, "in" to 4, "mm" to 5),
            DimensionConverter.dimensionConstantLookup
        )
        // Parsing fails before any Android call is made.
        assertThrows(NumberFormatException::class.java) { DimensionConverter.stringToDimensionPixelSize("abc", DisplayMetrics()) }
        assertThrows(NumberFormatException::class.java) { DimensionConverter.stringToDimension("12 furlongs", DisplayMetrics()) }
    }

    /**
     * Units are lower-cased locale-independently: in Turkish "DIP" used to become "dıp" (dotless
     * i) and was rejected. A recognised unit gets past the lookup to the Android conversion call,
     * which is a stub on the JVM, so anything but NumberFormatException means it was recognised.
     */
    @Test
    fun dimensionUnitsAreRecognisedInATurkishLocale() {
        val saved = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            val error = assertThrows(RuntimeException::class.java) { DimensionConverter.stringToDimension("12 DIP", DisplayMetrics()) }
            assertFalse(error.toString(), error is NumberFormatException)
        } finally {
            Locale.setDefault(saved)
        }
    }

    @Test
    fun editorActionWithoutAKeyEvent() {
        assertTrue(EditorActionHelper.isActionDoneOrKeyboardEnter(EditorInfo.IME_ACTION_DONE, null))
        assertFalse(EditorActionHelper.isActionDoneOrKeyboardEnter(EditorInfo.IME_ACTION_NEXT, null))
    }

    @Test
    fun tokenGrouping() {
        assertEquals("123 456", Tools.formatToken("123456", 3))
        assertEquals(null, Tools.formatToken(null, 3))
        assertEquals("abc", Tools.formatToken("abc", 0))
    }
}
