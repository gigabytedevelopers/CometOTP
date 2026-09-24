@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Receivers

import android.content.Context
import android.content.Intent
import com.gigabytedevelopersinc.app.cometOTP.R
import com.gigabytedevelopersinc.app.cometOTP.Utilities.BackupHelper
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Constants
import com.gigabytedevelopersinc.app.cometOTP.Utilities.DatabaseHelper
import com.gigabytedevelopersinc.app.cometOTP.Utilities.EncryptionHelper
import com.gigabytedevelopersinc.app.cometOTP.Utilities.KeyStoreHelper
import com.gigabytedevelopersinc.app.cometOTP.Utilities.NotificationHelper
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Settings
import com.gigabytedevelopersinc.app.cometOTP.Utilities.StorageAccessHelper
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Tools
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import javax.crypto.SecretKey

// Use the following command to test in the dev version:
//   adb shell am broadcast -a com.gigabytedevelopersinc.app.cometOTP.broadcast.ENCRYPTED_BACKUP com.gigabytedevelopersinc.app.cometOTP.dev
class EncryptedBackupBroadcastReceiver : BackupBroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val settings = Settings(context)

        if (settings.isEncryptedBackupBroadcastEnabled) {
            if (!canSaveBackup(context))
                return

            val password = settings.backupPasswordEnc

            if (password.isEmpty()) {
                NotificationHelper.notify(context, Constants.NotificationChannel.BACKUP_FAILED, R.string.backup_receiver_title_backup_failed, R.string.backup_toast_crypt_password_not_set)
                return
            }

            val encryptionKey: SecretKey?

            if (settings.encryption == Constants.EncryptionType.KEYSTORE) {
                encryptionKey = KeyStoreHelper.loadEncryptionKeyFromKeyStore(context, false)
            } else {
                NotificationHelper.notify(context, Constants.NotificationChannel.BACKUP_FAILED, R.string.backup_receiver_title_backup_failed, R.string.backup_receiver_custom_encryption_failed)
                return
            }

            if (Tools.isExternalStorageWritable()) {
                // Read before the backup file is looked up or created: an unreadable database
                // must not overwrite the existing backup with nothing.
                val entries = DatabaseHelper.loadDatabase(context, encryptionKey)
                if (entries == null) {
                    NotificationHelper.notify(context, Constants.NotificationChannel.BACKUP_FAILED, R.string.backup_receiver_title_backup_failed, R.string.backup_toast_export_failed)
                    return
                }

                val cryptBackupFile = BackupHelper.backupFile(context, settings.backupLocation, Constants.BackupType.ENCRYPTED)
                val file = cryptBackupFile.file

                if (file == null) {
                    NotificationHelper.notify(context, Constants.NotificationChannel.BACKUP_FAILED, R.string.backup_receiver_title_backup_failed, cryptBackupFile.errorMessage)
                    return
                }

                val plain = DatabaseHelper.entriesToString(entries)

                try {
                    val iter = EncryptionHelper.generateRandomIterations()
                    val salt = EncryptionHelper.generateRandom(Constants.ENCRYPTION_IV_LENGTH)

                    val key = EncryptionHelper.generateSymmetricKeyPBKDF2(password, iter, salt)
                    val encrypted = EncryptionHelper.encrypt(key, plain.toByteArray(StandardCharsets.UTF_8))

                    val iterBytes = ByteBuffer.allocate(Constants.INT_LENGTH).putInt(iter).array()
                    val data = ByteArray(Constants.INT_LENGTH + Constants.ENCRYPTION_IV_LENGTH + encrypted.size)

                    System.arraycopy(iterBytes, 0, data, 0, Constants.INT_LENGTH)
                    System.arraycopy(salt, 0, data, Constants.INT_LENGTH, Constants.ENCRYPTION_IV_LENGTH)
                    System.arraycopy(encrypted, 0, data, Constants.INT_LENGTH + Constants.ENCRYPTION_IV_LENGTH, encrypted.size)

                    StorageAccessHelper.saveFile(context, file.uri, data)

                    NotificationHelper.notify(context, Constants.NotificationChannel.BACKUP_SUCCESS, R.string.backup_receiver_title_backup_success, file.name)
                } catch (e: Exception) {
                    e.printStackTrace()
                    NotificationHelper.notify(context, Constants.NotificationChannel.BACKUP_FAILED, R.string.backup_receiver_title_backup_failed, R.string.backup_toast_export_failed)
                }
            } else {
                NotificationHelper.notify(context, Constants.NotificationChannel.BACKUP_FAILED, R.string.backup_receiver_title_backup_failed, R.string.backup_toast_storage_not_accessible)
            }
        } else {
            NotificationHelper.notify(context, Constants.NotificationChannel.BACKUP_FAILED, R.string.backup_receiver_title_backup_failed, R.string.backup_receiver_encrypted_disabled)
        }
    }
}
