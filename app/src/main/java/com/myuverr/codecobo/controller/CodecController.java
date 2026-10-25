// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.controller;

import android.content.*;
import android.os.Handler;
import android.os.Looper;
import com.myuverr.codecobo.core.*;
import com.myuverr.codecobo.controller.ScreenState.AccessMode;
import com.myuverr.codecobo.controller.ScreenState.OperationFailure;
import com.myuverr.codecobo.R;
import com.myuverr.codecobo.platform.*;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Owns UI state on the main thread and Bluetooth work on a single worker. */
public final class CodecController implements AutoCloseable {
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final OperationGate operations = new OperationGate(), reads = new OperationGate(),
                                profileJobs = new OperationGate();
    private final Context app;
    private final BluetoothRepository bluetooth;
    private final BluetoothRuntime.Lease bluetoothLease;
    private final CompanionAssociations associations;
    private final ShizukuBridge shizuku;
    private final SharedPreferences preferences;
    private final ProfileStore profiles;
    private volatile SaveTransaction saving;
    private final Consumer<ScreenState> listener;
    private List<BluetoothRepository.DeviceInfo> devices = List.of();
    private String selected;
    private BluetoothRepository.Snapshot snapshot;
    private AccessMode accessMode = AccessMode.SYSTEM;
    private OperationFailure operationFailure = OperationFailure.NONE;
    private boolean retainOutcome, profileChanging;
    private SubmissionFeedback submissionFeedback;
    private volatile boolean closed;
    private long editorRevision;
    private Long observedAutomaticRestore;
    private int messageId = R.string.controller_reading;
    private String detail = "";

