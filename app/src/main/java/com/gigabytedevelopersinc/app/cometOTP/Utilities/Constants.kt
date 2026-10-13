@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Utilities

object Constants {
    // Enums
    enum class AppStart {
        FIRST_TIME, FIRST_TIME_VERSION, NORMAL
    }

    enum class AuthMethod {
        NONE, PASSWORD, PIN, DEVICE
    }

    enum class EncryptionType {
        KEYSTORE, PASSWORD
    }

    enum class SortMode {
        UNSORTED, ISSUER, LABEL, LAST_USED, MOST_USED
    }

    enum class BackupType {
        PLAIN_TEXT, ENCRYPTED, OPEN_PGP, UNAVAILABLE
    }

    enum class TagFunctionality {
        OR, AND, SINGLE
    }

    enum class NotificationChannel {
        BACKUP_FAILED, BACKUP_SUCCESS, BACKUP_REMINDER
    }

    enum class SearchIncludes {
        LABEL, ISSUER, TAGS
    }

    enum class AutoBackup {
        OFF, NEW_ENTRIES, ALL_EDITS
    }

    /** Where scheduled backups and Auto Sync write to. */
    enum class BackupDestinationType {
        LOCAL, DRIVE
    }

    enum class TapMode {
        NOTHING, REVEAL, COPY, COPY_BACKGROUND, SEND_KEYSTROKES;

        /** Whether a tap set to this copies the token (and maybe goes to the background). */
        val copies: Boolean
            get() = this == COPY || this == COPY_BACKGROUND

        /**
         * Whether a double tap may do this while a single tap does [single]. A double tap starts
         * with a single tap, so it cannot do the same thing: only Nothing can be both.
         */
        fun allowedAsDoubleTap(single: TapMode): Boolean = this == NOTHING || this != single
    }

    enum class LabelDisplay {
        TRUNCATE, SCROLL, MULTILINE
    }

    // Intent extras
    const val EXTRA_AUTH_PASSWORD_KEY              = "password_key"
    const val EXTRA_AUTH_NEW_ENCRYPTION            = "new_encryption"
    const val EXTRA_AUTH_MESSAGE                   = "message"

    const val EXTRA_BACKUP_ENCRYPTION_KEY          = "encryption_key"

    // Explicit intent to MainActivity (from a backup notification): open the scheduled backups
    // screen once the app has been unlocked.
    const val INTENT_OPEN_SCHEDULED_BACKUP         = "com.gigabytedevelopersinc.app.cometOTP.intent.OPEN_SCHEDULED_BACKUP"

    const val EXTRA_INTRO_FINISHED                 = "setup_finished"

    const val EXTRA_SETTINGS_ENCRYPTION_CHANGED    = "encryption_changed"
    const val EXTRA_SETTINGS_ENCRYPTION_KEY        = "encryption_key"

    // Encryption algorithms and definitions
    internal const val ALGORITHM_SYMMETRIC     = "AES/GCM/NoPadding"
    internal const val ALGORITHM_ASYMMETRIC    = "RSA/ECB/PKCS1Padding"

    internal const val ENCRYPTION_KEY_LENGTH  = 16           // 128-bit encryption key (KeyStore-mode)
    const val ENCRYPTION_IV_LENGTH   = 12

    const val INT_LENGTH = 4

    internal const val PBKDF2_MIN_ITERATIONS      = 140000
    internal const val PBKDF2_MAX_ITERATIONS      = 160000
    internal const val PBKDF2_DEFAULT_ITERATIONS  = 150000
    // 256-bit PBKDF2 output. Password-mode splits it into a 128-bit encryption key and a 128-bit
    // password hash (generatePBKDF2Credentials); encrypted backups use all of it as an AES-256 key.
    internal const val PBKDF2_LENGTH              = 256
    internal const val PBKDF2_SALT_LENGTH         = 16

    // Authentication
    const val AUTH_MIN_PIN_LENGTH        = 4
    // The longest PIN the keypad lets you set up (Android's own lock screen allows 16 too).
    const val AUTH_MAX_PIN_LENGTH        = 16
    // Unlocking a PIN of unknown length (set before its length was stored) accepts this many digits.
    const val AUTH_MAX_ENTERED_PIN_LENGTH = 64
    const val AUTH_MIN_PASSWORD_LENGTH   = 6

    // KeyStore
    const val KEYSTORE_ALIAS_PASSWORD  = "password"
    const val KEYSTORE_ALIAS_WRAPPING  = "settings"

    // Database files
    const val FILENAME_ENCRYPTED_KEY   = "otp.key"
    const val FILENAME_DATABASE        = "secrets.dat"
    const val FILENAME_DATABASE_BACKUP = "secrets.dat.bck"
    // Removed accounts (RecycleBin), encrypted like the database and wiped with it
    const val FILENAME_REMOVED         = "removed.dat"
    // Paired browsers (BrowserStore), encrypted like the database, wiped with it, never backed up
    const val FILENAME_BROWSERS        = "browsers.dat"

    // Backup files
    const val BACKUP_FILENAME_PLAIN    = "otp_accounts.json"
    const val BACKUP_FILENAME_CRYPT    = "otp_accounts.json.aes"
    const val BACKUP_FILENAME_PGP      = "otp_accounts.json.gpg"

    const val BACKUP_FILENAME_PLAIN_FORMAT    = "otp_accounts_%s.json"
    const val BACKUP_FILENAME_CRYPT_FORMAT    = "otp_accounts_%s.json.aes"
    const val BACKUP_FILENAME_PGP_FORMAT      = "otp_accounts_%s.json.gpg"

    // Scheduled backups and Auto Sync. Old ones are pruned by this name only, so manual backups
    // (named above) are never deleted.
    const val BACKUP_FILENAME_AUTO           = "otp_accounts_auto.json.aes"
    const val BACKUP_FILENAME_AUTO_FORMAT    = "otp_accounts_auto_%s.json.aes"

    const val BACKUP_MIMETYPE_PLAIN    = "application/json"
    const val BACKUP_MIMETYPE_CRYPT    = "binary/aes"
    const val BACKUP_MIMETYPE_PGP      = "application/pgp-encrypted"
}
