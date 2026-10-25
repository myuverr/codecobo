// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.core;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Checks association around consent, then continues once. */
public final class SaveFlow {
    public interface Host {
        void inspect(Consumer<Boolean> result);
        void associate(Runnable success);
        void write(CodecRequest request);
        void prepared();
        void failed();
    }
    private enum Phase { NEW, CHECKING, ASSOCIATING, VERIFYING, DONE }
    private final CodecRequest request;
    private final BooleanSupplier current;
    private final Host host;
    private Phase phase = Phase.NEW;
    public SaveFlow(CodecRequest request, BooleanSupplier current, Host host) {
        this.request = request;
        this.current = current;
        this.host = host;
    }
    public void start() {
        if (phase != Phase.NEW || !current.getAsBoolean())
            return;
        phase = Phase.CHECKING;
        host.inspect(linked -> checked(Phase.CHECKING, linked));
    }
    private void checked(Phase expected, boolean linked) {
        if (phase != expected || !current.getAsBoolean())
            return;
        if (linked) {
            phase = Phase.DONE;
            if (request == null)
                host.prepared();
            else
                host.write(request);
        } else if (expected == Phase.VERIFYING) {
            phase = Phase.DONE;
            host.failed();
        } else {
            phase = Phase.ASSOCIATING;
            host.associate(() -> {
                if (phase != Phase.ASSOCIATING || !current.getAsBoolean())
                    return;
                phase = Phase.VERIFYING;
                host.inspect(value -> checked(Phase.VERIFYING, value));
            });
        }
    }
}
