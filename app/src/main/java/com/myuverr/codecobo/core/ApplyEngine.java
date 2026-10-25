// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.core;

/** Applies once, then performs bounded readback. */
public final class ApplyEngine {
    public enum Status { VERIFIED, DIFFERENT, UNVERIFIED, CANCELLED }
    public record Result(Status status, CodecState actual, String detail) {}
    public interface Device {
        boolean connected() throws Exception;
        void write(CodecRequest request) throws Exception;
        CodecState read() throws Exception;
    }
    public interface Pause {
        void await() throws InterruptedException;
    }
    private final Pause pause;
    public ApplyEngine(Pause pause) {
        this.pause = pause;
    }
    public Result apply(Device device, CodecRequest target, OperationGate gate, long token) throws Exception {
        if (!gate.isCurrent(token) || !device.connected())
            return new Result(Status.CANCELLED, null, "Device or operation is no longer current");
        device.write(target);
        CodecState actual = null;
        String error = "No verifiable state returned";
        for (int i = 0; i < 6; i++) {
            pause.await();
            if (!gate.isCurrent(token) || !device.connected())
                return new Result(Status.CANCELLED, null, "Device or operation is no longer current");
            try {
                actual = device.read();
                if (CodecRules.matches(target, actual))
                    return new Result(Status.VERIFIED, actual, "Applied and verified");
            } catch (Exception e) {
                actual = null;
                error = e.toString();
            }
        }
        return new Result(actual == null ? Status.UNVERIFIED : Status.DIFFERENT, actual,
            actual == null ? error : "Negotiated output differs from target; no automatic rewrite");
    }
}
