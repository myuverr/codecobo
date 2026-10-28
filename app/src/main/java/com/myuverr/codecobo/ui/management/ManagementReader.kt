// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.ui.management

import android.companion.CompanionDeviceManager
import android.content.Context
import com.myuverr.codecobo.R
import com.myuverr.codecobo.core.DeviceProfile
import com.myuverr.codecobo.platform.*

/** Reads configuration and diagnostic metadata off the main thread. */
class ManagementReader(context: Context) {
    private val app = context.applicationContext

    fun read(): ManagementData {
        val store = ProfileStore(app)
        val profiles = store.list()
        var associationError: String? = null
        var associationObservation = "association_read=ok"
        val associations = try {
            val manager = app.getSystemService(CompanionDeviceManager::class.java)
            if (manager == null) {
                associationError = "Companion device service unavailable"
                associationObservation = "association_read=unavailable"
                emptyList()
            } else manager.myAssociations.map {
                OwnAssociation(it.id, ProfileStore.address(it.deviceMacAddress?.toString()), it.displayName?.toString())
            }
        } catch (error: RuntimeException) {
            associationError = "App associations unavailable: " + error.javaClass.simpleName
            associationObservation = "association_read=failed exception=" + error.javaClass.name
            emptyList()
        }
        return ManagementData(
            profiles = profiles,
            associations = associations,
            associationError = associationError,
            restorePaused = store.paused(),
            batteryExempt = BatterySettings.alreadyExempt(app),
            diagnostics = diagnostics(store, profiles, associationObservation, associations.size),
            version = app.packageManager.getPackageInfo(app.packageName, 0).versionName ?: app.getString(R.string.notice_version_unknown),
        )
    }

    private fun diagnostics(
        store: ProfileStore, profiles: List<DeviceProfile>, associationObservation: String, associationCount: Int,
    ): String {
        val report = buildString {
            append(RestoreTrace.dump(app))
            append("\n[Background observations]\n").append(BackgroundStatus.describe(app, store))
            append("\n[Associations]\n").append(associationObservation).append(" count=").append(associationCount)
            append("\n[Saved configurations]\n")
            for (profile in profiles) {
                append("device=").append(RestoreTrace.safe(profile.address()))
                append(" restore_enabled=").append(profile.enabled())
                append(" revision=").append(profile.revision())
                append(" target=").append(profile.request())
                append(' ').append(store.describeSession(profile.address()))
                append(" result=").append(if (store.result(profile.address()) != null) "available" else "none")
                append('\n')
            }
            append("Device names and localized result/error text are not exported; stored originals are retained.\n")
        }
        val marker = "\n[Report truncated: additional snapshot details omitted]\n"
        return if (report.length > 85_000) report.take(85_000 - marker.length) + marker else report
    }
}
