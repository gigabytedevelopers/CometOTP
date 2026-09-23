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
        BACKUP_FAILED, BACKUP_SUCCESS
    }

    enum class SearchIncludes {
        LABEL, ISSUER, TAGS
    }

    enum class AutoBackup {
        OFF, NEW_ENTRIES, ALL_EDITS
    }

    enum class TapMode {
        NOTHING, REVEAL, COPY, COPY_BACKGROUND, SEND_KEYSTROKES
    }

    enum class LabelDisplay {
        TRUNCATE, SCROLL, MULTILINE
    }

    // Intent extras
    const val EXTRA_AUTH_PASSWORD_KEY              = "password_key"
    const val EXTRA_AUTH_NEW_ENCRYPTION            = "new_encryption"
    const val EXTRA_AUTH_MESSAGE                   = "message"

    const val EXTRA_BACKUP_ENCRYPTION_KEY          = "encryption_key"

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
    internal const val PBKDF2_LENGTH              = 256      // 128-bit encryption key (Password-mode)
    internal const val PBKDF2_SALT_LENGTH         = 16

    // Authentication
    const val AUTH_MIN_PIN_LENGTH        = 4
    const val AUTH_MIN_PASSWORD_LENGTH   = 6

    // KeyStore
    const val KEYSTORE_ALIAS_PASSWORD  = "password"
    const val KEYSTORE_ALIAS_WRAPPING  = "settings"

    // Database files
    const val FILENAME_ENCRYPTED_KEY   = "otp.key"
    const val FILENAME_DATABASE        = "secrets.dat"
    const val FILENAME_DATABASE_BACKUP = "secrets.dat.bck"

    // Backup files
    const val BACKUP_FILENAME_PLAIN    = "otp_accounts.json"
    const val BACKUP_FILENAME_CRYPT    = "otp_accounts.json.aes"
    const val BACKUP_FILENAME_PGP      = "otp_accounts.json.gpg"

    const val BACKUP_FILENAME_PLAIN_FORMAT    = "otp_accounts_%s.json"
    const val BACKUP_FILENAME_CRYPT_FORMAT    = "otp_accounts_%s.json.aes"
    const val BACKUP_FILENAME_PGP_FORMAT      = "otp_accounts_%s.json.gpg"

    const val BACKUP_MIMETYPE_PLAIN    = "application/json"
    const val BACKUP_MIMETYPE_CRYPT    = "binary/aes"
    const val BACKUP_MIMETYPE_PGP      = "application/pgp-encrypted"
}
