package com.gigabytedevelopersinc.app.CometOTP.Receivers;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.support.v4.content.ContextCompat;

import com.gigabytedevelopersinc.app.CometOTP.R;
import com.gigabytedevelopersinc.app.CometOTP.Utilities.Constants;
import com.gigabytedevelopersinc.app.CometOTP.Utilities.NotificationHelper;
import com.gigabytedevelopersinc.app.CometOTP.Utilities.Settings;

import java.io.File;

public abstract class BackupBroadcastReceiver extends BroadcastReceiver {

    protected boolean canSaveBackup(Context context) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            NotificationHelper.notify(context, Constants.NotificationChannel.BACKUP_FAILED, R.string.backup_receiver_title_backup_failed, R.string.backup_receiver_read_permission_failed);
            return false;
        }

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            NotificationHelper.notify(context, Constants.NotificationChannel.BACKUP_FAILED, R.string.backup_receiver_title_backup_failed, R.string.backup_receiver_write_permission_failed);
            return false;
        }

        Settings settings = new Settings(context);
        File backupDir = new File(settings.getBackupDir());
        if(!backupDir.exists())
            backupDir.mkdirs();

        return true;
    }
}
