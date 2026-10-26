// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.platform

import android.companion.AssociationInfo
import android.companion.CompanionDeviceService
import android.companion.DevicePresenceEvent
import androidx.annotation.RequiresApi

/** Companion-device callbacks enqueue storage checks without blocking the service thread. */
@Suppress("OVERRIDE_DEPRECATION") // These callbacks remain required on API 33-35.
class DevicePresenceService : CompanionDeviceService() {
    private var lease: BluetoothRuntime.Lease? = null
    @Volatile private var closed = false
    private var lastGeneration = 0L

    override fun onCreate() {
        super.onCreate()
        lease = BluetoothRuntime.acquire(this) {
            val current = lease
            if (!closed && current != null) {
                val generation = current.repository().connectionGeneration()
                if (generation != lastGeneration) {
                    lastGeneration = generation
                    val store = ProfileStore(this)
                    store.execute {
                        val wanted = StatusNotifications.enabled(this) || store.active()
                        if (wanted && !closed && current.repository().connectionGeneration() == generation)
                            DeviceConnectionChecks.request(this, null, false, true, "companion_connection_change", null)
                    }
                }
            }
        }
        val current = requireNotNull(lease)
        lastGeneration = current.repository().connectionGeneration()
        current.repository().bind()
        ServiceDiagnostics.record(this, "companion_presence", "connection.presence_handler_created")
    }

    private fun event(id: Int, address: String?, appeared: Boolean) {
        if (closed) return
        RestoreTrace.event(this, "", "companion_presence", "connection.presence_received",
            "association=$id device=$address appeared=$appeared")
        DeviceConnectionChecks.associated(this, id, address, appeared)
    }

    override fun onDeviceAppeared(info: AssociationInfo) = event(info.id, null, true)
    override fun onDeviceDisappeared(info: AssociationInfo) = event(info.id, null, false)
    @Suppress("DEPRECATION")
    override fun onDeviceAppeared(address: String) = event(-1, address, true)
    @Suppress("DEPRECATION")
    override fun onDeviceDisappeared(address: String) = event(-1, address, false)

    @RequiresApi(36)
    override fun onDevicePresenceEvent(event: DevicePresenceEvent) {
        when (event.event) {
            DevicePresenceEvent.EVENT_BT_CONNECTED, DevicePresenceEvent.EVENT_BLE_APPEARED ->
                event(event.associationId, null, true)
            DevicePresenceEvent.EVENT_BT_DISCONNECTED, DevicePresenceEvent.EVENT_BLE_DISAPPEARED ->
                event(event.associationId, null, false)
            else -> RestoreTrace.event(this, "", "companion_presence", "connection.presence_ignored", "kind=${event.event}")
        }
    }

    override fun onDestroy() {
        closed = true
        lease?.close()
        ServiceDiagnostics.record(this, "companion_presence", "connection.presence_handler_destroyed")
        super.onDestroy()
    }
}
