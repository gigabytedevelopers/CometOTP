@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Receivers

import android.content.Context
import android.content.Intent
import com.gigabytedevelopersinc.app.cometOTP.R
import com.gigabytedevelopersinc.app.cometOTP.Utilities.BackupHelper
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Constants
import com.gigabytedevelopersinc.app.cometOTP.Utilities.DatabaseHelper
import com.gigabytedevelopersinc.app.cometOTP.Utilities.KeyStoreHelper
import com.gigabytedevelopersinc.app.cometOTP.Utilities.NotificationHelper
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Settings
import com.gigabytedevelopersinc.app.cometOTP.Utilities.StorageAccessHelper
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Tools
import javax.crypto.SecretKey

// Use the following command to test in the dev version:
//   adb shell am broadcast -a com.gigabytedevelopersinc.app.cometOTP.broadcast.PLAIN_TEXT_BACKUP com.gigabytedevelopersinc.app.cometOTP.dev
class PlainTextBackupBroadcastReceiver : BackupBroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val settings = Settings(context)

        if (settings.isPlainTextBackupBroadcastEnabled) {
            if (!canSaveBackup(context))
                return

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

                val backupFile = BackupHelper.backupFile(context, settings.backupLocation, Constants.BackupType.PLAIN_TEXT)
                val file = backupFile.file

                if (file == null) {
                    NotificationHelper.notify(context, Constants.NotificationChannel.BACKUP_FAILED, R.string.backup_receiver_title_backup_failed, backupFile.errorMessage)
                    return
                }

                if (StorageAccessHelper.saveFile(context, file.uri, DatabaseHelper.entriesToString(entries))) {
                    NotificationHelper.notify(context, Constants.NotificationChannel.BACKUP_SUCCESS, R.string.backup_receiver_title_backup_success, file.name)
                } else {
                    NotificationHelper.notify(context, Constants.NotificationChannel.BACKUP_FAILED, R.string.backup_receiver_title_backup_failed, R.string.backup_toast_export_failed)
                }
            } else {
                NotificationHelper.notify(context, Constants.NotificationChannel.BACKUP_FAILED, R.string.backup_receiver_title_backup_failed, R.string.backup_toast_storage_not_accessible)
            }
        } else {
            NotificationHelper.notify(context, Constants.NotificationChannel.BACKUP_FAILED, R.string.backup_receiver_title_backup_failed, R.string.backup_receiver_plain_disabled)
        }
    }
}
