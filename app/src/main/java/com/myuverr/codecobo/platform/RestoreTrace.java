// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.platform;

import android.content.Context;
import android.os.Build;
import android.os.Process;
import android.os.SystemClock;
import android.util.Log;
import com.myuverr.codecobo.R;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/** Bounded, best-effort observations; never a source of restore eligibility. */
public final class RestoreTrace {
    private static final int MAX_LINE = DiagnosticJournal.MAX_LINE;
    private static final DateTimeFormatter UTC =
        DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT).withZone(ZoneOffset.UTC);
    private static final Pattern MAC = Pattern.compile("(?i)[0-9a-f]{2}(?:[:-][0-9a-f]{2}){5}");

    private RestoreTrace() {}
    public static String id() {
        return UUID.randomUUID().toString().substring(0, 18);
    }

    /** Escapes non-ASCII log fields and redacts Bluetooth addresses. */
    public static String safe(String value) {
        if (value == null)
            return "-";
        boolean shortened = value.length() > 2048;
        String bounded = shortened ? value.substring(0, 2048) : value;
        var matcher = MAC.matcher(bounded);
        StringBuffer redacted = new StringBuffer();
        while (matcher.find())
            matcher.appendReplacement(redacted,
                "**:**:**:**:" + matcher.group().substring(12).replace('-', ':').toUpperCase(Locale.ROOT));
        matcher.appendTail(redacted);
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < redacted.length(); i++) {
            char c = redacted.charAt(i);
            if (c >= 32 && c < 127)
                text.append(c);
            else
                text.append(String.format(Locale.ROOT, "\\u%04X", (int) c));
        }
        if (shortened)
            text.append(" [value truncated]");
        return text.toString();
    }
    private static String field(String value) {
        String text = safe(value);
        return text.isEmpty() ? "-" : text.replace(' ', '_');
    }

    public static void event(Context context, String id, String source, String event, String detail) {
        append(context, id, source, event, detail, "INFO");
    }

    public static void failure(Context context, String id, String source, String event, Throwable error) {
        try {
            StringBuilder detail = new StringBuilder("exception=").append(error.getClass().getName());
            int frames = 0;
            for (StackTraceElement frame : error.getStackTrace()) {
                if (!frame.getClassName().startsWith("com.myuverr.codecobo."))
                    continue;
                detail.append(" at=")
                    .append(frame.getClassName())
                    .append('.')
                    .append(frame.getMethodName())
                    .append(':')
                    .append(frame.getLineNumber());
                if (++frames == 3)
                    break;
            }
            append(context, id, source, event, detail.toString(), "ERROR");
        } catch (RuntimeException ignored) {
            Log.w("RestoreTrace", "Diagnostic event unavailable");
        }
    }

    private static void append(
        Context context, String id, String source, String event, String detail, String level) {
        try {
            String line = UTC.format(Instant.now()) + " elapsed=" + SystemClock.elapsedRealtime()
                + " pid=" + Process.myPid() + " level=" + level + " id=" + field(id)
                + " source=" + field(source) + " event=" + field(event) + " " + safe(detail);
            String marker = " truncated=true";
            if (line.length() > MAX_LINE)
                line = line.substring(0, MAX_LINE - marker.length()) + marker;
            DiagnosticJournal.append(context, line);
        } catch (RuntimeException ignored) {
            Log.w("RestoreTrace", "Diagnostic event unavailable");
        }
    }

    public static String dump(Context context) {
        try {
            var info = context.getPackageManager().getPackageInfo(
                context.getPackageName(), android.content.pm.PackageManager.PackageInfoFlags.of(0));
            String header = safe(context.getString(R.string.app_name)) + " diagnostics\n"
                + "package=" + safe(context.getPackageName()) + " version=" + safe(info.versionName)
                + " code=" + info.getLongVersionCode() + " snapshot_utc=" + UTC.format(Instant.now()) + "\n"
                + "manufacturer=" + safe(Build.MANUFACTURER) + " model=" + safe(Build.MODEL)
                + " sdk=" + Build.VERSION.SDK_INT + " build=" + safe(Build.DISPLAY) + "\n"
                + "Observations are not proof of applied settings. Missing logs do not prove missing system events.\n"
                + "Async persistence may lose the final events on abrupt process exit. Different pid means a different process.\n";
            var snapshot = DiagnosticJournal.snapshot(context);
            return header + "retention_entries=" + DiagnosticJournal.MAX_ENTRIES
                + " max_line_chars=" + MAX_LINE + " dropped_events=" + snapshot.getDropped()
                + " queue_dropped_events=" + snapshot.getQueueDropped()
                + " storage_failures=" + snapshot.getFailures() + "\n\n[Events]\n"
                + (snapshot.getLines().isEmpty() ? "No events.\n"
                                                 : String.join("\n", snapshot.getLines()) + "\n");
        } catch (RuntimeException | android.content.pm.PackageManager.NameNotFoundException ignored) {
            return "log_read=unavailable (diagnostic failure does not mean restoration stopped)\n";
        }
    }
    public static void clear(Context context) {
        DiagnosticJournal.clear(context);
    }
}
