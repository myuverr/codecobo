// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.ui

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import androidx.core.content.edit
import com.myuverr.codecobo.platform.RestoreTrace
import com.myuverr.codecobo.platform.StatusNotifications

/** Tracks notification consent across permission requests and recreation. */
internal class StatusNotificationControl(
    private val activity: Activity,
    saved: Bundle?,
    private val requestPermission: () -> Unit,
    private val onNearbyMissing: () -> Unit,
    private val observer: (State) -> Unit,
) : AutoCloseable {

    /** The switch shows the outcome; a failed enable simply stays off. */
    data class State(val checked: Boolean)

    private val preferences = StatusNotifications.preferences(activity)
    private val activityPreferences = activity.getPreferences(Activity.MODE_PRIVATE)
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> update() }
    private var current = State(false)
    private var permissionPending = saved?.getBoolean("status.permissionPending") == true
    private var settingsPending = saved?.getBoolean("status.settingsPending") == true
    private var token = saved?.getLong("status.intent") ?: 0L
    private var closed = false

    init {
        preferences.registerOnSharedPreferenceChangeListener(listener)
        render()
    }

    fun state(): State = current

    fun setEnabled(enabled: Boolean) {
        if (closed) return
        if (!enabled) {
            permissionPending = false
            settingsPending = false
            StatusNotifications.stop(activity)
            render()
            return
        }
        if (!StatusNotifications.nearby(activity)) {
            onNearbyMissing()
            render()
            return
        }
        try {
            token = StatusNotifications.beginEnable(activity)
            if (StatusNotifications.visible(activity)) {
                finishEnable()
                return
            }
            val granted = activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            val asked = activityPreferences.getBoolean("askedNotifications", false)
            // Android stops showing the dialog once the user denies it twice.
            val deniedPermanently = asked && !activity.shouldShowRequestPermissionRationale(
                Manifest.permission.POST_NOTIFICATIONS,
            )
            if (granted || deniedPermanently) {
                openNotificationSettings()
            } else {
                permissionPending = true
                activityPreferences.edit { putBoolean("askedNotifications", true) }
                render()
                requestPermission()
            }
        } catch (error: RuntimeException) {
            permissionPending = false
            settingsPending = false
            StatusNotifications.completeEnable(activity, token)
            RestoreTrace.failure(activity, "", "status_notification", "notification.enable_failed", error)
            render()
        }
    }

    /** Android only lets the channel switch change once the app itself may notify. */
    private fun openNotificationSettings() {
        settingsPending = true
        render()
        val manager = activity.getSystemService(NotificationManager::class.java)
        val channelBlocked = manager?.areNotificationsEnabled() == true &&
            manager.getNotificationChannel(StatusNotifications.CHANNEL)?.importance ==
            NotificationManager.IMPORTANCE_NONE
        val intent = if (channelBlocked) {
            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName)
                .putExtra(Settings.EXTRA_CHANNEL_ID, StatusNotifications.CHANNEL)
        } else {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName)
        }
        try {
            activity.startActivity(intent)
        } catch (_: RuntimeException) {
            activity.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName),
            )
        }
    }

    fun permissionResult() {
        if (closed) return
        if (permissionPending) finishEnable() else render()
    }

    fun resume() {
        if (closed) return
        if (!StatusNotifications.pending(activity, token)) {
            permissionPending = false
            settingsPending = false
        }
        when {
            settingsPending -> finishEnable()
            !permissionPending && StatusNotifications.enabled(activity) -> StatusNotifications.start(activity)
        }
        render()
    }

    fun save(state: Bundle) {
        state.putBoolean("status.permissionPending", permissionPending)
        state.putBoolean("status.settingsPending", settingsPending)
        state.putLong("status.intent", token)
    }

    override fun close() {
        closed = true
        preferences.unregisterOnSharedPreferenceChangeListener(listener)
    }

    private fun finishEnable() {
        permissionPending = false
        settingsPending = false
        if (StatusNotifications.pending(activity, token)) {
            StatusNotifications.start(activity)
            StatusNotifications.completeEnable(activity, token)
        }
        render()
    }

    private fun update() {
        activity.runOnUiThread { if (!closed) render() }
    }

    private fun render() {
        val pending = (permissionPending || settingsPending) && StatusNotifications.pending(activity, token)
        current = State(pending || StatusNotifications.enabled(activity))
        observer(current)
    }
}
