// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.platform;
import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.os.*;
import com.myuverr.codecobo.core.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

/** Executes one reconnect restore job at a time. */
public final class RestoreService extends Service {
    public static final String CHANNEL = "codec_restore", PAUSE = "com.myuverr.codecobo.PAUSE_RESTORE";
    private static final int NOTIFICATION = 42;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private ProfileStore profiles;
    private BluetoothRepository bluetooth;
    private BluetoothRuntime.Lease bluetoothLease;
    private CompanionAssociations associations;
    private volatile boolean closed;
    private int pending, lastStart;
    private volatile WriteArbiter.Lease activeLease;
    private final Runnable watchdog = this::timeout;
    private record Trace(String id, String source) {
        void log(Context context, String stage, String detail) {
            RestoreTrace.event(context, id, source, stage, detail);
        }
        void failure(Context context, String stage, Throwable error) {
            RestoreTrace.failure(context, id, source, stage, error);
        }
    }
    private Trace lastTrace = new Trace(RestoreTrace.id(), "restore_lifecycle");
    private volatile Trace activeTrace;
    private static Trace trace(Intent intent) {
        String id = intent == null ? null : intent.getStringExtra("trace.id");
        String source = intent == null ? null : intent.getStringExtra("trace.source");
        return new Trace(
            id == null ? RestoreTrace.id() : id, source == null ? "unattributed_request" : source);
    }
    public static void requestCheck(Context context) {
        requestCheck(context, null, "app_action");
    }
    public static void requestCheck(Context context, String address, String source) {
        requestCheck(context, address, source, RestoreTrace.id(), -1, () -> true);
    }
    static CompletableFuture<Void> requestCheck(Context context, String address, String source, String id,
        long expectedSession, BooleanSupplier eligible) {
        var handoff = new CompletableFuture<Void>();
        Trace trace = new Trace(id, source);
        trace.log(context, "restore.requested", "device=" + address);
        ProfileStore store = new ProfileStore(context);
        boolean accepted = store.execute(() -> {
            try {
                if (!eligible.getAsBoolean()) {
                    trace.log(context, "restore.skipped", "reason=request_expired");
                    return;
                }
                if (!store.active()) {
                    trace.log(context, "restore.skipped", "reason=paused_or_no_enabled_profiles");
                    return;
                }
                if (!BackgroundStatus.bluetoothReady(context)) {
                    trace.log(context, "restore.skipped", "reason=bluetooth_or_permission_not_ready");
                    store.backgroundError("Not started: check Nearby devices permission and Bluetooth");
                    return;
                }
                Intent intent = new Intent(context, RestoreService.class)
                                    .putExtra("trace.id", id)
                                    .putExtra("trace.source", source);
                if (address != null) {
                    long session = store.generation(address);
                    if (expectedSession >= 0 && session != expectedSession) {
                        trace.log(context, "restore.skipped", "reason=session_changed");
                        return;
                    }
                    intent.putExtra("address", address).putExtra("session", session);
                }
                if (!eligible.getAsBoolean()) {
                    trace.log(context, "restore.skipped", "reason=request_expired");
                    return;
                }
                trace.log(context, "restore.dispatch_started",
                    "device=" + address + " session=" + intent.getLongExtra("session", 0));
                context.startForegroundService(intent);
                trace.log(context, "restore.dispatch_returned", "awaiting_handler=true");
            } catch (RuntimeException e) {
                trace.failure(context, "restore.dispatch_failed", e);
                store.backgroundError(
                    "Cannot start restore service: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            } finally {
                handoff.complete(null);
            }
        });
        if (!accepted) {
            trace.log(context, "restore.dispatch_rejected", "reason=storage_queue_full");
            handoff.complete(null);
        }
        return handoff;
    }
    @Override
    public void onCreate() {
        super.onCreate();
        profiles = new ProfileStore(this);
    }
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        lastStart = startId;
        Trace trace = trace(intent);
        lastTrace = trace;
        trace.log(this, "restore.handler_entered", "startId=" + startId + " flags=" + flags);
        if (intent != null && PAUSE.equals(intent.getAction())) {
            trace.log(this, "restore.pause_requested", "automatic_work_stopped=true");
            closed = true;
            WriteArbiter.SHARED.cancelAutomatic();
            worker.shutdownNow();
            boolean accepted = profiles.execute(() -> {
                try {
                    profiles.setPaused(true);
                    DevicePresence.sync(this);
                } catch (RuntimeException e) {
                    profiles.backgroundError(
                        "Task stopped but pause state could not be saved completely; check the app: " + e);
                } finally {
                    main.post(() -> {
                        stopForeground(STOP_FOREGROUND_REMOVE);
                        stopSelf();
                    });
                }
            });
            if (!accepted) {
                trace.log(this, "restore.pause_not_persisted", "reason=storage_queue_full");
                stopForeground(STOP_FOREGROUND_REMOVE);
                stopSelf();
            }
            return START_NOT_STICKY;
        }
        try {
            if (closed || !BackgroundStatus.bluetoothReady(this)) {
                trace.log(this, "restore.handler_skipped",
                    "closed=" + closed + " profiles_or_bluetooth_not_ready=true");
                stopSelf(startId);
                return START_NOT_STICKY;
            }
            Notification notification = RestoreNotification.build(this);
            trace.log(this, "restore.foreground_promotion_started", "");
            startForeground(NOTIFICATION, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
            trace.log(this, "restore.foreground_promoted", "");
        } catch (RuntimeException e) {
            trace.failure(this, "restore.foreground_failed", e);
            profiles.backgroundError("Foreground promotion failed: " + e);
            stopSelf(startId);
            return START_NOT_STICKY;
        }
        try {
            if (bluetooth == null) {
                associations = new CompanionAssociations(this);
                bluetoothLease = BluetoothRuntime.acquire(this, () -> {
                    Trace owner = activeTrace == null ? lastTrace : activeTrace;
                    owner.log(this, "bluetooth.changed",
                        "generation="
                            + (bluetooth == null ? "unavailable" : bluetooth.connectionGeneration()));
                });
                bluetooth = bluetoothLease.repository();
                trace.log(this, "bluetooth.bind_requested", "");
                bluetooth.bind();
                trace.log(this, "bluetooth.bind_returned", "readiness_not_confirmed=true");
            }
        } catch (RuntimeException | LinkageError e) {
            trace.failure(this, "bluetooth.bind_failed", e);
            profiles.backgroundError("Cannot bind Bluetooth service: " + e);
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf(startId);
            return START_NOT_STICKY;
        }
        String address = intent == null ? null : ProfileStore.address(intent.getStringExtra("address"));
        long requestedSession = intent == null ? 0 : intent.getLongExtra("session", 0);
        if (pending == 0)
            main.postDelayed(watchdog, 60_000);
        pending++;
        trace.log(this, "restore.queued",
            "pending=" + pending + " device=" + address + " session=" + requestedSession);
        worker.execute(() -> {
            activeTrace = trace;
            trace.log(this, "restore.job_started", "");
            try {
                if (!profiles.active()) {
                    trace.log(this, "restore.handler_skipped", "reason=profiles_inactive");
                    return;
                }
                if (address != null)
                    restore(address, requestedSession, trace);
                else
                    scan(trace);
            } catch (InterruptedException e) {
                trace.log(this, "restore.interrupted", "");
                Thread.currentThread().interrupt();
            } catch (Exception | LinkageError e) {
                trace.failure(this, "restore.failed", e);
                if (!closed)
                    profiles.backgroundError("Restore incomplete: " + e);
            } finally {
                trace.log(this, "restore.job_finished", "closed=" + closed);
                if (activeTrace == trace)
                    activeTrace = null;
                main.post(() -> {
                    pending--;
                    if (!closed && pending == 0) {
                        main.removeCallbacks(watchdog);
                        stopForeground(STOP_FOREGROUND_REMOVE);
                        stopSelfResult(lastStart);
                    }
                });
            }
        });
        return START_NOT_STICKY;
    }
    private void scan(Trace trace) throws Exception {
        List<BluetoothRepository.DeviceInfo> found = List.of();
        for (int i = 0; i < 20 && !closed && profiles.active(); i++) {
            trace.log(this, "restore.devices_read_started", "n=" + (i + 1));
            try {
                found = bluetooth.devices();
                trace.log(this, "restore.devices_read_finished", "count=" + found.size());
                break;
            } catch (SecurityException e) {
                trace.failure(this, "restore.devices_read_failed", e);
                throw e;
            } catch (Exception e) {
                trace.failure(this, "restore.devices_waiting", e);
                if (i == 19)
                    throw e;
                Thread.sleep(500);
            }
        }
        for (var device : found) {
            if (closed || !profiles.active()) {
                trace.log(this, "restore.scan_cancelled", "reason=closed_or_profiles_inactive");
                return;
            }
            restore(device.address(), profiles.connection(device.address(), true), trace);
        }
    }
    private void restore(String address, long session, Trace trace) {
        DeviceProfile profile = profiles.get(address);
        if (profile == null || !profile.enabled() || profiles.paused() || closed) {
            trace.log(this, "restore.device_skipped",
                "device=" + address + " reason=profile_inactive_or_handler_closed");
            return;
        }
        long revision = profile.revision();
        String attempt = "device=" + address + " session=" + session + " revision=" + revision;
        trace.log(this, "restore.claim_started", attempt + " " + profiles.describeSession(address));
        if (!profiles.claim(address, session, revision)) {
            trace.log(this, "restore.claim_skipped", attempt + " " + profiles.describeSession(address));
            return;
        }
        trace.log(this, "restore.claimed", attempt);
        profiles.recordResult(address, session, revision,
            "Restore started for this connection; not yet verified and not evidence of applied settings");
        var lease = WriteArbiter.SHARED.automatic();
        if (lease == null) {
            trace.log(this, "restore.write_lease_unavailable", attempt);
            profiles.recordResult(address, session, revision,
                "Manual or restore work already running; skipped this connection");
            return;
        }
        activeLease = lease;
        long generation = bluetooth.connectionGeneration();
        var mode = getSharedPreferences("mode", MODE_PRIVATE);
        boolean requestedMode = mode.getBoolean("shizuku", false);
        trace.log(this, "restore.check", attempt + " generation=" + generation + " shizuku=" + requestedMode);
        var invalidLogged = new java.util.concurrent.atomic.AtomicBoolean();
        BooleanSupplier current = () -> {
            String reason = null;
            if (closed)
                reason = "reason=handler_closed";
            else if (!lease.current())
                reason = "reason=write_lease_invalid";
            else if (!profiles.current(address, session, revision))
                reason = "reason=session_or_profile_invalid";
            else if (bluetooth.connectionGeneration() != generation)
                reason = "reason=connection_generation_changed previous=" + generation + " -> "
                    + bluetooth.connectionGeneration();
            else if (mode.getBoolean("shizuku", false) != requestedMode)
                reason = "reason=mode_changed";
            if (reason == null)
                return true;
            if (invalidLogged.compareAndSet(false, true))
                trace.log(this, "restore.invalidated", attempt + " " + reason);
            return false;
        };
        try {
            var operation = bluetooth.operation(address, current);
            var host = new RestoreEngine.Host() {
                private int snapshots;
                public boolean associated() {
                    trace.log(RestoreService.this, "restore.association_check_started", attempt);
                    boolean value = associations.associated(address);
                    trace.log(
                        RestoreService.this, "restore.association_check_finished", "associated=" + value);
                    return value;
                }
                public boolean connected() throws Exception {
                    trace.log(RestoreService.this, "restore.connection_check_started", attempt);
                    try {
                        boolean value = operation.connected();
                        trace.log(
                            RestoreService.this, "restore.connection_check_finished", "connected=" + value);
                        return value;
                    } catch (Exception | LinkageError e) {
                        trace.failure(RestoreService.this, "restore.connection_check_failed", e);
                        throw e;
                    }
                }
                public RestoreEngine.Ready snapshot() throws Exception {
                    trace.log(RestoreService.this, "restore.snapshot_read_started",
                        "n=" + (++snapshots) + " " + attempt);
                    try {
                        var s = bluetooth.read(address);
                        trace.log(RestoreService.this, "restore.snapshot_read_finished",
                            "actual=" + s.actual() + " capabilities="
                                + s.capabilities().size());
                        return new RestoreEngine.Ready(s.actual(), s.capabilities());
                    } catch (Exception | LinkageError e) {
                        trace.failure(RestoreService.this, "restore.snapshot_read_failed", e);
                        throw e;
                    }
                }
                public void write(CodecRequest request) throws Exception {
                    trace.log(RestoreService.this, "restore.write_started", attempt + " target=" + request);
                    try {
                        operation.write(request);
                        trace.log(RestoreService.this, "restore.write_returned", "verification_pending=true");
                    } catch (Exception | LinkageError e) {
                        trace.failure(RestoreService.this, "restore.write_failed", e);
                        throw e;
                    }
                }
                public CodecState read() throws Exception {
                    trace.log(RestoreService.this, "restore.verification_read_started", attempt);
                    try {
                        var value = operation.read();
                        trace.log(
                            RestoreService.this, "restore.verification_read_finished", "actual=" + value);
                        return value;
                    } catch (Exception | LinkageError e) {
                        trace.failure(RestoreService.this, "restore.verification_read_failed", e);
                        throw e;
                    }
                }
            };
            var result = new RestoreEngine(() -> Thread.sleep(500)).restore(profile.request(), host, current);
            trace.log(this, "restore.finished",
                attempt + " status=" + result.status() + " actual=" + result.actual());
            if (current.getAsBoolean()) {
                bluetooth.automaticRestoreFinished(address, generation);
                profiles.recordResult(address, session, revision,
                    result.status() + " · " + result.detail() + "\nTarget: " + profile.request()
                        + "\nVerified actual: "
                        + (result.actual() == null ? "Unavailable" : result.actual()));
                profiles.backgroundError("");
            }
        } finally {
            lease.close();
            if (activeLease == lease)
                activeLease = null;
        }
    }
    private void timeout() {
        if (closed || pending == 0)
            return;
        Trace trace = activeTrace == null ? lastTrace : activeTrace;
        trace.log(this, "restore.watchdog_timeout",
            "pending=" + pending + " inflight_binder_cannot_be_interrupted=true");
        closed = true;
        var lease = activeLease;
        if (lease != null)
            lease.close();
        worker.shutdownNow();
        profiles.backgroundError(
            "Restore timed out; foreground service stopped and stale work will not continue when the system call returns");
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }
    @Override
    public void onConfigurationChanged(android.content.res.Configuration configuration) {
        super.onConfigurationChanged(configuration);
        if (closed || pending == 0)
            return;
        try {
            getSystemService(NotificationManager.class).notify(NOTIFICATION, RestoreNotification.build(this));
        } catch (RuntimeException error) {
            lastTrace.failure(this, "notification.locale_refresh_failed", error);
        }
    }
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    @Override
    public void onDestroy() {
        lastTrace.log(this, "restore.handler_destroyed", "pending=" + pending);
        closed = true;
        main.removeCallbacks(watchdog);
        var lease = activeLease;
        if (lease != null)
            lease.close();
        worker.shutdownNow();
        if (bluetoothLease != null)
            bluetoothLease.close();
        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }
}