    public CodecController(Context context, String restoredAddress, Consumer<ScreenState> listener) {
        this.listener = listener;
        selected = restoredAddress;
        app = context.getApplicationContext();
        preferences = context.getSharedPreferences("mode", Context.MODE_PRIVATE);
        profiles = new ProfileStore(context);
        associations = new CompanionAssociations(context);
        bluetoothLease = BluetoothRuntime.acquire(context, this::onBluetoothChanged);
        bluetooth = bluetoothLease.repository();
        observedAutomaticRestore = bluetooth.automaticRestoreGeneration(selected);
        shizuku = new ShizukuBridge(context, this::onShizukuChanged);
        accessMode = preferences.getBoolean("shizuku", false) && shizuku.ready() ? AccessMode.SHIZUKU
                                                                                 : AccessMode.SYSTEM;
        preferences.edit().putBoolean("shizuku", accessMode.requested()).apply();
    }
    private void onBluetoothChanged() {
        if (closed)
            return;
        if (saving != null && bluetooth.connectionGeneration() != saving.connectionGeneration) {
            failSave(R.string.controller_connection_changed, "");
        }
        if (saving == null)
            refresh();
    }
    private void onShizukuChanged() {
        if (closed)
            return;
        if (accessMode == AccessMode.SHIZUKU && !shizuku.ready())
            failAuthorization(shizuku.status());
        else
            emit();
    }
    public ScreenState state() {
        return new ScreenState(devices, selected, snapshot,
            saving != null || accessMode == AccessMode.AUTHORIZING || profileChanging,
            bluetooth.hasPermission(), accessMode, shizuku.ready(), messageId, operationFailure, detail,
            editorRevision, saving != null, submissionFeedback);
    }
    private void emit() {
        if (!closed)
            listener.accept(state());
    }
    private void clearWait(SaveTransaction transaction) {
        transaction.waitingSystem = false;
        if (transaction.timeout != null)
            main.removeCallbacks(transaction.timeout);
        transaction.timeout = null;
    }
    private void releaseSave() {
        var transaction = saving;
        saving = null;
        if (transaction != null) {
            clearWait(transaction);
            transaction.pending = null;
            transaction.notice = null;
            transaction.lease.close();
        }
    }
    private void invalidateSave() {
        submissionFeedback = null;
        operations.invalidate();
        if (saving != null && saving.writeStarted) {
            editorRevision++;
            snapshot = null;
        }
        releaseSave();
    }
    public void select(String address) {
        if (Objects.equals(selected, address))
            return;
        invalidateSave();
        reads.invalidate();
        selected = address;
        observedAutomaticRestore = bluetooth.automaticRestoreGeneration(address);
        snapshot = null;
        retainOutcome = false;
        refresh();
    }
    public void setShizukuMode(boolean enabled) {
        if (closed
            || (enabled && accessMode.requested()
                && (accessMode == AccessMode.AUTHORIZING || shizuku.ready())))
            return;
        invalidateSave();
        reads.invalidate();
        cancelProfileWork();
        WriteArbiter.SHARED.cancelAutomatic();
        shizuku.cancelPermissionRequest();
        preferences.edit().putBoolean("shizuku", false).apply();
        accessMode = enabled ? AccessMode.AUTHORIZING : AccessMode.SYSTEM;
        operationFailure = OperationFailure.NONE;
        detail = "";
        retainOutcome = false;
        if (!enabled) {
            shizuku.releaseService();
            messageId = R.string.controller_system_mode_selected;
            emit();
            refresh();
            return;
        }
        messageId = R.string.controller_authorizing;
        emit();
        shizuku.requestPermission(granted -> {
            if (closed || accessMode != AccessMode.AUTHORIZING)
                return;
            if (!granted || !shizuku.ready()) {
                failAuthorization(shizuku.permissionError());
                return;
            }
            accessMode = AccessMode.SHIZUKU;
            preferences.edit().putBoolean("shizuku", true).apply();
            messageId = R.string.controller_authorized;
            detail = "Bluetooth reads and writes still run in the app";
            emit();
            refresh();
        });
    }
    private void failAuthorization(String reason) {
        invalidateSave();
        reads.invalidate();
        cancelProfileWork();
        accessMode = AccessMode.AUTHORIZATION_FAILED;
        operationFailure = OperationFailure.NONE;
        shizuku.cancelPermissionRequest();
        shizuku.releaseService();
        preferences.edit().putBoolean("shizuku", false).apply();
        retainOutcome = true;
        messageId = R.string.controller_authorization_failed;
        detail = (reason.isEmpty() ? shizuku.status() : reason) + "\nFell back to system access";
        emit();
    }
    public void refresh() {
        if (closed || saving != null || accessMode == AccessMode.AUTHORIZING || profileChanging)
            return;
        bluetooth.bind();
        long token = reads.issue();
        String requested = selected;
        worker.execute(() -> {
            long connection = bluetooth.connectionGeneration();
            List<BluetoothRepository.DeviceInfo> found = List.of();
            String address = null, error = "";
            Long automaticRestore = null;
            BluetoothRepository.Snapshot value = null;
            try {
                found = bluetooth.devices();
                for (var d : found)
                    if (d.address().equals(requested))
                        address = requested;
                if (address == null) {
                    var preferred = found.stream()
                                        .filter(BluetoothRepository.DeviceInfo::active)
                                        .findFirst()
                                        .orElse(found.size() == 1 ? found.get(0) : null);
                    address = preferred == null ? null : preferred.address();
                }
                if (address != null) {
                    automaticRestore = bluetooth.automaticRestoreGeneration(address);
                    value = bluetooth.read(address);
                }
            } catch (Exception | LinkageError e) {
                error = describe(e);
            }
            var resultDevices = found;
            String resultAddress = address, resultError = error;
            var resultSnapshot = value;
            var resultAutomaticRestore = automaticRestore;
            main.post(() -> {
                if (closed || !reads.isCurrent(token) || connection != bluetooth.connectionGeneration())
                    return;
                boolean authorizationFailed = accessMode == AccessMode.AUTHORIZATION_FAILED;
                if (!Objects.equals(selected, resultAddress)) {
                    invalidateSave();
                    observedAutomaticRestore = resultAutomaticRestore;
                    if (!authorizationFailed) {
                        retainOutcome = false;
                        messageId = R.string.controller_reading;
                        detail = "";
                    }
                } else if (resultAutomaticRestore != null
                    && !Objects.equals(observedAutomaticRestore, resultAutomaticRestore)) {
                    observedAutomaticRestore = resultAutomaticRestore;
                    editorRevision++;
                }
                devices = resultDevices;
                selected = resultAddress;
                snapshot = resultSnapshot;
                if (authorizationFailed) {
                    emit();
                    return;
                }
                if (!resultError.isEmpty()) {
                    // Preserve the completed result when a later refresh fails.
                    if (!retainOutcome) {
                        messageId = R.string.controller_read_failed;
                        detail = resultError;
                    } else if (!detail.contains(resultError))
                        detail += "\nRead details: " + resultError;
                } else if (devices.isEmpty()) {
                    messageId = R.string.controller_disconnected;
                    detail = "";
                } else if (selected == null) {
                    messageId = R.string.controller_select_device;
                    detail = "";
                } else if (messageId == R.string.controller_reading
                    || messageId == R.string.controller_read_failed
                    || messageId == R.string.controller_disconnected) {
                    messageId = R.string.controller_read_complete;
                    detail = "";
                }
                emit();
            });
        });
    }
    public void submit(CodecRequest request, SubmissionIntent intent, boolean enableRestore,
        Consumer<IntentSender> pending, Consumer<String> completed) {
        if (closed || intent == null || intent == SubmissionIntent.NONE)
            return;
        if (request == null || !state().canEdit() || !CodecRules.valid(request, snapshot.capabilities())) {
            messageId = R.string.controller_edit_unavailable;
            emit();
            completed.accept(app.getString(messageId));
            return;
        }
        beginSave(request, intent, enableRestore, pending, completed);
    }
    public void prepareEdit(Consumer<IntentSender> pending, Consumer<String> completed) {
        if (state().canPrepareEdit())
            beginSave(null, SubmissionIntent.NONE, false, pending, completed);
    }
    private void beginSave(CodecRequest request, SubmissionIntent intent, boolean enableRestore,
        Consumer<IntentSender> pending, Consumer<String> completed) {
        if (closed || saving != null || accessMode == AccessMode.AUTHORIZING)
            return;
        reads.invalidate();
        var lease = WriteArbiter.SHARED.manual();
        // Manual work supersedes initialization, including a late automatic notification.
        observedAutomaticRestore = Objects.requireNonNullElse(
            bluetooth.automaticRestoreGeneration(selected), bluetooth.connectionGeneration());
        var transaction = new SaveTransaction(operations.issue(), selected, bluetooth.connectionGeneration(),
            request, intent, enableRestore, lease, pending, completed);
        saving = transaction;
        operationFailure = OperationFailure.NONE;
        submissionFeedback = request == null || intent == SubmissionIntent.NONE
            ? null
            : new SubmissionFeedback(
                  transaction.id, transaction.address, request, intent, SubmissionFeedback.Status.RUNNING);
        retainOutcome = false;
        messageId = R.string.controller_checking_association;
        detail = "";
        emit();
        transaction.timeout = () -> {
            if (!closed && saving == transaction && operations.isCurrent(transaction.id))
                failSave(R.string.controller_save_timeout, "");
        };
        main.postDelayed(transaction.timeout, 120_000);
        new SaveFlow(request, () -> current(transaction), new SaveFlow.Host() {
            @Override
            public void inspect(Consumer<Boolean> result) {
                inspectAssociation(transaction, result);
            }
            @Override
            public void associate(Runnable success) {
                requestAssociation(transaction, success);
            }
            @Override
            public void write(CodecRequest target) {
                runWrite(transaction);
            }
            @Override
            public void prepared() {
                var notice = transaction.takeNotice();
                operations.invalidate();
                releaseSave();
                messageId = R.string.controller_edit_prepared;
                emit();
                notice.accept(app.getString(R.string.controller_options_ready));
                refresh();
            }
            @Override
            public void failed() {
                failSave(R.string.controller_association_missing, detail);
            }
        }).start();
    }
    private void inspectAssociation(SaveTransaction transaction, Consumer<Boolean> result) {
        if (!current(transaction))
            return;
        worker.execute(() -> {
            boolean linked = false;
            String error = null;
            try {
                if (!bluetooth.connected(transaction.address)
                    || bluetooth.connectionGeneration() != transaction.connectionGeneration)
                    throw new IllegalStateException("Device connection changed");
                linked = associations.associated(transaction.address);
            } catch (Exception | LinkageError e) {
                error = describe(e);
            }
            boolean value = linked;
            String failure = error;
            main.post(() -> {
                if (!current(transaction))
                    return;
                if (failure != null) {
                    failSave(R.string.controller_preflight_failed, failure);
                    return;
                }
                result.accept(value);
            });
        });
    }

