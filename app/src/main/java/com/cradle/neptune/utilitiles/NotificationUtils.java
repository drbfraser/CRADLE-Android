package com.cradle.neptune.utilitiles;

import android.content.Context;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.cradle.neptune.R;

public class NotificationUtils {

    private static final String NOTIFICATION_CHANNEL_ID= "channelIdForDownloadingPatients";
    public static final int PatientDownloadingNotificationID = 99;
    public static final int PatientDownloadFailNotificationID = 98;

    public static void buildNotification(String title, String message, int id, Context context){

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.cradle_for_icon_512x512)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        notificationManager.notify(id,builder.build());
    }
}
