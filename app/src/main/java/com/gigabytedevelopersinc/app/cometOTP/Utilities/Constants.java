package com.gigabytedevelopersinc.app.cometOTP.Utilities;

import android.os.Environment;

import java.io.File;

public class Constants {
    // Enums
    public enum AppStart {
        FIRST_TIME, FIRST_TIME_VERSION, NORMAL
    }

    public enum AuthMethod {
        NONE, PASSWORD, PIN, DEVICE
    }

    public enum EncryptionType {
        KEYSTORE, PASSWORD
    }

    public enum SortMode {
        UNSORTED, ISSUER, LABEL, LAST_USED, MOST_USED
    }

    public enum BackupType {
        PLAIN_TEXT, ENCRYPTED, OPEN_PGP, UNAVAILABLE
    }

    public enum TagFunctionality {
        OR, AND, SINGLE
    }

    public enum NotificationChannel {
        BACKUP_FAILED, BACKUP_SUCCESS
    }

    public enum SearchIncludes {
        LABEL, ISSUER, TAGS
    }

    public enum AutoBackup {
        OFF, NEW_ENTRIES, ALL_EDITS
    }

    public enum TapMode {
        NOTHING, REVEAL, COPY, COPY_BACKGROUND
    }

    // Intents (Format: A0x with A = parent Activity, x = number of the intent)
    public final static int INTENT_MAIN_AUTHENTICATE            = 100;
    public final static int INTENT_MAIN_SETTINGS                = 101;
    public final static int INTENT_MAIN_BACKUP                  = 102;
    public final static int INTENT_MAIN_INTRO                   = 103;
    public final static int INTENT_MAIN_LICENSES                = 104;
    public final static int INTENT_MAIN_CHANGELOG               = 105;
    public final static int INTENT_MAIN_WHATSNEW                = 106;
    public final static int INTENT_MAIN_PRIVACYPOLICY           = 107;
    public final static int INTENT_MAIN_CLEARCACHE              = 108;
    public final static int INTENT_MAIN_QR_OPEN_IMAGE           = 109;

    public final static int INTENT_BACKUP_OPEN_DOCUMENT_PLAIN   = 200;
    public final static int INTENT_BACKUP_SAVE_DOCUMENT_PLAIN   = 201;
    public final static int INTENT_BACKUP_OPEN_DOCUMENT_CRYPT   = 202;
    public final static int INTENT_BACKUP_SAVE_DOCUMENT_CRYPT   = 203;
    public final static int INTENT_BACKUP_OPEN_DOCUMENT_PGP     = 204;
    public final static int INTENT_BACKUP_SAVE_DOCUMENT_PGP     = 205;
    public final static int INTENT_BACKUP_ENCRYPT_PGP           = 206;
    public final static int INTENT_BACKUP_DECRYPT_PGP           = 207;
    public final static int INTENT_BACKUP_OPEN_DOCUMENT_CRYPT_OLD   = 208;

    public static final int INTENT_SETTINGS_AUTHENTICATE        = 300;
    public static final int INTENT_SETTINGS_BACKUP_LOCATION     = 301;

    // Intent extras
    public final static String EXTRA_AUTH_PASSWORD_KEY              = "password_key";
    public final static String EXTRA_AUTH_NEW_ENCRYPTION            = "new_encryption";
    public final static String EXTRA_AUTH_MESSAGE                   = "message";

    public final static String EXTRA_BACKUP_ENCRYPTION_KEY          = "encryption_key";

    public final static String EXTRA_SETTINGS_ENCRYPTION_CHANGED    = "encryption_changed";
    public final static String EXTRA_SETTINGS_ENCRYPTION_KEY        = "encryption_key";

    // Encryption algorithms and definitions
    final static String ALGORITHM_SYMMETRIC     = "AES/GCM/NoPadding";
    final static String ALGORITHM_ASYMMETRIC    = "RSA/ECB/PKCS1Padding";

    final static int ENCRYPTION_KEY_LENGTH  = 16;           // 128-bit encryption key (KeyStore-mode)
    public final static int ENCRYPTION_IV_LENGTH   = 12;

    public final static int INT_LENGTH = 4;

    final static int PBKDF2_MIN_ITERATIONS      = 140000;
    final static int PBKDF2_MAX_ITERATIONS      = 160000;
    final static int PBKDF2_DEFAULT_ITERATIONS  = 150000;
    final static int PBKDF2_LENGTH              = 256;      // 128-bit encryption key (Password-mode)
    final static int PBKDF2_SALT_LENGTH         = 16;

    // Authentication
    public final static int AUTH_MIN_PIN_LENGTH        = 4;
    public final static int AUTH_MIN_PASSWORD_LENGTH   = 6;

    // KeyStore
    public final static String KEYSTORE_ALIAS_PASSWORD  = "password";
    public final static String KEYSTORE_ALIAS_WRAPPING  = "settings";

    // Database files
    public final static String FILENAME_ENCRYPTED_KEY   = "otp.key";
    public final static String FILENAME_DATABASE        = "secrets.dat";
    public final static String FILENAME_DATABASE_BACKUP = "secrets.dat.bck";

    // Backup files
    public final static String BACKUP_FOLDER            = Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + "CometOTP";

    public final static String BACKUP_FILENAME_PLAIN    = "otp_accounts.json";
    public final static String BACKUP_FILENAME_CRYPT    = "otp_accounts.json.aes";
    public final static String BACKUP_FILENAME_PGP      = "otp_accounts.json.gpg";

    public final static String BACKUP_FILENAME_PLAIN_FORMAT    = "otp_accounts_%s.json";
    public final static String BACKUP_FILENAME_CRYPT_FORMAT    = "otp_accounts_%s.json.aes";
    public final static String BACKUP_FILENAME_PGP_FORMAT      = "otp_accounts_%s.json.gpg";

    public final static String BACKUP_MIMETYPE_PLAIN    = "application/json";
    public final static String BACKUP_MIMETYPE_CRYPT    = "binary/aes";
    public final static String BACKUP_MIMETYPE_PGP      = "application/pgp-encrypted";
}
