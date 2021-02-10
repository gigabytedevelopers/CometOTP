package com.gigabytedevelopersinc.app.cometOTP.Tasks;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.gigabytedevelopersinc.app.cometOTP.Database.Entry;
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Constants;
import com.gigabytedevelopersinc.app.cometOTP.Utilities.DatabaseHelper;
import com.gigabytedevelopersinc.app.cometOTP.Utilities.StorageAccessHelper;

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
 * Time: 12:12 AM
 * Desc: PlainTextBackupTask
 **/
public class PlainTextBackupTask extends GenericBackupTask {
    private final ArrayList<Entry> entries;

    public PlainTextBackupTask(Context context, ArrayList<Entry> entries, @Nullable Uri uri) {
        super(context, uri);
        this.entries = entries;
    }

    @Override
    @NonNull
    protected Constants.BackupType getBackupType() {
        return Constants.BackupType.PLAIN_TEXT;
    }

    @Override
    protected boolean doBackup() {
        String payload = DatabaseHelper.entriesToString(entries);
        return StorageAccessHelper.saveFile(applicationContext, uri, payload);
    }
}