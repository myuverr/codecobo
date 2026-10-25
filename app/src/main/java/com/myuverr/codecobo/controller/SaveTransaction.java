// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.controller;

import android.content.IntentSender;
import com.myuverr.codecobo.core.CodecRequest;
import com.myuverr.codecobo.core.SubmissionIntent;
import com.myuverr.codecobo.core.WriteArbiter;
import java.util.function.Consumer;

/** Immutable submission context with main-thread wait state. */
final class SaveTransaction {
    final long id, connectionGeneration;
    final String address;
    final CodecRequest request; // Null for preparation.
    final SubmissionIntent intent;
    final boolean enableRestore;
    final WriteArbiter.Lease lease;
    boolean waitingSystem, writeStarted;
    Runnable timeout;
    Consumer<IntentSender> pending;
    Consumer<String> notice;

    SaveTransaction(long id, String address, long connectionGeneration, CodecRequest request,
        SubmissionIntent intent, boolean enableRestore, WriteArbiter.Lease lease,
        Consumer<IntentSender> pending, Consumer<String> notice) {
        this.id = id;
        this.address = address;
        this.connectionGeneration = connectionGeneration;
        this.request = request;
        this.intent = intent;
        this.enableRestore = enableRestore;
        this.lease = lease;
        this.pending = pending;
        this.notice = notice;
    }

    void showPending(IntentSender sender) {
        var callback = pending;
        pending = null;
        if (callback != null)
            callback.accept(sender);
    }

    Consumer<String> takeNotice() {
        var callback = notice;
        notice = null;
        return callback;
    }
}
