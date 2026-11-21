// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.platform

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.myuverr.codecobo.R
import com.myuverr.codecobo.ui.MainActivity

/** Native presentation only; observation and monitoring policy belong to the service. */
internal object CodecStatusNotification {
    @JvmStatic
    fun build(context: Context, title: String, body: String, observedAtMillis: Long): Notification {
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val publicView = Notification.Builder(context, StatusNotifications.CHANNEL)
            .setSmallIcon(R.drawable.ic_headphones)
            .build()
        return Notification.Builder(context, StatusNotifications.CHANNEL)
            .setSmallIcon(R.drawable.ic_headphones)
            .setContentTitle(title.takeIf(String::isNotBlank))
            .setContentText(body)
            .setStyle(Notification.BigTextStyle().bigText(body))
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setPublicVersion(publicView)
            .setShowWhen(true)
            .setWhen(observedAtMillis)
            .build()
    }
}
