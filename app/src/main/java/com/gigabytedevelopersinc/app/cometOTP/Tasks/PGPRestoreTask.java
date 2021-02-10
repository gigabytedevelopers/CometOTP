package com.gigabytedevelopersinc.app.cometOTP.Tasks;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.annotation.NonNull;

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
 * Time: 12:20 AM
 * Desc: PGPRestoreTask
 **/
public class PGPRestoreTask extends GenericRestoreTask {
    private final Intent decryptIntent;

    public PGPRestoreTask(Context context, Uri uri, Intent decryptIntent) {
        super(context, uri);
        this.decryptIntent = decryptIntent;
    }

    @Override
    @NonNull
    protected RestoreTaskResult doInBackground() {
        String data = StorageAccessHelper.loadFileString(applicationContext, uri);

        return new RestoreTaskResult(true, data, 0, true, decryptIntent, uri);
    }
}