    private void requestAssociation(SaveTransaction transaction, Runnable success) {
        if (!current(transaction))
            return;
        if (accessMode == AccessMode.SHIZUKU) {
            messageId = R.string.controller_associating_privileged;
            emit();
            shizuku.associate(android.os.Process.myUid() / 100000, transaction.address, output -> {
                if (!current(transaction))
                    return;
                detail = output;
                success.run();
            });
        } else {
            messageId = R.string.controller_association_consent;
            transaction.waitingSystem = true;
            emit();
            try {
                associations.request(transaction.address,
                    sender
                    -> {
                        if (current(transaction) && transaction.waitingSystem) {
                            transaction.showPending(sender);
                        }
                    },
                    ()
                        -> {
                        if (!current(transaction) || !transaction.waitingSystem)
                            return;
                        transaction.waitingSystem = false;
                        success.run();
                    },
                    error -> {
                        if (current(transaction))
                            failSave(R.string.controller_association_failed, error);
                    });
            } catch (RuntimeException e) {
                failSave(R.string.controller_association_start_failed, describe(e));
            }
        }
    }

    private boolean current(SaveTransaction transaction) {
        return !closed && saving == transaction && transaction.lease.current()
            && operations.isCurrent(transaction.id) && Objects.equals(selected, transaction.address)
            && bluetooth.connectionGeneration() == transaction.connectionGeneration;
    }
    /** RESULT_OK is not an association receipt; wait for onAssociationCreated. */
    public void associationActivityResult(boolean ok) {
        if (!closed && saving != null && saving.waitingSystem && !ok)
            failSave(R.string.controller_association_cancelled, "");
    }
    private void failSave(int text, String error) {
        var notice = saving.takeNotice();
        invalidateSave();
        retainOutcome = true;
        messageId = text;
        detail = error;
        emit();
        notice.accept(app.getString(text));
        refresh();
    }
    private void runWrite(SaveTransaction transaction) {
        if (!current(transaction))
            return;
        clearWait(transaction);
        transaction.writeStarted = transaction.intent.applies();
        messageId = transaction.intent.applies() ? R.string.controller_applying : R.string.controller_saving;
        emit();
        String deviceName = devices.stream()
                                .filter(d -> d.address().equals(transaction.address))
                                .map(BluetoothRepository.DeviceInfo::name)
                                .findFirst()
                                .orElse(transaction.address);
        worker.execute(() -> {
            var result = executeSubmission(transaction, deviceName);
            main.post(() -> finishSubmission(transaction, result));
        });
    }

