// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.core;
import java.util.concurrent.atomic.AtomicLong;
/** Invalidates work across device, mode, and lifecycle changes. */
public final class OperationGate {
    private final AtomicLong generation = new AtomicLong();
    public long issue() {
        return generation.incrementAndGet();
    }
    public void invalidate() {
        generation.incrementAndGet();
    }
    public boolean isCurrent(long token) {
        return generation.get() == token;
    }
}
