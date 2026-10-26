// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.platform;

import android.content.Context;
import android.os.*;
import java.util.*;
import java.util.concurrent.*;

/** Resolves connected devices from system events with bounded waits. */
public final class DeviceConnectionChecks {
    private DeviceConnectionChecks() {}
    private static final Handler main = new Handler(Looper.getMainLooper());
    private static final ExecutorService worker = Executors.newSingleThreadExecutor();
    private static final Map<Context, State> states = new IdentityHashMap<>();

    /** Receipt deadline includes time queued behind Binder calls. */
    static final class Completion implements Runnable {
        final Context context;
        final Runnable callback;
        volatile boolean expired;
        boolean completed;
        final Runnable deadline = () -> {
            expired = true;
            run();
        };
        Completion(Context context, Runnable callback) {
            this.context = context;
            this.callback = callback;
            main.postDelayed(deadline, 10_000);
        }
        @Override
        public void run() {
            if (Looper.myLooper() != main.getLooper()) {
                main.post(this);
                return;
            }
            if (completed)
                return;
            completed = true;
            main.removeCallbacks(deadline);
            try {
                if (callback != null)
                    callback.run();
            } catch (RuntimeException e) {
                RestoreTrace.failure(context, "", "connection_check", "connection.receipt_failed", e);
            }
        }
    }
    private record Request(String address, boolean appearing, boolean restore, String source, Completion done,
        long epoch, int associationId, boolean companion, String id) {
        String key() {
            return (companion ? "association=" + associationId + "|" : "") + String.valueOf(address) + "|"
                + restore;
        }
    }
    private static final class State {
        final Context context;
        final LinkedHashMap<String, Request> pending = new LinkedHashMap<>();
        Job active;
        State(Context context) {
            this.context = context;
        }
        void enqueue(Request request) {
            if (pending.containsKey(request.key())) {
                Request old = pending.put(request.key(), request);
                old.done().run();
                return;
            }
            if (pending.size() >= 16) {
                log(request, "connection.check_skipped", "reason=queue_full");
                request.done().run();
                return;
            }
            pending.put(request.key(), request);
            next();
        }
        void next() {
            while (active == null && !pending.isEmpty()) {
                var first = pending.entrySet().iterator();
                Request request = first.next().getValue();
                first.remove();
                if (request.done().expired) {
                    log(request, "connection.request_expired", "reason=receipt_expired");
                    continue;
                }
                active = new Job(this, request);
                active.begin();
            }
            if (active == null && pending.isEmpty())
                states.remove(context);
        }
        void log(Request r, String stage, String detail) {
            RestoreTrace.event(context, r.id(), r.source(), stage, detail);
        }
    }
    private static final class Job {
        final State state;
        final Request request;
        BluetoothRuntime.Lease lease;
        volatile boolean valid = true;
        volatile String targetAddress;
        final Runnable timeout = this::timeout;
        Job(State state, Request request) {
            this.state = state;
            this.request = request;
            targetAddress = request.address();
        }
        void timeout() {
            if (!valid)
                return;
            valid = false;
            state.log(
                request, "connection.check_timeout", "awaiting_inflight_call=true replacement_reader=false");
            request.done().run();
            release();
            // Keep the slot occupied until Binder returns.
        }
        void begin() {
            state.log(request, "connection.check_started",
                "device=" + request.address() + " appearing=" + request.appearing()
                    + " restore=" + request.restore());
            try {
                lease = BluetoothRuntime.acquire(state.context, () -> {});
                lease.repository().bind();
                main.postDelayed(timeout, 15_000);
                worker.execute(this::read);
            } catch (RuntimeException | LinkageError e) {
                failed(e);
                finish();
            }
        }
        void read() {
            List<BluetoothRepository.DeviceInfo> found = List.of();
            Throwable error = null;
            long generation = 0;
            boolean eligible = true;
            var repository = lease.repository();
            try {
                if (request.companion()) {
                    targetAddress =
                        DevicePresence.resolve(state.context, request.associationId(), request.address());
                    eligible = targetAddress != null && DevicePresence.wanted(state.context, targetAddress);
                }
                for (int i = 0; eligible && i < 20 && valid && !request.done().expired; i++) {
                    if (!repository.enabled())
                        break;
                    generation = repository.connectionGeneration();
                    try {
                        found = repository.devices()
                                    .stream()
                                    .filter(d -> !repository.disconnectedHint(d.address()))
                                    .collect(java.util.stream.Collectors.toList());
                        boolean ready = targetAddress == null || !request.appearing()
                            || found.stream().anyMatch(d -> d.address().equals(targetAddress));
                        if (ready || i == 19)
                            break;
                    } catch (SecurityException e) {
                        throw e;
                    } catch (IllegalStateException e) {
                        if (i == 19)
                            throw e;
                    }
                    Thread.sleep(500);
                }
            } catch (Exception | LinkageError e) {
                error = e;
            }
            try {
                if (valid && !request.done().expired) {
                    if (!eligible)
                        state.log(request, "connection.presence_skipped",
                            "reason=association_invalid_or_no_enabled_features");
                    else if (error != null)
                        failed(error);
                    else if (generation == repository.connectionGeneration())
                        dispatch(found, generation);
                    else
                        state.log(request, "connection.check_stale", "reason=connection_generation_changed");
                }
            } catch (RuntimeException | LinkageError e) {
                failed(e);
            } finally {
                main.post(this::finish);
            }
        }
        void dispatch(List<BluetoothRepository.DeviceInfo> devices, long observedGeneration) {
            Context c = state.context;
            if (!StatusNotifications.nearby(c))
                return;
            var store = new ProfileStore(c);
            if (targetAddress != null && !request.appearing()
                && devices.stream().noneMatch(d -> d.address().equals(targetAddress)))
                store.connection(targetAddress, false);
            if (!valid || request.done().expired
                || observedGeneration != lease.repository().connectionGeneration())
                return;
            if (StatusNotifications.enabled(c)
                && (request.restore() || request.epoch() == StatusNotifications.generation(c))) {
                if (!devices.isEmpty())
                    BluetoothRuntime.handoff(c);
                StatusNotifications.ensureRunning(c);
            }
            if (devices.isEmpty()) {
                state.log(request, "connection.no_devices", "restore_not_requested=true");
                return;
            }
            if (request.restore() && store.active()) {
                for (var device : devices) {
                    var profile = store.get(device.address());
                    if (profile == null || !profile.enabled())
                        continue;
                    long session = store.connection(device.address(), true);
                    if (valid && !request.done().expired
                        && observedGeneration == lease.repository().connectionGeneration())
                        // Keep the receipt through dispatch, not merely through I/O queue admission.
                        RestoreService
                            .requestCheck(c, device.address(), request.source(), request.id(), session,
                                ()
                                    -> valid && !request.done().expired
                                    && observedGeneration == lease.repository().connectionGeneration())
                            .join();
                }
            }
            state.log(request, "connection.check_ready", "actualCount=" + devices.size());
        }
        void failed(Throwable e) {
            RestoreTrace.failure(state.context, request.id(), request.source(), "connection.check_failed", e);
            try {
                if (StatusNotifications.enabled(state.context))
                    StatusNotifications.note(
                        state.context, "Connection check failed: " + e.getClass().getSimpleName());
            } catch (RuntimeException ignored) { /* Release the receipt even if tracing fails. */
            }
        }
        void release() {
            if (lease == null)
                return;
            try {
                lease.close();
            } catch (RuntimeException e) {
                RestoreTrace.failure(
                    state.context, request.id(), request.source(), "bluetooth.release_failed", e);
            }
        }
        void finish() {
            valid = false;
            main.removeCallbacks(timeout);
            release();
            request.done().run();
            if (state.active == this)
                state.active = null;
            state.log(request, "connection.check_finished", "");
            state.next();
        }
    }
    public static void request(
        Context context, String address, boolean appearing, boolean restore, String source, Runnable done) {
        requestWithReceipt(context, address, appearing, restore, source,
            new Completion(context.getApplicationContext(), done));
    }
    static void requestWithReceipt(Context context, String address, boolean appearing, boolean restore,
        String source, Completion completion) {
        Context app = context.getApplicationContext();
        Request request = new Request(ProfileStore.address(address), appearing, restore, source, completion,
            StatusNotifications.generation(app), -1, false, RestoreTrace.id());
        main.post(() -> states.computeIfAbsent(app, State::new).enqueue(request));
    }
    static void associated(Context context, int associationId, String address, boolean appearing) {
        Context app = context.getApplicationContext();
        Request request = new Request(ProfileStore.address(address), appearing, true, "companion_presence",
            new Completion(app, null), StatusNotifications.generation(app), associationId, true,
            RestoreTrace.id());
        main.post(() -> states.computeIfAbsent(app, State::new).enqueue(request));
    }
}
