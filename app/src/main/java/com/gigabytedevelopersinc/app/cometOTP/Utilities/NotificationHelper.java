package com.gigabytedevelopersinc.app.cometOTP.Utilities;

import static android.content.Context.NOTIFICATION_SERVICE;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.gigabytedevelopersinc.app.cometOTP.R;

public class NotificationHelper {
    private static final String TAG = NotificationHelper.class.getSimpleName();

    private static String channelId(Constants.NotificationChannel channel) {
        return "CometOTP_" + channel.name().toLowerCase();
    }

    private static void createNotificationChannel(Context context, Constants.NotificationChannel channel) {
        if (GeneralUtils.INSTANCE.isOreo()) {
            NotificationChannel notificationChannel = new NotificationChannel(channelId(channel), context.getString(R.string.app_name), NotificationManager.IMPORTANCE_DEFAULT);

            switch(channel) {
                case BACKUP_FAILED:
                    notificationChannel.setName(context.getString(R.string.notification_channel_name_backup_failed));
                    notificationChannel.setDescription(context.getString(R.string.notification_channel_desc_backup_failed));
                    notificationChannel.setImportance(NotificationManager.IMPORTANCE_HIGH);
                    break;
                case BACKUP_SUCCESS:
                    notificationChannel.setName(context.getString(R.string.notification_channel_name_backup_success));
                    notificationChannel.setDescription(context.getString(R.string.notification_channel_desc_backup_success));
                    notificationChannel.setImportance(NotificationManager.IMPORTANCE_LOW);
                    break;
                default:
                    break;
            }

            NotificationManager notificationManager = (NotificationManager) context.getSystemService(NOTIFICATION_SERVICE);
            notificationManager.createNotificationChannel(notificationChannel);
        }
    }

    public static void initializeNotificationChannels(Context context) {
        if (GeneralUtils.INSTANCE.isOreo()) {
            for (Constants.NotificationChannel channel : Constants.NotificationChannel.values()) {
                NotificationHelper.createNotificationChannel(context, channel);
            }
        }
    }

    /**
     * Whether the app is currently allowed to post notifications. On Android 13+ (API 33) this
     * requires the POST_NOTIFICATIONS runtime permission; on older versions only the per-app
     * notification toggle matters.
     */
    public static boolean canPostNotifications(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return false;
        }

        return NotificationManagerCompat.from(context).areNotificationsEnabled();
    }

    public static void notify(Context context, Constants.NotificationChannel channel, int resIdTitle, int resIdBody) {
        notify(context, channel, resIdTitle, context.getText(resIdBody).toString());
    }

    public static void notify(Context context, Constants.NotificationChannel channel , int resIdTitle, String resBody) {
        if (!canPostNotifications(context)) {
            // The system would silently drop the notification anyway; log it so the outcome of a
            // broadcast-triggered backup is at least visible in logcat.
            Log.w(TAG, "Notifications are not permitted, dropping: " + context.getText(resIdTitle) + " - " + resBody);
            return;
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId(channel))
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(context.getText(resIdTitle))
                .setStyle(new NotificationCompat.BigTextStyle()
                    .bigText(resBody));

        if (GeneralUtils.INSTANCE.isOreo()) {
            builder.setPriority(NotificationCompat.PRIORITY_HIGH);
        }

        createNotificationChannel(context, channel);
        builder.setChannelId(channelId(channel));

        int notificationId = 1;

        try {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build());
        } catch (SecurityException e) {
            Log.w(TAG, "Failed to post notification", e);
        }
    }
}
