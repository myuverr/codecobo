// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.platform;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import com.myuverr.codecobo.core.*;
import com.myuverr.codecobo.R;
import org.lsposed.hiddenapibypass.HiddenApiBypass;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** AOSP A2DP access. */
@SuppressLint("MissingPermission")
public final class BluetoothRepository implements AutoCloseable {
    public record DeviceInfo(String address, String name, boolean active) {}
    public record
        Snapshot(CodecState actual, String codecName, List<CodecCapability> capabilities) {}
    private record Cached(BluetoothCodecStatus status, long receivedAt) {}
    private final Context context;
    private final BluetoothAdapter adapter;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable changed;
    private final Map<String, Cached> broadcasts = new ConcurrentHashMap<>();
    private final Map<String, Long> readFloor = new ConcurrentHashMap<>();
    private final Map<String, Long> automaticRestores = new HashMap<>();
    private record ConnectionHint(int state, long receivedAt) {}
    private final Map<String, ConnectionHint> connectionStates = new ConcurrentHashMap<>();
    public boolean disconnectedHint(String address) {
        ConnectionHint hint = connectionStates.get(address);
        if (hint == null)
            return false;
        long age = SystemClock.elapsedRealtime() - hint.receivedAt();
        return age >= 0 && age <= 1_000
            && (hint.state() == BluetoothProfile.STATE_DISCONNECTED
                || hint.state() == BluetoothProfile.STATE_DISCONNECTING);
    }
    private final java.util.concurrent.atomic.AtomicLong connectionGeneration =
        new java.util.concurrent.atomic.AtomicLong();
    public long connectionGeneration() {
        return connectionGeneration.get();
    }
    private final java.util.concurrent.atomic.AtomicLong activityGeneration =
        new java.util.concurrent.atomic.AtomicLong();
    private String activityAddress;
    private boolean activityReported;
    /** Activity changes invalidate observations, not connection-bound save transactions. */
    public long activityGeneration() {
        return activityGeneration.get();
    }
    public Long automaticRestoreGeneration(String address) {
        synchronized (automaticRestores) {
            return address == null || closed ? null : automaticRestores.get(address);
        }
    }
    /** Ends connection initialization; this does not imply restore success. */
    void automaticRestoreFinished(String address, long generation) {
        synchronized (automaticRestores) {
            if (closed || generation != connectionGeneration.get()
                || automaticRestores.putIfAbsent(address, generation) != null)
                return;
        }
        notifyChanged();
    }
    private void invalidateConnection(String address) {
        // Invalidation and completion publication must not cross each other.
        synchronized (automaticRestores) {
            connectionGeneration.incrementAndGet();
            if (address == null)
                automaticRestores.clear();
            else
                automaticRestores.remove(address);
        }
    }
    private volatile BluetoothA2dp proxy;
    private volatile boolean closed;
    private boolean binding;
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent intent) {
            String action = intent.getAction();
            BluetoothDevice d =
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice.class);
            if ("android.bluetooth.a2dp.profile.action.CODEC_CONFIG_CHANGED".equals(action) && d != null) {
                BluetoothCodecStatus s = intent.getParcelableExtra(
                    BluetoothCodecStatus.EXTRA_CODEC_STATUS, BluetoothCodecStatus.class);
                if (s != null)
                    broadcasts.put(d.getAddress(), new Cached(s, SystemClock.elapsedRealtime()));
            } else if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                broadcasts.clear();
                readFloor.clear();
                invalidateConnection(null);
                if (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1) == BluetoothAdapter.STATE_ON)
                    bind();
            } else if (BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED.equals(action) && d != null) {
                invalidateConnection(d.getAddress());
                readFloor.remove(d.getAddress());
                connectionStates.put(d.getAddress(),
                    new ConnectionHint(
                        intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1), SystemClock.elapsedRealtime()));
                if (intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1) != BluetoothProfile.STATE_CONNECTED)
                    broadcasts.remove(d.getAddress());
            } else if ("android.bluetooth.a2dp.profile.action.ACTIVE_DEVICE_CHANGED".equals(action)) {
                String address = d == null ? null : d.getAddress();
                if (!activityReported || !Objects.equals(activityAddress, address)) {
                    activityReported = true;
                    activityAddress = address;
                    activityGeneration.incrementAndGet();
                }
            }
            notifyChanged();
        }
    };
    public BluetoothRepository(Context context, Runnable changed) {
        this.context = context.getApplicationContext();
        this.changed = changed;
        BluetoothManager manager = context.getSystemService(BluetoothManager.class);
        adapter = manager == null ? null : manager.getAdapter();
        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED);
        filter.addAction("android.bluetooth.a2dp.profile.action.CODEC_CONFIG_CHANGED");
        filter.addAction("android.bluetooth.a2dp.profile.action.ACTIVE_DEVICE_CHANGED");
        // Bluetooth broadcasts come from another process.
        this.context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
    }
    private void notifyChanged() {
        main.post(() -> {
            if (!closed)
                changed.run();
        });
    }
    public boolean hasPermission() {
        return context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
            == PackageManager.PERMISSION_GRANTED;
    }
    public boolean enabled() {
        return hasPermission() && adapter != null && adapter.isEnabled();
    }
    public void bind() {
        if (closed || binding || proxy != null || !enabled())
            return;
        binding = true;
        boolean requested = adapter.getProfileProxy(context, new BluetoothProfile.ServiceListener() {
            @Override
            public void onServiceConnected(int profile, BluetoothProfile service) {
                binding = false;
                if (closed) {
                    adapter.closeProfileProxy(profile, service);
                    return;
                }
                proxy = (BluetoothA2dp) service;
                notifyChanged();
            }
            @Override
            public void onServiceDisconnected(int profile) {
                proxy = null;
                binding = false;
                broadcasts.clear();
                readFloor.clear();
                invalidateConnection(null);
                notifyChanged();
            }
        }, BluetoothProfile.A2DP);
        if (!requested)
            binding = false;
    }
    public List<DeviceInfo> devices() throws Exception {
        BluetoothA2dp p = requireProxy();
        String active = null;
        try {
            BluetoothDevice d =
                (BluetoothDevice) HiddenApiBypass.invoke(BluetoothA2dp.class, p, "getActiveDevice");
            if (d != null)
                active = d.getAddress();
        } catch (ReflectiveOperationException | RuntimeException
            | LinkageError ignored) { /* activity marking is optional */
        }
        List<DeviceInfo> devices = new ArrayList<>();
        for (BluetoothDevice d : p.getConnectedDevices()) {
            String name = d.getName();
            devices.add(new DeviceInfo(d.getAddress(),
                name == null || name.isBlank() ? context.getString(R.string.notification_unnamed_device)
                                               : name,
                d.getAddress().equals(active)));
        }
        return List.copyOf(devices);
    }
    public boolean connected(String address) throws Exception {
        return devices().stream().anyMatch(d -> d.address().equals(address));
    }
    public Snapshot read(String address) throws Exception {
        return snapshot(status(address, readFloor.getOrDefault(address, 0L)));
    }
    private BluetoothCodecStatus status(String address, long notBefore) throws Exception {
        BluetoothA2dp p = requireProxy();
        if (!connected(address))
            throw new IllegalStateException("Device disconnected");
        Exception failure;
        try {
            BluetoothCodecStatus s = (BluetoothCodecStatus) HiddenApiBypass.invoke(
                BluetoothA2dp.class, p, "getCodecStatus", device(address));
            if (s != null && s.getCodecConfig() != null)
                return s;
            failure = new IllegalStateException("Bluetooth service has not returned codec status");
        } catch (ReflectiveOperationException | RuntimeException e) {
            failure = e;
        } catch (LinkageError e) {
            failure = new IllegalStateException("Platform codec query unavailable", e);
        }
        Cached cached = broadcasts.get(address);
        if (cached != null && cached.receivedAt() >= notBefore
            && SystemClock.elapsedRealtime() - cached.receivedAt() <= 10_000)
            return cached.status();
        throw failure;
    }
    private Snapshot snapshot(BluetoothCodecStatus status) {
        BluetoothCodecConfig actual = status.getCodecConfig();
        if (actual == null)
            throw new IllegalStateException("Current codec unknown");
        List<CodecCapability> caps = new ArrayList<>();
        List<BluetoothCodecConfig> selectable = status.getCodecsSelectableCapabilities();
        if (selectable != null)
            for (BluetoothCodecConfig c : selectable) {
                caps.add(new CodecCapability(
                    c.getCodecType(), CodecNames.name(context, c),
                    c.getSampleRate(), c.getBitsPerSample(), c.getChannelMode()));
            }
        return new Snapshot(new CodecState(actual.getCodecType(), actual.getSampleRate(),
                                actual.getBitsPerSample(), actual.getChannelMode(),
                                actual.getCodecType() == BluetoothCodecConfig.SOURCE_CODEC_TYPE_LDAC
                                    ? actual.getCodecSpecific1()
                                    : -1),
            CodecNames.name(context, actual), CodecRules.merge(caps));
    }
    public ApplyEngine.Device operation(String address, java.util.function.BooleanSupplier current) {
        return new ApplyEngine.Device() {
            private long submittedAt;
            @Override
            public boolean connected() throws Exception {
                return BluetoothRepository.this.connected(address);
            }
            @Override
            public void write(CodecRequest request) throws Exception {
                BluetoothCodecStatus fresh = status(address, 0);
                if (!CodecRules.valid(request, snapshot(fresh).capabilities()))
                    throw new IllegalArgumentException(
                        "Device capabilities changed; select again after the state updates");
                BluetoothCodecConfig old = fresh.getCodecConfig();
                BluetoothCodecConfig.Builder b =
                    new BluetoothCodecConfig.Builder()
                        .setCodecType(request.type())
                        .setCodecPriority(BluetoothCodecConfig.CODEC_PRIORITY_HIGHEST)
                        .setSampleRate(request.sampleRate());
                // Preserve unspecified fields only when the codec is unchanged.
                if (old != null && old.getCodecType() == request.type()) {
                    b.setBitsPerSample(old.getBitsPerSample())
                        .setChannelMode(old.getChannelMode())
                        .setCodecSpecific1(old.getCodecSpecific1())
                        .setCodecSpecific2(old.getCodecSpecific2())
                        .setCodecSpecific3(old.getCodecSpecific3())
                        .setCodecSpecific4(old.getCodecSpecific4());
                }
                if (request.bitsPerSample() != 0)
                    b.setBitsPerSample(request.bitsPerSample());
                if (request.channelMode() != 0)
                    b.setChannelMode(request.channelMode());
                if (request.ldacQuality() != 0)
                    b.setCodecSpecific1(request.ldacQuality());
                // The query may have blocked across reconnect or cancellation.
                if (!current.getAsBoolean())
                    throw new IllegalStateException("Save request or connection is no longer current");
                submittedAt = SystemClock.elapsedRealtime();
                readFloor.put(address, submittedAt);
                // A pre-write broadcast remains stale even within the same clock tick.
                broadcasts.remove(address);
                HiddenApiBypass.invoke(BluetoothA2dp.class, requireProxy(), "setCodecConfigPreference",
                    device(address), b.build());
            }
            @Override
            public CodecState read() throws Exception {
                return snapshot(status(address, submittedAt)).actual();
            }
        };
    }
    private BluetoothDevice device(String address) {
        return adapter.getRemoteDevice(address);
    }
    private BluetoothA2dp requireProxy() {
        if (!hasPermission())
            throw new SecurityException("Nearby devices permission required");
        if (!enabled())
            throw new IllegalStateException(
                adapter == null ? "No Bluetooth adapter" : "Bluetooth is disabled");
        BluetoothA2dp p = proxy;
        if (p == null)
            throw new IllegalStateException("Waiting for A2DP service");
        return p;
    }
    @Override
    public void close() {
        closed = true;
        context.unregisterReceiver(receiver);
        broadcasts.clear();
        readFloor.clear();
        synchronized (automaticRestores) {
            automaticRestores.clear();
        }
        connectionStates.clear();
        BluetoothA2dp p = proxy;
        proxy = null;
        if (p != null && adapter != null)
            adapter.closeProfileProxy(BluetoothProfile.A2DP, p);
    }
}
