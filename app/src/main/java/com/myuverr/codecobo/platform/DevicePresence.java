// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.platform;

import android.companion.*;
import android.content.*;
import android.os.Build;
import java.util.*;
import java.util.concurrent.*;

/** Observes this app's existing device associations. */
public final class DevicePresence {
    private DevicePresence() {}
    private static final Object LOCK = new Object(), REGISTRATION_LOCK = new Object();
    private static final ExecutorService worker = Executors.newSingleThreadExecutor();
    private static final Map<Context, Sync> syncing = new IdentityHashMap<>();
    private static final class Sync {
        boolean dirty, force;
    }
    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("device_presence", Context.MODE_PRIVATE);
    }
    public static void sync(Context context) {
        sync(context, false);
    }
    public static void sync(Context context, boolean force) {
        Context app = context.getApplicationContext();
        synchronized (LOCK) {
            Sync existing = syncing.get(app);
            if (existing != null) {
                existing.dirty = true;
                existing.force |= force;
                return;
            }
            Sync state = new Sync();
            state.force = force;
            syncing.put(app, state);
            worker.execute(() -> {
                while (true) {
                    boolean refresh;
                    synchronized (LOCK) {
                        refresh = state.force;
                        state.force = false;
                        state.dirty = false;
                    }
                    reconcile(app, refresh);
                    synchronized (LOCK) {
                        if (!state.dirty) {
                            syncing.remove(app);
                            return;
                        }
                    }
                }
            });
        }
    }
    static boolean wanted(Context c, String address) {
        var profiles = new ProfileStore(c);
        var profile = profiles.get(address);
        return StatusNotifications.enabled(c) || (!profiles.paused() && profile != null && profile.enabled());
    }
    private static Map<String, AssociationInfo> records(Context c) {
        var manager = c.getSystemService(CompanionDeviceManager.class);
        if (manager == null)
            throw new IllegalStateException("Companion device service unavailable");
        Map<String, AssociationInfo> result = new LinkedHashMap<>();
        for (var info : manager.getMyAssociations()) {
            if (!CompanionAssociations.normalAssociation(info) || info.getDeviceMacAddress() == null)
                continue;
            String address = ProfileStore.address(info.getDeviceMacAddress().toString());
            if (address != null)
                result.put(info.getId() + "|" + address, info);
        }
        return result;
    }
    static String resolve(Context c, int id, String address) {
        try {
            for (var info : records(c).values()) {
                String actual = info.getDeviceMacAddress().toString().toUpperCase(Locale.ROOT);
                if ((id >= 0 && info.getId() == id) || (id < 0 && actual.equalsIgnoreCase(address)))
                    return actual;
            }
        } catch (RuntimeException | LinkageError e) {
            note(c, "Association check failed: " + e.getClass().getSimpleName());
        }
        return null;
    }
    public static void reconcile(Context c, boolean force) {
        synchronized (REGISTRATION_LOCK) {
            var saved = prefs(c);
            Set<String> registered = new HashSet<>(saved.getStringSet("registered", Set.of()));
            String error = "";
            int desired = 0;
            try {
                var manager = c.getSystemService(CompanionDeviceManager.class);
                var records = records(c);
                registered.retainAll(records.keySet());
                for (var entry : records.entrySet()) {
                    var info = entry.getValue();
                    String key = entry.getKey(), address = info.getDeviceMacAddress().toString();
                    boolean want = wanted(c, address) && StatusNotifications.nearby(c);
                    if (want)
                        desired++;
                    try {
                        if (want && (force || !registered.contains(key))) {
                            observe(manager, info, true);
                            registered.add(key);
                            RestoreTrace.event(c, "", "companion_observer",
                                "connection.observation_registered",
                                "id=" + info.getId() + " device=" + address);
                        } else if (!want) {
                            observe(manager, info, false);
                            registered.remove(key);
                        }
                    } catch (Exception | LinkageError e) {
                        error = "Device presence observation failed: " + e.getClass().getSimpleName() + ": "
                            + e.getMessage();
                        RestoreTrace.failure(c, "", "companion_observer", "connection.observation_failed", e);
                    }
                }
                if (!saved.edit()
                        .putStringSet("registered", registered)
                        .putInt("desired", desired)
                        .putString("error", error)
                        .commit())
                    note(c, "Cannot save device presence state");
            } catch (RuntimeException | LinkageError e) {
                note(c, "Device presence observation failed: " + e.getClass().getSimpleName());
            }
        }
    }
    private static void observe(CompanionDeviceManager manager, AssociationInfo info, boolean start)
        throws Exception {
        if (Build.VERSION.SDK_INT >= 36) {
            Api36.observe(manager, info.getId(), start);
            return;
        }
        String address = ProfileStore.address(info.getDeviceMacAddress().toString());
        if (start)
            manager.startObservingDevicePresence(address);
        else
            manager.stopObservingDevicePresence(address);
    }
    @androidx.annotation.RequiresApi(36)
    private static final class Api36 {
        static void observe(CompanionDeviceManager manager, int id, boolean start) {
            var request = new ObservingDevicePresenceRequest.Builder().setAssociationId(id).build();
            if (start)
                manager.startObservingDevicePresence(request);
            else
                manager.stopObservingDevicePresence(request);
        }
    }
    private static void note(Context c, String error) {
        prefs(c).edit().putString("error", error).apply();
    }
    public static String describe(Context c) {
        var saved = prefs(c);
        String error = saved.getString("error", "");
        int count = saved.getStringSet("registered", Set.of()).size();
        return "presence_registered_count=" + count
            + " presence_error_text=" + (error.isEmpty() ? "none" : "retained_not_exported");
    }
}
