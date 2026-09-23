@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Tasks

import android.content.Context
import android.net.Uri
import com.gigabytedevelopersinc.app.cometOTP.R
import com.gigabytedevelopersinc.app.cometOTP.Utilities.BackupHelper
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Constants
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Settings

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
abstract class GenericBackupTask(context: Context, uri: Uri?) :
    UiBasedBackgroundTask<GenericBackupTask.BackupTaskResult>(BackupTaskResult.failure()) {
    protected val applicationContext: Context = context.applicationContext
    protected val settings: Settings = Settings(applicationContext)

    // Read during construction, so overrides must be plain getters returning a constant (a backing
    // field in the subclass would not be initialised yet).
    @Suppress("LeakingThis")
    protected val type: Constants.BackupType = backupType
    protected var uri: Uri? = uri

    override fun doInBackground(): BackupTaskResult {
        if (uri == null) {
            val backupFile = BackupHelper.backupFile(applicationContext, settings.backupLocation, type)

            val file = backupFile.file
                ?: return BackupTaskResult(false, backupFile.errorMessage)

            uri = file.uri
        }

        val success = doBackup()

        return if (success)
            BackupTaskResult.success()
        else
            BackupTaskResult.failure()
    }

    protected abstract val backupType: Constants.BackupType
    protected abstract fun doBackup(): Boolean


    class BackupTaskResult(@JvmField val success: Boolean, @JvmField val messageId: Int) {
        companion object {
            @JvmStatic
            fun success(): BackupTaskResult {
                return BackupTaskResult(true, R.string.backup_toast_export_success)
            }

            @JvmStatic
            fun failure(): BackupTaskResult {
                return BackupTaskResult(false, R.string.backup_toast_export_failed)
            }
        }
    }
}
