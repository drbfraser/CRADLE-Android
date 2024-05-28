package com.cradleplatform.neptune.utilities.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.cradleplatform.neptune.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationManagerGlobal @Inject constructor(@ApplicationContext private val context: Context) {
    private val channelId: String = context.resources.getString(R.string.notification_id)
    private val tag = "GlobalNotificationManager"

    // https://developer.android.com/training/notify-user/build-notification#Priority
    fun createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = context.getString(R.string.notification_channel_name)
            val descriptionText = context.getString(R.string.notification_channel_description)
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(
                channelId,
                name,
                importance
            ).apply {
                description = descriptionText
            }
            // Register the channel with the system
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * The tab action need to be passed to this activity
     * Ex:  val intent = Intent(this, DashBoardActivity::class.java).apply {
     *          flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
     *      }
     */
    fun pushNotification(
        title: String,
        msg: String,
        notificationID: Int,
        intent: Intent
    ) {
        val pendingIntent: PendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        else
            PendingIntent.getActivity(context, 0, intent, 0)

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.cradle_for_icon_512x512)
            .setContentTitle(title)
            .setContentText(msg)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        with(NotificationManagerCompat.from(context)) {
            // notificationId is a unique int for each notification that you must define
            notify(notificationID, builder.build())
        }
    }

    fun cancelNotification(notificationID: Int) {
        with(NotificationManagerCompat.from(context)) {
            cancel(notificationID)
        }
    }
}
