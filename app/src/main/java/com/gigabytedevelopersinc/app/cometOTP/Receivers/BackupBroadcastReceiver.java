package com.gigabytedevelopersinc.app.cometOTP.Receivers;

import android.content.BroadcastReceiver;
import android.content.Context;

import com.gigabytedevelopersinc.app.cometOTP.Utilities.Settings;

public abstract class BackupBroadcastReceiver extends BroadcastReceiver {
    protected boolean canSaveBackup(Context context) {
        Settings settings = new Settings(context);
        return settings.isBackupLocationSet();
    }
}
