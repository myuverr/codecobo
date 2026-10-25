// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.platform;

import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import rikka.shizuku.Shizuku;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Official Shizuku permission and user-service integration. */
public final class ShizukuBridge implements AutoCloseable {
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Shizuku.UserServiceArgs args;
    private final Runnable changed;
    private final com.myuverr.codecobo.core.OperationGate jobs = new com.myuverr.codecobo.core.OperationGate();
    private boolean closed;
    private final Shizuku.OnBinderReceivedListener received = this::notifyChanged;
    private final Shizuku.OnBinderDeadListener died = () -> main.post(() -> {
        service = null;
        binding = false;
        jobs.invalidate();
        error = "Shizuku API connection lost";
        finishPermission(false);
        finish("Shizuku API connection lost");
        notifyChanged();
    });
    private static final java.util.concurrent.atomic.AtomicInteger permissionCodes =
        new java.util.concurrent.atomic.AtomicInteger(100);
    private int permissionCode;
    private Consumer<Boolean> permissionCompletion;
    private final Runnable permissionTimeout = () -> {
        error = "Permission request timed out";
        finishPermission(false);
    };
    private final Shizuku.OnRequestPermissionResultListener permission = (code, result) -> main.post(() -> {
        if (code != permissionCode || permissionCompletion == null || closed)
            return;
        if (result != PackageManager.PERMISSION_GRANTED)
            error = "Shizuku API permission not granted";
        finishPermission(result == PackageManager.PERMISSION_GRANTED && ready());
    });
    private IAssociationService service;
    private Runnable pending;
    private Consumer<String> completion;
    private boolean binding;
    private String error = "";
    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            if (closed || !binding)
                return;
            binding = false;
            service = IAssociationService.Stub.asInterface(binder);
            Runnable next = pending;
            pending = null;
            if (next != null)
                next.run();
            notifyChanged();
        }
        @Override
        public void onServiceDisconnected(ComponentName name) {
            service = null;
            binding = false;
            finish("Shizuku API user service disconnected");
            notifyChanged();
        }
    };
    public ShizukuBridge(Context context, Runnable changed) {
        this.changed = changed;
        int user = android.os.Process.myUid() / 100000;
        args = new Shizuku.UserServiceArgs(new ComponentName(context, AssociationUserService.class))
                   .tag("codec-association-user-" + user)
                   .version(2)
                   .daemon(false)
                   .processNameSuffix("association")
                   .debuggable(false);
        Shizuku.addBinderReceivedListenerSticky(received);
        Shizuku.addBinderDeadListener(died);
        Shizuku.addRequestPermissionResultListener(permission);
    }
    private void notifyChanged() {
        main.post(() -> {
            if (!closed)
                changed.run();
        });
    }
    public boolean ready() {
        try {
            return Shizuku.pingBinder() && !Shizuku.isPreV11() && Shizuku.getVersion() >= 13
                && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (RuntimeException e) {
            return false;
        }
    }
    public String status() {
        try {
            if (!Shizuku.pingBinder())
                return "Not connected: start your Shizuku API-compatible service";
            if (Shizuku.isPreV11() || Shizuku.getVersion() < 13)
                return "Unsupported service: Shizuku API 13+ required";
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED)
                return "Service connected; permission not granted";
            return "Permission granted; backend UID " + Shizuku.getUid()
                + (binding ? "; connecting user service" : "") + (error.isEmpty() ? "" : "\n" + error);
        } catch (RuntimeException e) {
            return "Service unavailable: " + e.getMessage();
        }
    }
    public void requestPermission(Consumer<Boolean> callback) {
        if (closed || permissionCompletion != null) {
            callback.accept(false);
            return;
        }
        permissionCompletion = callback;
        permissionCode = permissionCodes.incrementAndGet();
        error = "";
        try {
            if (ready()) {
                finishPermission(true);
                return;
            }
            if (!Shizuku.pingBinder() || Shizuku.isPreV11() || Shizuku.getVersion() < 13) {
                error = status();
                finishPermission(false);
                return;
            }
            if (Shizuku.shouldShowRequestPermissionRationale()) {
                error = "Permission denied without another prompt; allow this app in your service manager";
                finishPermission(false);
                return;
            }
            main.postDelayed(permissionTimeout, 120_000);
            Shizuku.requestPermission(permissionCode);
        } catch (RuntimeException e) {
            error = e.toString();
            finishPermission(false);
        }
    }
    public String permissionError() {
        return error;
    }
    private void finishPermission(boolean granted) {
        Consumer<Boolean> callback = permissionCompletion;
        cancelPermissionRequest();
        if (!closed && callback != null)
            callback.accept(granted);
        notifyChanged();
    }
    public void cancelPermissionRequest() {
        main.removeCallbacks(permissionTimeout);
        permissionCompletion = null;
    }
    public void associate(int userId, String address, Consumer<String> callback) {
        if (!ready()) {
            callback.accept("Shizuku API unavailable or permission not granted");
            return;
        }
        if (completion != null) {
            callback.accept("Another user-service request is running");
            return;
        }
        completion = callback;
        long job = jobs.issue();
        pending = () -> {
            IAssociationService remote = service;
            executor.execute(() -> {
                if (closed || !jobs.isCurrent(job))
                    return;
                String result;
                try {
                    result = remote.associate(userId, address);
                } catch (Exception e) {
                    result = e.toString();
                }
                String output = result;
                main.post(() -> {
                    if (jobs.isCurrent(job))
                        finish(output);
                });
            });
        };
        if (service != null && service.asBinder().isBinderAlive()) {
            Runnable next = pending;
            pending = null;
            next.run();
        } else {
            try {
                binding = true;
                Shizuku.bindUserService(args, connection);
                main.postDelayed(() -> {
                    if (!closed && jobs.isCurrent(job) && binding && pending != null) {
                        finish("Association user-service binding timed out");
                        releaseService();
                    }
                }, 5000);
            } catch (RuntimeException e) {
                binding = false;
                finish(e.toString());
            }
        }
        notifyChanged();
    }
    private void finish(String output) {
        Consumer<String> callback = completion;
        completion = null;
        pending = null;
        if (!closed && callback != null)
            callback.accept(output);
        notifyChanged();
    }
    public void releaseService() {
        jobs.invalidate();
        pending = null;
        completion = null;
        binding = false;
        service = null;
        try {
            if (Shizuku.pingBinder())
                Shizuku.unbindUserService(args, connection, true);
        } catch (RuntimeException ignored) {
        }
    }
    @Override
    public void close() {
        closed = true;
        cancelPermissionRequest();
        Shizuku.removeBinderReceivedListener(received);
        Shizuku.removeBinderDeadListener(died);
        Shizuku.removeRequestPermissionResultListener(permission);
        releaseService();
        executor.shutdownNow();
    }
}
