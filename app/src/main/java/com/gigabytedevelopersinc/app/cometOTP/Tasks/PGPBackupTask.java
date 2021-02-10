package com.gigabytedevelopersinc.app.cometOTP.Tasks;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.gigabytedevelopersinc.app.cometOTP.Utilities.Constants;
import com.gigabytedevelopersinc.app.cometOTP.Utilities.StorageAccessHelper;

/**
 * Project - CometOTP
 * Created with Android Studio
 * Company: Gigabyte Developers
 * User: Emmanuel Nwokoma
 * Title: Founder and CEO
 * Day: Wednesday, 10
 * Month: February
 * Year: 2021
 * Date: 10 Feb, 2021
 * Time: 12:18 AM
 * Desc: PGPBackupTask
 **/
public class PGPBackupTask extends GenericBackupTask {
    private final String payload;

    public PGPBackupTask(Context context, String payload, @Nullable Uri uri) {
        super(context, uri);
        this.payload = payload;
    }

    @Override
    @NonNull
    protected Constants.BackupType getBackupType() {
        return Constants.BackupType.OPEN_PGP;
    }

    @Override
    protected boolean doBackup() {
        return StorageAccessHelper.saveFile(applicationContext, uri, payload);
    }
}