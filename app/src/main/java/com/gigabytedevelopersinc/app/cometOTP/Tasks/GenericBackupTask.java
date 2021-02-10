package com.gigabytedevelopersinc.app.cometOTP.Tasks;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.gigabytedevelopersinc.app.cometOTP.R;
import com.gigabytedevelopersinc.app.cometOTP.Utilities.BackupHelper;
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Constants;
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
 * Time: 12:05 AM
 * Desc: GenericBackupTask
 **/
public abstract class GenericBackupTask extends UiBasedBackgroundTask<GenericBackupTask.BackupTaskResult> {
    protected final Context applicationContext;
    protected final Settings settings;
    protected final Constants.BackupType type;
    protected Uri uri;

    public GenericBackupTask(Context context, @Nullable Uri uri) {
        super(BackupTaskResult.failure());

        this.applicationContext = context.getApplicationContext();
        this.settings = new Settings(applicationContext);

        this.type = getBackupType();
        this.uri = uri;
    }

    @Override
    @NonNull
    protected BackupTaskResult doInBackground() {
        if (uri == null) {
            BackupHelper.BackupFile backupFile = BackupHelper.backupFile(applicationContext, settings.getBackupLocation(), type);

            if (backupFile.file == null)
                return new BackupTaskResult(false, backupFile.errorMessage);

            uri = backupFile.file.getUri();
        }

        boolean success = doBackup();

        if (success)
            return BackupTaskResult.success();
        else
            return BackupTaskResult.failure();
    }

    @NonNull
    protected abstract Constants.BackupType getBackupType();
    protected abstract boolean doBackup();


    public static class BackupTaskResult {
        public final boolean success;
        public final int messageId;

        public BackupTaskResult(boolean success, int messageId) {
            this.success = success;
            this.messageId = messageId;
        }

        public static BackupTaskResult success() {
            return new BackupTaskResult(true, R.string.backup_toast_export_success);
        }

        public static BackupTaskResult failure() {
            return new BackupTaskResult(false, R.string.backup_toast_export_failed);
        }
    }
}