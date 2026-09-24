@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Utilities

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Context.NOTIFICATION_SERVICE
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.gigabytedevelopersinc.app.cometOTP.R
import java.util.Locale

object NotificationHelper {
    private val TAG = NotificationHelper::class.java.simpleName

    private fun channelId(channel: Constants.NotificationChannel): String {
        return "CometOTP_" + channel.name.lowercase(Locale.ROOT)
    }

    private fun createNotificationChannel(context: Context, channel: Constants.NotificationChannel) {
        if (GeneralUtils.isOreo()) {
            val notificationChannel = NotificationChannel(channelId(channel), context.getString(R.string.app_name), NotificationManager.IMPORTANCE_DEFAULT)

            when (channel) {
                Constants.NotificationChannel.BACKUP_FAILED -> {
                    notificationChannel.name = context.getString(R.string.notification_channel_name_backup_failed)
                    notificationChannel.description = context.getString(R.string.notification_channel_desc_backup_failed)
                    notificationChannel.importance = NotificationManager.IMPORTANCE_HIGH
                }
                Constants.NotificationChannel.BACKUP_SUCCESS -> {
                    notificationChannel.name = context.getString(R.string.notification_channel_name_backup_success)
                    notificationChannel.description = context.getString(R.string.notification_channel_desc_backup_success)
                    notificationChannel.importance = NotificationManager.IMPORTANCE_LOW
                }
            }

            val notificationManager = context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(notificationChannel)
        }
    }

    fun initializeNotificationChannels(context: Context) {
        if (GeneralUtils.isOreo()) {
            for (channel in Constants.NotificationChannel.values()) {
                createNotificationChannel(context, channel)
            }
        }
    }

    /**
     * Whether the app is currently allowed to post notifications. On Android 13+ (API 33) this
     * requires the POST_NOTIFICATIONS runtime permission; on older versions only the per-app
     * notification toggle matters.
     */
    fun canPostNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return false
        }

        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun notify(context: Context, channel: Constants.NotificationChannel, resIdTitle: Int, resIdBody: Int) {
        notify(context, channel, resIdTitle, context.getText(resIdBody).toString())
    }

    fun notify(context: Context, channel: Constants.NotificationChannel, resIdTitle: Int, resBody: String?) {
        if (!canPostNotifications(context)) {
            // The system would silently drop the notification anyway; log it so the outcome of a
            // broadcast-triggered backup is at least visible in logcat.
            Log.w(TAG, "Notifications are not permitted, dropping: " + context.getText(resIdTitle) + " - " + resBody)
            return
        }

        val builder = NotificationCompat.Builder(context, channelId(channel))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(context.getText(resIdTitle))
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText(resBody))

        if (GeneralUtils.isOreo()) {
            builder.setPriority(NotificationCompat.PRIORITY_HIGH)
        }

        createNotificationChannel(context, channel)
        builder.setChannelId(channelId(channel))

        val notificationId = 1

        try {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        } catch (e: SecurityException) {
            Log.w(TAG, "Failed to post notification", e)
        }
    }
}
