// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.platform;
import android.Manifest;
import android.app.*;
import android.bluetooth.BluetoothManager;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
/** Stores read-only notification preferences. */
public final class StatusNotifications {
    public static final String CHANNEL = "codec_status";
    static final int ID = 43;
    private StatusNotifications() {}
    public static SharedPreferences preferences(Context c) {
        return c.getApplicationContext().getSharedPreferences("status_notification", Context.MODE_PRIVATE);
    }
    public static boolean enabled(Context c) {
        return preferences(c).getBoolean("enabled", false);
    }
    static long generation(Context c) {
        return preferences(c).getLong("generation", 0);
    }
    public static String error(Context c) {
        return preferences(c).getString("error", "");
    }
    static void note(Context c, String message) {
        String safe = message == null ? "" : message.substring(0, Math.min(2048, message.length()));
        if (!safe.equals(error(c)))
            preferences(c).edit().putString("error", safe).apply();
    }
    public static boolean visible(Context c) {
        try {
            var manager = c.getSystemService(NotificationManager.class);
            var channel = manager == null ? null : manager.getNotificationChannel(CHANNEL);
            return c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED
                && manager != null && manager.areNotificationsEnabled()
                && (channel == null || channel.getImportance() != NotificationManager.IMPORTANCE_NONE);
        } catch (RuntimeException e) {
            return false;
        }
    }
    public static boolean nearby(Context c) {
        return c.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
            == PackageManager.PERMISSION_GRANTED;
    }
    public static boolean start(Context c) {
        try {
            if (!visible(c) || !nearby(c)) {
                return fail(c, "Not enabled: Nearby devices permission and visible notifications required");
            }
            var manager = c.getSystemService(BluetoothManager.class);
            if (manager == null || manager.getAdapter() == null)
                return fail(c, "No Bluetooth adapter");
            long epoch = generation(c);
            if (!enabled(c) || epoch <= 0)
                epoch = epoch == Long.MAX_VALUE ? 1 : epoch + 1;
            if (!preferences(c)
                    .edit()
                    .putLong("generation", epoch)
                    .putBoolean("enabled", true)
                    .putString("error", "")
                    .commit()) {
                return fail(c, "Cannot save notification preference");
            }
            DevicePresence.sync(c);
            return ensureRunning(c);
        } catch (RuntimeException e) {
            return fail(c, "Cannot start status notification: " + e);
        }
    }
    static boolean ensureRunning(Context c) {
        if (!enabled(c))
            return false;
        if (!visible(c) || !nearby(c))
            return fail(c, "Notification or Nearby devices permission revoked");
        try {
            c.startForegroundService(command(c, CodecStatusService.REFRESH));
            return true;
        } catch (RuntimeException e) {
            note(c, "Status service dispatch failed: " + e.getClass().getSimpleName());
            RestoreTrace.failure(c, "", "codec_status_service", "notification.dispatch_failed", e);
            return false;
        }
    }
    private static boolean fail(Context c, String reason) {
        try {
            disable(c, reason);
        } finally {
            try {
                c.stopService(new Intent(c, CodecStatusService.class));
            } catch (RuntimeException e) {
                note(c, reason + "; status service stop failed: " + e);
            }
        }
        return false;
    }
    public static long beginEnable(Context c) {
        long next = preferences(c).getLong("intent", 0) + 1;
        if (!preferences(c).edit().putLong("intent", next).putBoolean("wanted", true).commit())
            throw new IllegalStateException("Cannot save notification consent request");
        return next;
    }
    public static boolean pending(Context c, long token) {
        return token > 0 && preferences(c).getLong("intent", 0) == token
            && preferences(c).getBoolean("wanted", false);
    }
    public static void completeEnable(Context c, long token) {
        if (pending(c, token))
            preferences(c).edit().putBoolean("wanted", false).apply();
    }
    static void disable(Context c, String reason) {
        long epoch = generation(c);
        boolean saved = preferences(c)
                            .edit()
                            .putBoolean("enabled", false)
                            .putBoolean("wanted", false)
                            .putLong("generation", epoch == Long.MAX_VALUE ? 1 : epoch + 1)
                            .commit();
        note(c, saved ? reason : reason + "; disabling preference could not be saved");
        DevicePresence.sync(c);
    }
    public static void stop(Context c) {
        disable(c, "");
        try {
            c.stopService(new Intent(c, CodecStatusService.class));
        } catch (RuntimeException e) {
            note(c, "Status service stop failed: " + e);
        }
    }
    static Intent command(Context c, String action) {
        long epoch = generation(c);
        Uri uri = new Uri.Builder()
                      .scheme("codec-status")
                      .authority(c.getPackageName())
                      .appendPath(Long.toString(epoch))
                      .appendPath(action)
                      .build();
        return new Intent(c, CodecStatusService.class)
            .setAction(action)
            .setData(uri)
            .putExtra("generation", epoch);
    }
}
