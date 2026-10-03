@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Tasks

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.gigabytedevelopersinc.app.cometOTP.Utilities.BackupNotifications
import com.gigabytedevelopersinc.app.cometOTP.Utilities.BackupRunner
import com.gigabytedevelopersinc.app.cometOTP.Utilities.BackupScheduler
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Constants
import com.gigabytedevelopersinc.app.cometOTP.Utilities.KeyStoreHelper
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Settings

/**
 * Makes a scheduled or Auto Sync backup in the background (see [BackupScheduler]).
 *
 * With KeyStore encryption the database key can be loaded here, so the backup is made straight
 * away. With password encryption the key only exists while the app is unlocked: a scheduled run
 * then only reminds the user, and the backup is made after the next unlock.
 */
class ScheduledBackupWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        val context = applicationContext
        val settings = Settings(context)
        val trigger = inputData.getString(KEY_TRIGGER) ?: TRIGGER_SCHEDULE
        val now = System.currentTimeMillis()

        if (trigger == TRIGGER_SCHEDULE) {
            if (!settings.scheduledBackupEnabled)
                return Result.success()

            // A backup made since the last run (after an unlock, or by Auto Sync) is recent
            // enough; making another one now would only push an older one out.
            if (!BackupScheduler.isOverdue(settings.lastBackupSuccess, settings.scheduledBackupInterval / 2, now))
                return Result.success()
        }

        if (settings.encryption == Constants.EncryptionType.PASSWORD) {
            if (trigger == TRIGGER_SCHEDULE && BackupScheduler.isOverdue(settings.lastBackupSuccess, settings.scheduledBackupInterval, now))
                BackupNotifications.backupDue(context)
            return Result.success()
        }

        val key = KeyStoreHelper.loadEncryptionKeyFromKeyStore(context, true)

        return when (val outcome = BackupRunner.run(context, key, now)) {
            is BackupRunner.Outcome.Success -> {
                BackupNotifications.succeeded(context, outcome.fileName)
                Result.success()
            }
            is BackupRunner.Outcome.Skipped -> Result.success()
            is BackupRunner.Outcome.Retry -> {
                if (runAttemptCount + 1 < MAX_ATTEMPTS) {
                    Result.retry()
                } else {
                    BackupNotifications.failed(context, outcome.messageId, BackupRunner.Fix.NONE)
                    Result.failure()
                }
            }
            is BackupRunner.Outcome.Failed -> {
                BackupNotifications.failed(context, outcome.messageId, outcome.fix)
                Result.failure()
            }
        }
    }

    companion object {
        const val KEY_TRIGGER = "trigger"
        const val TRIGGER_SCHEDULE = "schedule"
        const val TRIGGER_AUTO_SYNC = "auto_sync"

        private const val MAX_ATTEMPTS = 3
    }
}