    private record SubmissionResult(ApplyEngine.Result outcome, BluetoothRepository.Snapshot readback,
        String error, boolean saved, String profileNote) {}

    private SubmissionResult executeSubmission(SaveTransaction transaction, String deviceName) {
        String address = transaction.address;
        var request = transaction.request;
        var intent = transaction.intent;
        ApplyEngine.Result result = null;
        String error = null;
        BluetoothRepository.Snapshot actual = null;
        try {
            var device = bluetooth.operation(address, () -> current(transaction));
            var guarded = new ApplyEngine.Device() {
                @Override
                public boolean connected() throws Exception {
                    return current(transaction) && device.connected();
                }
                @Override
                public void write(CodecRequest target) throws Exception {
                    if (!current(transaction))
                        throw new IllegalStateException("Save request is no longer current");
                    device.write(target);
                }
                @Override
                public CodecState read() throws Exception {
                    return device.read();
                }
            };
            result = new ManualSubmission(() -> Thread.sleep(500))
                         .execute(guarded, request, intent, operations, transaction.id);
        } catch (Exception | LinkageError e) {
            error = describe(e);
        }
        try {
            if (current(transaction))
                actual = bluetooth.read(address);
        } catch (Exception | LinkageError e) {
            if (error == null)
                error = describe(e);
        }
        var outcome = result;
        var fresh = actual;
        String profileNote = "";
        boolean saved = false;
        boolean verified = outcome != null && outcome.status() == ApplyEngine.Status.VERIFIED && fresh != null
            && CodecRules.matches(request, fresh.actual());
        if (current(transaction) && intent.saves() && outcome != null
            && outcome.status() == ApplyEngine.Status.VERIFIED) {
            if (verified) {
                try {
                    saved = profiles.saveVerified(address, deviceName, fresh.codecName(), request,
                        fresh.actual(), transaction.enableRestore, () -> current(transaction));
                    profileNote = saved ? "\nConfiguration saved" : "\nInvalid configuration not saved";
                } catch (RuntimeException e) {
                    saved = false;
                    profileNote = "\nProfile commit failed: " + describe(e);
                }
            } else
                profileNote = "\nFinal output unavailable or changed; previous profile retained";
        }
        return new SubmissionResult(result, actual, error, saved, profileNote);
    }

