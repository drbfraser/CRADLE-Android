package com.cradleplatform.neptune.utilities.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.cradleplatform.neptune.R

class NotificationManagerCustom {
    companion object {
        private lateinit var channelID: String

        // https://developer.android.com/training/notify-user/build-notification#Priority
        fun createNotificationChannel(context: Context) {
            // Create the NotificationChannel, but only on API 26+ because
            // the NotificationChannel class is new and not in the support library
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val name = context.getString(R.string.notification_channel_name)
                val descriptionText = context.getString(R.string.notification_channel_description)
                val importance = NotificationManager.IMPORTANCE_DEFAULT
                val channel = NotificationChannel(
                    context.resources.getString(R.string.notification_id),
                    name,
                    importance
                ).apply {
                    description = descriptionText
                }
                channelID = context.resources.getString(R.string.notification_id)
                // Register the channel with the system
                val notificationManager: NotificationManager =
                    context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.createNotificationChannel(channel)
            }
        }

        fun pushNotification(context: Context, title: String, msg: String) {
            var builder = NotificationCompat.Builder(context, channelID)
                .setSmallIcon(R.drawable.cradle_for_icon_512x512)
                .setContentTitle(title)
                .setContentText(msg)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)

            with(NotificationManagerCompat.from(context)) {
                // notificationId is a unique int for each notification that you must define
                notify(0, builder.build())
            }
        }
    }
}
