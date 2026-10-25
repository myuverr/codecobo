// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.platform;

import android.content.Context;
import java.util.*;

/** Shares one A2DP proxy and cache across process-local clients. */
public final class BluetoothRuntime {
    private BluetoothRuntime() {}
    private static final android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
    private static final Map<Context, Entry> entries = new IdentityHashMap<>();
    private static final class Entry {
        final Context context;
        final BluetoothRepository repository;
        final List<Lease> owners = new ArrayList<>();
        Entry(Context context) {
            this.context = context;
            repository = new BluetoothRepository(context, this::changed);
        }
        void changed() {
            List<Lease> snapshot;
            synchronized (entries) {
                snapshot = List.copyOf(owners);
            }
            for (Lease owner : snapshot)
                if (!owner.closed)
                    owner.changed.run();
        }
    }
    public static final class Lease implements AutoCloseable {
        private final Entry entry;
        private final Runnable changed;
        private volatile boolean closed;
        private Runnable expiry;
        private Lease(Entry entry, Runnable changed) {
            this.entry = entry;
            this.changed = changed;
        }
        public BluetoothRepository repository() {
            return entry.repository;
        }
        @Override
        public void close() {
            synchronized (entries) {
                if (closed)
                    return;
                closed = true;
                entry.owners.remove(this);
                if (expiry != null)
                    main.removeCallbacks(expiry);
                if (entry.owners.isEmpty()) {
                    entries.remove(entry.context);
                    entry.repository.close();
                }
            }
        }
    }
    /** Keeps the proxy briefly for a succeeding client. */
    static void handoff(Context context) {
        synchronized (entries) {
            Lease guard = acquire(context, () -> {});
            guard.expiry = guard::close;
            main.postDelayed(guard.expiry, 5_000);
        }
    }
    public static Lease acquire(Context context, Runnable changed) {
        Context app = context.getApplicationContext();
        synchronized (entries) {
            Entry entry = entries.computeIfAbsent(app, Entry::new);
            Lease owner = new Lease(entry, changed);
            entry.owners.add(owner);
            for (Lease previous : List.copyOf(entry.owners))
                if (previous.expiry != null)
                    previous.close();
            return owner;
        }
    }
}
