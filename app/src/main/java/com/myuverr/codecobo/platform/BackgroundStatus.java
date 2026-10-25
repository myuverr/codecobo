// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.platform;

import android.Manifest;
import android.app.NotificationManager;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.PowerManager;

public final class BackgroundStatus {
    private BackgroundStatus() {}
    public static boolean bluetoothReady(Context c) {
        if (c.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
            return false;
        try {
            var m = c.getSystemService(BluetoothManager.class);
            return m != null && m.getAdapter() != null && m.getAdapter().isEnabled();
        } catch (RuntimeException e) {
            return false;
        }
    }
    public static String describe(Context c, ProfileStore store) {
        StringBuilder text = new StringBuilder("restore_paused=").append(store.paused());
        text.append("\nrestore_policy=per_device_connection_events");
        text.append("\nbluetooth_connect_permission=").append(
            c.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED);
        try {
            var power = c.getSystemService(PowerManager.class);
            text.append("\nbattery_exempt=").append(power == null ? "unknown"
                : Boolean.toString(power.isIgnoringBatteryOptimizations(c.getPackageName())));
        } catch (RuntimeException e) {
            text.append("\nbattery_exempt=unknown exception=").append(e.getClass().getName());
        }
        text.append("\nnotification_permission=").append(
            c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED);
        try {
            var notifications = c.getSystemService(NotificationManager.class);
            text.append("\nnotifications_enabled=").append(
                notifications == null ? "unknown" : Boolean.toString(notifications.areNotificationsEnabled()));
            for (String id : new String[] {RestoreService.CHANNEL, StatusNotifications.CHANNEL}) {
                var channel = notifications == null ? null : notifications.getNotificationChannel(id);
                text.append("\nchannel=").append(id).append(" importance=").append(
                    notifications == null ? "unknown" : channel == null ? "not_created" : channel.getImportance());
            }
        } catch (RuntimeException e) {
            text.append("\nnotifications_read=failed exception=").append(e.getClass().getName());
        }
        text.append("\nstatus_notification_enabled=").append(StatusNotifications.enabled(c));
        text.append("\nstatus_notification_policy=enabled_foreground_observer");
        text.append("\n").append(DevicePresence.describe(c));
        text.append("\nstatus_error_text=").append(StatusNotifications.error(c).isEmpty() ? "none" : "retained_not_exported");
        text.append("\nbackground_error_text=").append(store.backgroundError().isEmpty() ? "none" : "retained_not_exported");
        text.append("\noem_background_policy=not_observed (standard exemption does not prove OEM approval)\n");
        return text.toString();
    }
}
