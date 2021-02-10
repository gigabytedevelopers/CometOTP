package com.gigabytedevelopersinc.app.cometOTP.Tasks;

import android.content.Context;
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
 * Time: 12:14 AM
 * Desc: PlainTextRestoreTask
 **/
public class PlainTextRestoreTask extends GenericRestoreTask {
    public PlainTextRestoreTask(Context context, Uri uri) {
        super(context, uri);
    }

    @Override
    @NonNull
    protected RestoreTaskResult doInBackground() {
        String data = StorageAccessHelper.loadFileString(applicationContext, uri);
        return RestoreTaskResult.success(data);
    }
}
