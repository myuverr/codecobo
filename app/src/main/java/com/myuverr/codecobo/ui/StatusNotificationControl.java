// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.ui;
import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import com.myuverr.codecobo.R;
import com.myuverr.codecobo.platform.StatusNotifications;
/** Tracks notification consent across permission requests and recreation. */
public final class StatusNotificationControl implements AutoCloseable {
    public static final int PERMISSION = 11;
    private final Activity activity;
    private final java.util.function.Consumer<State> observer;
    private State current;
    public record State(boolean checked, boolean pending, String error) {}
    private final SharedPreferences preferences;
    private boolean permissionPending, settingsPending, closed;
    private long token;
    private String localError = "";
    private boolean nearbyRequired;
    private final SharedPreferences.OnSharedPreferenceChangeListener listener = (p, key) -> update();
    public StatusNotificationControl(
        Activity activity, Bundle saved, java.util.function.Consumer<State> observer) {
        this.activity = activity;
        this.observer = java.util.Objects.requireNonNull(observer);
        if (saved != null) {
            permissionPending = saved.getBoolean("status.permissionPending");
            settingsPending = saved.getBoolean("status.settingsPending");
            token = saved.getLong("status.intent");
        }
        preferences = StatusNotifications.preferences(activity);
        preferences.registerOnSharedPreferenceChangeListener(listener);
        render();
    }
    private void update() {
        activity.runOnUiThread(() -> {
            if (!closed)
                render();
        });
    }
    private void render() {
        boolean pending =
            (permissionPending || settingsPending) && StatusNotifications.pending(activity, token);

        String error = localError.isEmpty() ? StatusNotifications.error(activity) : localError;
        if (nearbyRequired && !StatusNotifications.nearby(activity))
            error = activity.getString(R.string.notice_status_nearby_required);
        current = new State(pending || StatusNotifications.enabled(activity), pending, error);
        observer.accept(current);
    }
    public State state() {
        return current;
    }
    public void setEnabled(boolean enabled) {
        if (closed)
            return;
        localError = "";
        nearbyRequired = false;
        if (!enabled) {
            permissionPending = settingsPending = false;
            StatusNotifications.stop(activity);
            render();
            return;
        }
        if (!StatusNotifications.nearby(activity)) {
            nearbyRequired = true;
            render();
            return;
        }
        try {
            token = StatusNotifications.beginEnable(activity);
            if (StatusNotifications.visible(activity)) {
                finishEnable();
                return;
            }
            boolean granted = activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
            boolean asked =
                activity.getPreferences(Activity.MODE_PRIVATE).getBoolean("askedNotifications", false);
            if (granted
                || (asked
                    && !activity.shouldShowRequestPermissionRationale(
                        Manifest.permission.POST_NOTIFICATIONS))) {
                settingsPending = true;
                render();
                var manager = activity.getSystemService(NotificationManager.class);
                boolean channel =
                    manager != null && manager.getNotificationChannel(StatusNotifications.CHANNEL) != null;
                Intent intent = new Intent(channel ? Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS
                                                   : Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(Settings.EXTRA_APP_PACKAGE, activity.getPackageName());
                if (channel)
                    intent.putExtra(Settings.EXTRA_CHANNEL_ID, StatusNotifications.CHANNEL);
                try {
                    activity.startActivity(intent);
                } catch (RuntimeException first) {
                    activity.startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, activity.getPackageName()));
                }
            } else {
                permissionPending = true;
                activity.getPreferences(Activity.MODE_PRIVATE)
                    .edit()
                    .putBoolean("askedNotifications", true)
                    .apply();
                render();
                activity.requestPermissions(
                    new String[] {Manifest.permission.POST_NOTIFICATIONS}, PERMISSION);
            }
        } catch (RuntimeException e) {
            permissionPending = settingsPending = false;
            StatusNotifications.completeEnable(activity, token);
            localError = e.toString();
            render();
        }
    }
    private void finishEnable() {
        permissionPending = settingsPending = false;
        if (StatusNotifications.pending(activity, token)) {
            StatusNotifications.start(activity);
            StatusNotifications.completeEnable(activity, token);
        }
        render();
    }
    public void permissionResult(int code) {
        if (closed)
            return;
        if (code == PERMISSION && permissionPending)
            finishEnable();
        else
            render();
    }
    public void resume() {
        if (closed)
            return;
        if (!StatusNotifications.pending(activity, token))
            permissionPending = settingsPending = false;
        if (settingsPending)
            finishEnable();
        else if (!permissionPending && StatusNotifications.enabled(activity))
            StatusNotifications.start(activity);
        render();
    }
    public void save(Bundle saved) {
        saved.putBoolean("status.permissionPending", permissionPending);
        saved.putBoolean("status.settingsPending", settingsPending);
        saved.putLong("status.intent", token);
    }
    @Override
    public void close() {
        closed = true;
        preferences.unregisterOnSharedPreferenceChangeListener(listener);
    }
}
