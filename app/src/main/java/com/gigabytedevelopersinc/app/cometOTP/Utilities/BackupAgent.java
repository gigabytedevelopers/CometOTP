package com.gigabytedevelopersinc.app.cometOTP.Utilities;

import android.app.backup.BackupAgentHelper;
import android.app.backup.BackupDataInput;
import android.app.backup.BackupDataOutput;
import android.app.backup.FileBackupHelper;
import android.app.backup.SharedPreferencesBackupHelper;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.IOException;

public class BackupAgent extends BackupAgentHelper {
    static final String PREFS_BACKUP_KEY = "prefs";
    static final String FILES_BACKUP_KEY = "files";

    // PreferenceManager.getDefaultSharedPreferencesName is only available in API > 24, this is its implementation
    String getDefaultSharedPreferencesName() {
        return getPackageName() + "_preferences";
    }

    @Override
    public void onBackup(ParcelFileDescriptor oldState, BackupDataOutput data, ParcelFileDescriptor newState) throws IOException {
        Settings settings = new Settings(this);

        StringBuilder stringBuilder = new StringBuilder("onBackup called with the backup service set to ");
        stringBuilder.append(settings.getAndroidBackupServiceEnabled() ? "enabled" : "disabled");

        if (settings.getAndroidBackupServiceEnabled()) {
            synchronized (DatabaseHelper.DatabaseFileLock) {
                stringBuilder.append(" calling parent onBackup");
                super.onBackup(oldState, data, newState);
            }
        }
        Log.d(BackupAgent.class.getSimpleName(), stringBuilder.toString());
    }

    @Override
    public void onRestore(BackupDataInput data, int appVersionCode, ParcelFileDescriptor newState) throws IOException {
        Settings settings = new Settings(this);
        StringBuilder stringBuilder = new StringBuilder("onRestore called with the backup service set to ");
        stringBuilder.append(settings.getAndroidBackupServiceEnabled() ? "enabled" : "disabled");

        synchronized (DatabaseHelper.DatabaseFileLock) {
            stringBuilder.append(" but restore happens regardless, calling parent onRestore");
            super.onRestore(data, appVersionCode, newState);
        }
        Log.d(BackupAgent.class.getSimpleName(), stringBuilder.toString());
    }

    @Override
    public void onCreate() {
        String prefs = getDefaultSharedPreferencesName();

        SharedPreferencesBackupHelper sharedPreferencesBackupHelper = new SharedPreferencesBackupHelper(this, prefs);
        addHelper(PREFS_BACKUP_KEY, sharedPreferencesBackupHelper);

        FileBackupHelper fileBackupHelper = new FileBackupHelper(this, Constants.FILENAME_DATABASE, Constants.FILENAME_DATABASE_BACKUP);
        addHelper(FILES_BACKUP_KEY, fileBackupHelper);
    }
}
