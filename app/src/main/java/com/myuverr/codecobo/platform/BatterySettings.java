// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.platform;
import android.app.Activity;
import android.content.*;
import android.net.Uri;
import android.os.PowerManager;
import android.provider.Settings;

public final class BatterySettings {
    private BatterySettings() {}
    public static boolean alreadyExempt(Context context) {
        try {
            var power = context.getSystemService(PowerManager.class);
            return power != null && power.isIgnoringBatteryOptimizations(context.getPackageName());
        } catch (RuntimeException e) {
            return false;
        }
    }
    public static String open(Activity activity) {
        Uri own = Uri.parse("package:" + activity.getPackageName());
        Intent list = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
        Intent request = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, own);
        Intent details = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, own);
        Intent[] candidates =
            alreadyExempt(activity) ? new Intent[] {list, details} : new Intent[] {request, list, details};
        String error = "";
        for (Intent intent : candidates) {
            try {
                activity.startActivity(intent);
                return "";
            } catch (RuntimeException e) {
                RestoreTrace.failure(activity, "", "battery_settings", "system.battery_settings_attempt_failed", e);
                error = e.toString();
            }
        }
        return error;
    }
}
