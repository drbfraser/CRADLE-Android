package com.cradleplatform.neptune.utilities.notification

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.cradleplatform.neptune.R
import android.os.SystemClock
import android.util.Log
import java.util.concurrent.TimeUnit

class NotificationManagerCustom {
    companion object {
        private lateinit var channelID: String
        private const val TAG = "MyNotificationManager"

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

        /**
         * The tab action need to be passed to this activity
         * Ex:  val intent = Intent(this, DashBoardActivity::class.java).apply {
         *          flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
         *      }
         */
        fun pushNotification(
            context: Context,
            title: String,
            msg: String,
            notificationID: Int,
            intent: Intent
        ) {
            val pendingIntent: PendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
            else
                PendingIntent.getActivity(context, 0, intent, 0)

            val builder = NotificationCompat.Builder(context, channelID)
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

        // https://stackoverflow.com/questions/36902667/how-to-schedule-notification-in-android
        fun scheduleNotification(
            context: Context,
            title: String,
            msg: String,
            notificationID: Long,
            intent: Intent,
            timeInMinutes: Int, // push the notification in _ minutes
        ) {
            val pendingIntent: PendingIntent = constructPendingIntentForAlarmManager(
                context,
                intent,
                title,
                msg,
                notificationID.toInt()
            )
            val futureInMillis: Long =
                SystemClock.elapsedRealtime() + TimeUnit.MINUTES.toMillis(timeInMinutes.toLong())

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.set(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                futureInMillis,
                pendingIntent
            )
            Log.d(TAG, "notification scheduled, title: $title")
        }

        private fun constructPendingIntentForAlarmManager(
            context: Context,
            intent: Intent,
            title: String,
            msg: String,
            notificationID: Int
        ): PendingIntent {
            val pendingIntent: PendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
            else
                PendingIntent.getActivity(context, 0, intent, 0)

            val builder = NotificationCompat.Builder(context, channelID)
                .setSmallIcon(R.drawable.cradle_for_icon_512x512)
                .setContentTitle(title)
                .setContentText(msg)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            val notification: Notification = builder.build()

            val notificationIntent = Intent(context, NotificationPublisher::class.java)
            notificationIntent.putExtra(NotificationPublisher.NOTIFICATION_ID, notificationID)
            notificationIntent.putExtra(NotificationPublisher.NOTIFICATION, notification)

            return PendingIntent.getBroadcast(
                context,
                notificationID,
                notificationIntent,
                0
            )
        }

        fun cancelScheduledNotification(
            context: Context,
            title: String,
            msg: String,
            notificationID: Long,
            intent: Intent
        ) {
            val pendingIntent: PendingIntent = constructPendingIntentForAlarmManager(
                context,
                intent,
                title,
                msg,
                notificationID.toInt()
            )
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.cancel(pendingIntent)
            Log.d(TAG, "notification cancelled, title: $title")
        }
    }
}
