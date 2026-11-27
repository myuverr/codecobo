// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.platform

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import androidx.core.net.toUri

object BatterySettings {
    fun alreadyExempt(context: Context): Boolean? = try {
        context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName)
    } catch (_: RuntimeException) {
        null
    }

    /** Opens the best available exemption screen; an empty result means one of them opened. */
    fun open(activity: Activity): String {
        val own = ("package:" + activity.packageName).toUri()
        val list = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        val request = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, own)
        val details = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, own)
        val candidates = if (alreadyExempt(activity) == true) listOf(list, details)
        else listOf(request, list, details)
        var error = ""
        for (intent in candidates) {
            try {
                activity.startActivity(intent)
                return ""
            } catch (failure: RuntimeException) {
                RestoreTrace.failure(activity, "", "battery_settings", "system.battery_settings_attempt_failed", failure)
                error = failure.toString()
            }
        }
        return error
    }
}
