package com.gigabytedevelopersinc.app.cometOTP.Receivers;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import com.gigabytedevelopersinc.app.cometOTP.Database.Entry;
import com.gigabytedevelopersinc.app.cometOTP.R;
import com.gigabytedevelopersinc.app.cometOTP.Utilities.BackupHelper;
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Constants;
import com.gigabytedevelopersinc.app.cometOTP.Utilities.DatabaseHelper;
import com.gigabytedevelopersinc.app.cometOTP.Utilities.EncryptionHelper;
import com.gigabytedevelopersinc.app.cometOTP.Utilities.KeyStoreHelper;
import com.gigabytedevelopersinc.app.cometOTP.Utilities.NotificationHelper;
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Settings;
import com.gigabytedevelopersinc.app.cometOTP.Utilities.StorageAccessHelper;
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Tools;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

import javax.crypto.SecretKey;

// Use the following command to test in the dev version:
//   adb shell am broadcast -a com.gigabytedevelopersinc.app.cometOTP.broadcast.ENCRYPTED_BACKUP com.gigabytedevelopersinc.app.cometOTP.dev
public class EncryptedBackupBroadcastReceiver extends BackupBroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Settings settings = new Settings(context);

        if (settings.isEncryptedBackupBroadcastEnabled()) {
            if (!canSaveBackup(context))
                return;

            String password = settings.getBackupPasswordEnc();

            if (password.isEmpty()) {
                NotificationHelper.notify(context, Constants.NotificationChannel.BACKUP_FAILED, R.string.backup_receiver_title_backup_failed, R.string.backup_toast_crypt_password_not_set);
                return;
            }

            SecretKey encryptionKey = null;

            if (settings.getEncryption() == Constants.EncryptionType.KEYSTORE) {
                encryptionKey = KeyStoreHelper.loadEncryptionKeyFromKeyStore(context, false);
            } else {
                NotificationHelper.notify(context, Constants.NotificationChannel.BACKUP_FAILED, R.string.backup_receiver_title_backup_failed, R.string.backup_receiver_custom_encryption_failed);
                return;
            }

            if (Tools.isExternalStorageWritable()) {
                BackupHelper.BackupFile cryptBackupFile = BackupHelper.backupFile(context, settings.getBackupLocation(), Constants.BackupType.ENCRYPTED);

                if (cryptBackupFile.file == null) {
                    NotificationHelper.notify(context, Constants.NotificationChannel.BACKUP_FAILED, R.string.backup_receiver_title_backup_failed, cryptBackupFile.errorMessage);
                    return;
                }

                ArrayList<Entry> entries = DatabaseHelper.loadDatabase(context, encryptionKey);
                String plain = DatabaseHelper.entriesToString(entries);

                try {
                    int iter = EncryptionHelper.generateRandomIterations();
                    byte[] salt = EncryptionHelper.generateRandom(Constants.ENCRYPTION_IV_LENGTH);

                    SecretKey key = EncryptionHelper.generateSymmetricKeyPBKDF2(password, iter, salt);
                    byte[] encrypted = EncryptionHelper.encrypt(key, plain.getBytes(StandardCharsets.UTF_8));

                    byte[] iterBytes = ByteBuffer.allocate(Constants.INT_LENGTH).putInt(iter).array();
                    byte[] data = new byte[Constants.INT_LENGTH + Constants.ENCRYPTION_IV_LENGTH + encrypted.length];

                    System.arraycopy(iterBytes, 0, data, 0, Constants.INT_LENGTH);
                    System.arraycopy(salt, 0, data, Constants.INT_LENGTH, Constants.ENCRYPTION_IV_LENGTH);
                    System.arraycopy(encrypted, 0, data, Constants.INT_LENGTH + Constants.ENCRYPTION_IV_LENGTH, encrypted.length);

                    StorageAccessHelper.saveFile(context, cryptBackupFile.file.getUri(), data);

                    NotificationHelper.notify(context, Constants.NotificationChannel.BACKUP_SUCCESS, R.string.backup_receiver_title_backup_success, cryptBackupFile.file.getName());
                } catch (Exception e) {
                    e.printStackTrace();
                    NotificationHelper.notify(context, Constants.NotificationChannel.BACKUP_FAILED, R.string.backup_receiver_title_backup_failed, R.string.backup_toast_export_failed);
                }
            } else {
                NotificationHelper.notify(context, Constants.NotificationChannel.BACKUP_FAILED, R.string.backup_receiver_title_backup_failed, R.string.backup_toast_storage_not_accessible);
            }
        } else {
            NotificationHelper.notify(context, Constants.NotificationChannel.BACKUP_FAILED, R.string.backup_receiver_title_backup_failed, R.string.backup_receiver_encrypted_disabled);
        }
    }
}
