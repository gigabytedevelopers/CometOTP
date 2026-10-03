@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Utilities

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.gigabytedevelopersinc.app.cometOTP.Activities.MainActivity
import com.gigabytedevelopersinc.app.cometOTP.R

/** Notifications about automatic backups. Tapping one always goes through the unlock screen. */
object BackupNotifications {

    fun succeeded(context: Context, fileName: String) {
        NotificationHelper.cancel(context, NotificationHelper.NOTIFICATION_ID_BACKUP_DUE)

        if (Settings(context).scheduledBackupNotifySuccess) {
            NotificationHelper.notify(context, Constants.NotificationChannel.BACKUP_SUCCESS,
                R.string.backup_receiver_title_backup_success, fileName,
                NotificationHelper.NOTIFICATION_ID_AUTO_BACKUP, openApp(context, null))
        } else {
            // A success clears the failure that may still be showing.
            NotificationHelper.cancel(context, NotificationHelper.NOTIFICATION_ID_AUTO_BACKUP)
        }
    }

    fun failed(context: Context, messageId: Int, fix: BackupRunner.Fix) {
        val action = if (fix == BackupRunner.Fix.OPEN_SCHEDULED_BACKUP) Constants.INTENT_OPEN_SCHEDULED_BACKUP else null

        NotificationHelper.notify(context, Constants.NotificationChannel.BACKUP_FAILED,
            R.string.backup_notification_title_failed, context.getString(messageId),
            NotificationHelper.NOTIFICATION_ID_AUTO_BACKUP, openApp(context, action))
    }

    /** A backup made while the app is open: the failure or reminder showing is out of date. */
    fun clear(context: Context) {
        NotificationHelper.cancel(context, NotificationHelper.NOTIFICATION_ID_AUTO_BACKUP)
        NotificationHelper.cancel(context, NotificationHelper.NOTIFICATION_ID_BACKUP_DUE)
    }

    /** Password encryption: the backup can only be made once the app is unlocked. */
    fun backupDue(context: Context) {
        NotificationHelper.notify(context, Constants.NotificationChannel.BACKUP_REMINDER,
            R.string.backup_notification_title_due, context.getString(R.string.backup_notification_msg_due),
            NotificationHelper.NOTIFICATION_ID_BACKUP_DUE, openApp(context, null))
    }

    private fun openApp(context: Context, action: String?): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(action)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

        val requestCode = if (action == null) 0 else 1
        return PendingIntent.getActivity(context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}
