package com.cradleplatform.neptune.utilities.notification

import android.app.Notification
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.app.NotificationManager
import okhttp3.internal.notify

class NotificationPublisher : BroadcastReceiver() {
    companion object {
        var NOTIFICATION_ID = "Cradle"
        var NOTIFICATION = "CradleNotification"
    }
    override fun onReceive(context: Context?, intent: Intent?) {
        val notificationManager =
            context!!.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val notification: Notification? = intent!!.getParcelableExtra(NOTIFICATION)
        val notificationId = intent.getIntExtra(NOTIFICATION_ID, 0)
        notificationManager.notify(notificationId, notification)
    }
}
