package com.gigabytedevelopersinc.app.cometOTP.Utilities;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;

import androidx.core.app.NotificationCompat;

import com.gigabytedevelopersinc.app.cometOTP.R;

import static android.content.Context.NOTIFICATION_SERVICE;

public class NotificationHelper {
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

    public static void notify(Context context, Constants.NotificationChannel channel, int resIdTitle, int resIdBody) {
        notify(context, channel, resIdTitle, context.getText(resIdBody).toString());
    }

    public static void notify(Context context, Constants.NotificationChannel channel , int resIdTitle, String resBody) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, (Notification) null)
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

        NotificationManager notificationManager = (NotificationManager) context.getSystemService(NOTIFICATION_SERVICE);
        notificationManager.notify(notificationId, builder.build());
    }
}
