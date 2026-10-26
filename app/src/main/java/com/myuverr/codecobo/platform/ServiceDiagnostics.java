// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.platform;
import android.app.*;
/** Records current process state and the latest exit reason. */
final class ServiceDiagnostics {
    private ServiceDiagnostics() {}
    static void record(Service service, String source, String stage) {
        try {
            var info = new ActivityManager.RunningAppProcessInfo();
            ActivityManager.getMyMemoryState(info);
            String detail = "type=" + service.getForegroundServiceType() + " importance=" + info.importance;
            var manager = service.getSystemService(ActivityManager.class);
            var exits = manager == null
                ? java.util.List.<ApplicationExitInfo>of()
                : manager.getHistoricalProcessExitReasons(service.getPackageName(), 0, 1);
            if (!exits.isEmpty())
                detail += " previousExitReason=" + exits.get(0).getReason()
                    + " previousExitTime=" + exits.get(0).getTimestamp();
            RestoreTrace.event(service, "", source, stage, detail);
        } catch (RuntimeException | LinkageError e) {
            RestoreTrace.failure(service, "", source, "process.sample_failed", e);
        }
    }
}