    private void finishSubmission(SaveTransaction transaction, SubmissionResult result) {
        var outcome = result.outcome();
        var fresh = result.readback();
        String failure = result.error();
        String address = transaction.address;
        var request = transaction.request;
        var intent = transaction.intent;
        if (!current(transaction))
            return;
        String profileNote = result.profileNote();
        boolean saved = result.saved();
        boolean verified = outcome != null && outcome.status() == ApplyEngine.Status.VERIFIED && fresh != null
            && CodecRules.matches(request, fresh.actual());
        var notice = transaction.takeNotice();
        operations.invalidate();
        releaseSave();
        editorRevision++;
        snapshot = fresh;
        retainOutcome = true;
        if (outcome == null) {
            messageId = R.string.controller_operation_failed;
            detail = failure == null ? "" : failure;
        } else {
            messageId = switch (outcome.status()) {
                case VERIFIED ->
                    !verified              ? R.string.controller_readback_changed
                        : intent.applies() ? R.string.controller_apply_verified
                        : saved            ? R.string.controller_profile_saved
                                           : R.string.controller_verified_not_saved;
                case DIFFERENT -> R.string.controller_different;
                case UNVERIFIED -> R.string.controller_unverified;
                case CANCELLED -> R.string.controller_cancelled;
            };
            detail =
                outcome.detail() + (failure == null ? "" : "\nCurrent state read: " + failure) + profileNote;
        }
        if (!intent.applies() && !verified && outcome != null) {
            messageId = fresh != null && !CodecRules.matches(request, fresh.actual())
                ? R.string.controller_save_actual_changed
                : R.string.controller_save_unverified;
        }
        if (submissionFeedback != null && submissionFeedback.id() == transaction.id)
            submissionFeedback = submissionFeedback.completed(verified, saved);
        emit();
        int completed = messageId;
        if (verified) {
            if (!intent.saves())
                completed = R.string.controller_applied;
            else if (!saved)
                completed = intent.applies() ? R.string.controller_applied_not_saved
                                             : R.string.controller_save_failed;
            else
                completed =
                    intent.applies() ? R.string.controller_applied_and_saved : R.string.controller_saved;
        }
        notice.accept(app.getString(completed));
        if (saved)
            DevicePresence.sync(app);
    }
    private void cancelProfileWork() {
        profileJobs.invalidate();
        profileChanging = false;
    }
    public void setAutoRestore(String address, boolean enabled, Consumer<Boolean> done) {
        if (closed || saving != null || accessMode == AccessMode.AUTHORIZING || profileChanging) {
            done.accept(false);
            return;
        }
        long token = profileJobs.issue();
        profileChanging = true;
        operationFailure = OperationFailure.NONE;
        retainOutcome = true;
        messageId = R.string.controller_checking_restore;
        emit();
        worker.execute(() -> {
            String error = null;
            boolean matches = false;
            boolean wasPaused = false;
            try {
                if (profiles.get(address) == null)
                    throw new IllegalStateException(
                        "Connect the device and save a verified configuration first");
                if (enabled && (!bluetooth.hasPermission() || !associations.associated(address)))
                    throw new IllegalStateException(
                        "Nearby devices permission and a valid association required; connect and save. Association is not requested automatically.");
                if (!closed && profileJobs.isCurrent(token))
                    profiles.setEnabled(address, enabled);
                var stored = profiles.get(address);
                matches = stored != null && stored.enabled() == enabled;
                wasPaused = profiles.paused();
            } catch (RuntimeException e) {
                error = describe(e);
            }
            String failure = error;
            boolean success = failure == null && matches;
            boolean paused = wasPaused;
            main.post(() -> {
                if (closed || !profileJobs.isCurrent(token))
                    return;
                profileChanging = false;
                operationFailure = success ? OperationFailure.NONE : OperationFailure.RESTORE;
                messageId = success
                    ? (enabled ? R.string.controller_restore_enabled : R.string.controller_restore_disabled)
                    : R.string.controller_restore_change_failed;
                detail = failure == null
                    ? (paused ? "All restores remain paused; resume in the library"
                              : "One attempt per connection; settings are not continuously enforced")
                    : failure;
                emit();
                done.accept(success);
                if (success)
                    DevicePresence.sync(app);
                if (success && enabled)
                    RestoreService.requestCheck(app);
            });
        });
    }
    private void changeProfiles(Supplier<Boolean> change, int success, boolean check) {
        if (closed || saving != null || accessMode == AccessMode.AUTHORIZING || profileChanging)
            return;
        long token = profileJobs.issue();
        profileChanging = true;
        operationFailure = OperationFailure.NONE;
        emit();
        worker.execute(() -> {
            String error = null;
            boolean changed = false;
            try {
                if (!closed && profileJobs.isCurrent(token))
                    changed = change.get();
            } catch (RuntimeException e) {
                error = describe(e);
            }
            String failure = error;
            boolean completed = changed;
            main.post(() -> {
                if (closed || !profileJobs.isCurrent(token))
                    return;
                profileChanging = false;
                retainOutcome = true;
                operationFailure = failure != null ? OperationFailure.PROFILE
                    : completed                    ? OperationFailure.NONE
                                                   : OperationFailure.PROFILE_CHANGED;
                messageId = failure != null ? R.string.controller_profile_change_failed
                    : completed             ? success
                                            : R.string.library_changed;
                detail = failure == null ? "" : failure;
                emit();
                if (failure == null && completed)
                    DevicePresence.sync(app);
                if (failure == null && completed && check)
                    RestoreService.requestCheck(app);
            });
        });
    }
    public void removeProfile(String address, long revision) {
        changeProfiles(() -> profiles.remove(address, revision), R.string.controller_profile_deleted, false);
    }
    public void pauseRestore(boolean paused) {
        if (paused)
            WriteArbiter.SHARED.cancelAutomatic();
        changeProfiles(() -> {
            profiles.setPaused(paused);
            return true;
        }, paused ? R.string.controller_restore_paused : R.string.controller_restore_resumed, !paused);
    }
    private static String describe(Throwable e) {
        Throwable cause = e;
        while (cause.getCause() != null && cause.getCause() != cause) cause = cause.getCause();
        return cause.getClass().getSimpleName() + ": " + cause.getMessage();
    }
    @Override
    public void close() {
        closed = true;
        operations.invalidate();
        reads.invalidate();
        releaseSave();
        cancelProfileWork();
        worker.shutdownNow();
        bluetoothLease.close();
        shizuku.close();
    }
}
