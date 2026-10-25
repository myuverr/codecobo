// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.core;

/** Reads before submission; SAVE does not write Bluetooth parameters. */
public final class ManualSubmission {
    private final ApplyEngine.Pause pause;
    public ManualSubmission(ApplyEngine.Pause pause) {
        this.pause = pause;
    }

    public ApplyEngine.Result execute(ApplyEngine.Device device, CodecRequest target, SubmissionIntent intent,
        OperationGate gate, long token) throws Exception {
        if (intent == SubmissionIntent.NONE || !gate.isCurrent(token) || !device.connected())
            return cancelled();
        CodecState actual = null;
        try {
            actual = device.read();
        } catch (Exception error) {
            if (!intent.applies())
                throw error;
        }
        if (!gate.isCurrent(token) || !device.connected())
            return cancelled();
        if (CodecRules.matches(target, actual))
            return new ApplyEngine.Result(ApplyEngine.Status.VERIFIED, actual, "Actual output verified");
        if (!intent.applies())
            return new ApplyEngine.Result(
                ApplyEngine.Status.DIFFERENT, actual, "Actual output changed; not saved or applied");
        return new ApplyEngine(pause).apply(device, target, gate, token);
    }

    private static ApplyEngine.Result cancelled() {
        return new ApplyEngine.Result(
            ApplyEngine.Status.CANCELLED, null, "Device or operation is no longer current");
    }
}
