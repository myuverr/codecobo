// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.platform

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.myuverr.codecobo.R
import com.myuverr.codecobo.ui.MainActivity

internal object RestoreNotification {
    @JvmStatic
    fun build(context: Context): Notification {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(RestoreService.CHANNEL, context.getString(R.string.notification_restore_channel),
                NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val pause = PendingIntent.getService(context, 1,
            Intent(context, RestoreService::class.java).setAction(RestoreService.PAUSE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(context, RestoreService.CHANNEL)
            .setSmallIcon(R.drawable.ic_headphones)
            .setContentTitle(context.getString(R.string.notification_restore_title))
            .setContentText(context.getString(R.string.notification_restore_body))
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .addAction(Notification.Action.Builder(null,
                context.getString(R.string.notification_restore_pause), pause).build())
            .build()
    }
}
