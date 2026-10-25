// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.core;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Runs fixed argv commands with bounded output. */
public final class CommandRunner {
    public record Result(int exitCode, boolean timedOut, String output) {}
    private CommandRunner() {}
    public static Result run(List<String> arguments, long timeoutMillis)
        throws IOException, InterruptedException {
        Process process = new ProcessBuilder(arguments).redirectErrorStream(true).start();
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        Thread reader = new Thread(() -> {
            try (InputStream input = process.getInputStream()) {
                byte[] buffer = new byte[1024];
                int n;
                while ((n = input.read(buffer)) != -1) {
                    synchronized (captured) {
                        int keep = Math.min(n, 8192 - captured.size());
                        if (keep > 0)
                            captured.write(buffer, 0, keep);
                    }
                }
            } catch (IOException ignored) { /* process timeout closes its stream */
            }
        }, "association-output");
        reader.setDaemon(true);
        reader.start();
        try {
            boolean finished = process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                process.waitFor(500, TimeUnit.MILLISECONDS);
            }
            reader.join(1000);
            String output;
            synchronized (captured) {
                output = captured.toString(StandardCharsets.UTF_8.name());
            }
            return new Result(finished ? process.exitValue() : -1, !finished, output);
        } finally {
            if (process.isAlive())
                process.destroyForcibly();
            process.getInputStream().close();
            process.getOutputStream().close();
            process.getErrorStream().close();
        }
    }
}
