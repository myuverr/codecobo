// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.core;

/** Process-wide write arbitration; manual work preempts automatic work. */
public final class WriteArbiter {
    public static final WriteArbiter SHARED = new WriteArbiter();
    private Lease owner;

    public final class Lease implements AutoCloseable {
        private final boolean manual;

        private Lease(boolean manual) {
            this.manual = manual;
        }

        public boolean current() {
            synchronized (WriteArbiter.this) {
                return owner == this;
            }
        }

        @Override
        public void close() {
            synchronized (WriteArbiter.this) {
                if (owner == this)
                    owner = null;
            }
        }
    }

    public synchronized Lease manual() {
        return owner = new Lease(true);
    }

    public synchronized Lease automatic() {
        return owner == null ? owner = new Lease(false) : null;
    }

    public synchronized void cancelAutomatic() {
        if (owner != null && !owner.manual)
            owner = null;
    }
}
