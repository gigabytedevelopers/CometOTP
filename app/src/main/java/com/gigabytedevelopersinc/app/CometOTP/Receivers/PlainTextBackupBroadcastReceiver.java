package com.gigabytedevelopersinc.app.CometOTP.Receivers;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import com.gigabytedevelopersinc.app.CometOTP.Database.Entry;
import com.gigabytedevelopersinc.app.CometOTP.R;
import com.gigabytedevelopersinc.app.CometOTP.Utilities.Constants;
import com.gigabytedevelopersinc.app.CometOTP.Utilities.DatabaseHelper;
import com.gigabytedevelopersinc.app.CometOTP.Utilities.FileHelper;
import com.gigabytedevelopersinc.app.CometOTP.Utilities.KeyStoreHelper;
import com.gigabytedevelopersinc.app.CometOTP.Utilities.NotificationHelper;
import com.gigabytedevelopersinc.app.CometOTP.Utilities.Settings;
import com.gigabytedevelopersinc.app.CometOTP.Utilities.Tools;

import java.util.ArrayList;

import javax.crypto.SecretKey;

//Test with: adb shell am broadcast -n com.gigabytedevelopersinc.app.CometOTP/.Receivers.PlainTextBackupBroadcastReceiver
// Use the following command to test in the dev version:
//   adb shell am broadcast -a com.gigabytedevelopersinc.app.CometOTP.broadcast.PLAIN_TEXT_BACKUP com.gigabytedevelopersinc.app.CometOTP.dev
public class PlainTextBackupBroadcastReceiver extends BackupBroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {

        Settings settings = new Settings(context);

        if (settings.isPlainTextBackupBroadcastEnabled()) {
            if (!canSaveBackup(context))
                return;

            Uri savePath = Tools.buildUri(settings.getBackupDir(), FileHelper.backupFilename(context, Constants.BackupType.PLAIN_TEXT));

            SecretKey encryptionKey = null;

            if (settings.getEncryption() == Constants.EncryptionType.KEYSTORE) {
                encryptionKey = KeyStoreHelper.loadEncryptionKeyFromKeyStore(context, false);
            } else {
                NotificationHelper.notify(context, Constants.NotificationChannel.BACKUP_FAILED, R.string.backup_receiver_title_backup_failed, R.string.backup_receiver_custom_encryption_failed);
                return;
            }

            if (Tools.isExternalStorageWritable()) {
                ArrayList<Entry> entries = DatabaseHelper.loadDatabase(context, encryptionKey);

                if (FileHelper.writeStringToFile(context, savePath, DatabaseHelper.entriesToString(entries))) {
                    NotificationHelper.notify(context, Constants.NotificationChannel.BACKUP_SUCCESS, R.string.backup_receiver_title_backup_success, savePath.getPath());
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
