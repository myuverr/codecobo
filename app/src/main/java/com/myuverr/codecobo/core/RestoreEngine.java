// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.core;
import java.util.List;
import java.util.function.BooleanSupplier;
/** Waits for readiness, applies once, then verifies. */
public final class RestoreEngine {
    public enum Status { MATCHED, VERIFIED, DIFFERENT, UNVERIFIED, CANCELLED, FAILED }
    public record Ready(CodecState actual, List<CodecCapability> capabilities) {}
    public record Result(Status status, CodecState actual, String detail) {}
    public interface Host extends ApplyEngine.Device {
        boolean associated() throws Exception;
        Ready snapshot() throws Exception;
    }
    private final ApplyEngine.Pause pause;
    public RestoreEngine(ApplyEngine.Pause pause) {
        this.pause = pause;
    }
    private static Result cancelled() {
        return new Result(
            Status.CANCELLED, null, "Connection, profile or operation is no longer current; restore stopped");
    }
    public Result restore(CodecRequest target, Host host, BooleanSupplier current) {
        try {
            if (!current.getAsBoolean())
                return cancelled();
            if (!host.associated())
                return new Result(Status.FAILED, null,
                    "No valid device association; open the app, connect and save. Association is never requested in the background.");
            Ready ready = null;
            String last = "A2DP is not ready";
            for (int i = 0; i < 20; i++) {
                if (!current.getAsBoolean())
                    return cancelled();
                try {
                    if (!host.connected())
                        throw new IllegalStateException("Waiting for the A2DP connection");
                    Ready value = host.snapshot();
                    if (value.actual() == null || value.capabilities() == null
                        || value.capabilities().isEmpty())
                        throw new IllegalStateException(
                            "Waiting for actual output and available capabilities");
                    ready = value;
                    break;
                } catch (SecurityException e) {
                    throw e;
                } catch (Exception e) {
                    last = e.toString();
                }
                if (i < 19)
                    pause.await();
            }
            if (!current.getAsBoolean())
                return cancelled();
            if (ready == null)
                return new Result(Status.FAILED, null, "Device readiness timed out: " + last);
            if (!CodecRules.valid(target, ready.capabilities()))
                return new Result(Status.FAILED, ready.actual(),
                    "Saved combination is unsupported; no automatic submission");
            if (CodecRules.matches(target, ready.actual()))
                return new Result(
                    Status.MATCHED, ready.actual(), "Actual output already matches; no duplicate submission");
            var guarded = new ApplyEngine.Device() {
                public boolean connected() throws Exception {
                    return current.getAsBoolean() && host.connected();
                }
                public void write(CodecRequest request) throws Exception {
                    if (!current.getAsBoolean())
                        throw new IllegalStateException("Restore is no longer current");
                    host.write(request);
                }
                public CodecState read() throws Exception {
                    return host.read();
                }
            };
            OperationGate gate = new OperationGate();
            long token = gate.issue();
            ApplyEngine.Result applied = new ApplyEngine(pause).apply(guarded, target, gate, token);
            if (!current.getAsBoolean())
                return cancelled();
            return new Result(Status.valueOf(applied.status().name()), applied.actual(), applied.detail());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return cancelled();
        } catch (Exception | LinkageError e) {
            return new Result(Status.FAILED, null, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }
}
