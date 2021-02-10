package com.gigabytedevelopersinc.app.cometOTP.Tasks;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.gigabytedevelopersinc.app.cometOTP.R;
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Settings;

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
 * Time: 12:08 AM
 * Desc: GenericRestoreTask
 **/
public abstract class GenericRestoreTask extends UiBasedBackgroundTask<GenericRestoreTask.RestoreTaskResult> {
    protected final Context applicationContext;
    protected final Settings settings;
    protected Uri uri;

    public GenericRestoreTask(Context context, @Nullable Uri uri) {
        super(GenericRestoreTask.RestoreTaskResult.failure(R.string.backup_toast_import_failed));

        this.applicationContext = context.getApplicationContext();
        this.settings = new Settings(applicationContext);

        this.uri = uri;
    }

    @Override
    @NonNull
    protected abstract RestoreTaskResult doInBackground();

    public static class RestoreTaskResult {
        public final boolean success;
        public final String payload;
        public final int messageId;

        public boolean isPGP = false;
        public Intent decryptIntent = null;
        public Uri uri = null;

        public RestoreTaskResult(boolean success, String payload, int messageId) {
            this.success = success;
            this.payload = payload;
            this.messageId = messageId;
        }

        public RestoreTaskResult(boolean success, String payload, int messageId, boolean isPGP, Intent decryptIntent, Uri uri) {
            this.success = success;
            this.payload = payload;
            this.messageId = messageId;
            this.isPGP = isPGP;
            this.decryptIntent = decryptIntent;
            this.uri = uri;
        }

        public static RestoreTaskResult success(String payload) {
            return new RestoreTaskResult(true, payload, 0);
        }

        public static RestoreTaskResult failure(int messageId) {
            return new RestoreTaskResult(false, null, messageId);
        }
    }
}
