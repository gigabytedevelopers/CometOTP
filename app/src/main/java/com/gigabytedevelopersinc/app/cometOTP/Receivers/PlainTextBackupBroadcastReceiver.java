package com.gigabytedevelopersinc.app.cometOTP.Receivers;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import com.gigabytedevelopersinc.app.cometOTP.Database.Entry;
import com.gigabytedevelopersinc.app.cometOTP.R;
import com.gigabytedevelopersinc.app.cometOTP.Utilities.BackupHelper;
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Constants;
import com.gigabytedevelopersinc.app.cometOTP.Utilities.DatabaseHelper;
import com.gigabytedevelopersinc.app.cometOTP.Utilities.KeyStoreHelper;
import com.gigabytedevelopersinc.app.cometOTP.Utilities.NotificationHelper;
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Settings;
import com.gigabytedevelopersinc.app.cometOTP.Utilities.StorageAccessHelper;
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Tools;

import java.util.ArrayList;

import javax.crypto.SecretKey;

// Use the following command to test in the dev version:
//   adb shell am broadcast -a com.gigabytedevelopersinc.app.cometOTP.broadcast.PLAIN_TEXT_BACKUP com.gigabytedevelopersinc.app.cometOTP.dev
public class PlainTextBackupBroadcastReceiver extends BackupBroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Settings settings = new Settings(context);

        if (settings.isPlainTextBackupBroadcastEnabled()) {
            if (!canSaveBackup(context))
                return;

            SecretKey encryptionKey = null;

            if (settings.getEncryption() == Constants.EncryptionType.KEYSTORE) {
                encryptionKey = KeyStoreHelper.loadEncryptionKeyFromKeyStore(context, false);
            } else {
                NotificationHelper.notify(context, Constants.NotificationChannel.BACKUP_FAILED, R.string.backup_receiver_title_backup_failed, R.string.backup_receiver_custom_encryption_failed);
                return;
            }

            if (Tools.isExternalStorageWritable()) {
                BackupHelper.BackupFile backupFile = BackupHelper.backupFile(context, settings.getBackupLocation(), Constants.BackupType.PLAIN_TEXT);

                if (backupFile.file == null) {
                    NotificationHelper.notify(context, Constants.NotificationChannel.BACKUP_FAILED, R.string.backup_receiver_title_backup_failed, backupFile.errorMessage);
                    return;
                }

                ArrayList<Entry> entries = DatabaseHelper.loadDatabase(context, encryptionKey);

                if (StorageAccessHelper.saveFile(context, backupFile.file.getUri(), DatabaseHelper.entriesToString(entries))) {
                    NotificationHelper.notify(context, Constants.NotificationChannel.BACKUP_SUCCESS, R.string.backup_receiver_title_backup_success, backupFile.file.getName());
                } else {
                    NotificationHelper.notify(context, Constants.NotificationChannel.BACKUP_FAILED, R.string.backup_receiver_title_backup_failed, R.string.backup_toast_export_failed);
                }
            } else {
                NotificationHelper.notify(context, Constants.NotificationChannel.BACKUP_FAILED, R.string.backup_receiver_title_backup_failed, R.string.backup_toast_storage_not_accessible);
            }
        } else {
            NotificationHelper.notify(context, Constants.NotificationChannel.BACKUP_FAILED, R.string.backup_receiver_title_backup_failed, R.string.backup_receiver_plain_disabled);
        }
    }
}
