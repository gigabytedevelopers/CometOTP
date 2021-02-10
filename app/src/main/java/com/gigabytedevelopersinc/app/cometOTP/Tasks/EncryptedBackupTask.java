package com.gigabytedevelopersinc.app.cometOTP.Tasks;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.gigabytedevelopersinc.app.cometOTP.Database.Entry;
import com.gigabytedevelopersinc.app.cometOTP.Utilities.BackupHelper;
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Constants;
import com.gigabytedevelopersinc.app.cometOTP.Utilities.DatabaseHelper;

import java.util.ArrayList;

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
 * Time: 12:00 AM
 * Desc: EncryptedBackupTask
 **/
public class EncryptedBackupTask extends GenericBackupTask {
    private final String password;
    private final ArrayList<Entry> entries;

    public EncryptedBackupTask(Context context, ArrayList<Entry> entries, String password, @Nullable Uri uri) {
        super(context, uri);
        this.entries = entries;
        this.password = password;
    }

    @Override
    @NonNull
    protected Constants.BackupType getBackupType() {
        return Constants.BackupType.ENCRYPTED;
    }

    @Override
    protected boolean doBackup() {
        String payload = DatabaseHelper.entriesToString(entries);
        return BackupHelper.backupToFile(applicationContext, uri, password, payload);
    }
}
