// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.platform

import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.myuverr.codecobo.core.WriteArbiter

/** The receipt deadline covers queued database work and connection discovery. */
class RestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        val pending = goAsync()
        val receipt = DeviceConnectionChecks.Completion(context) { pending?.finish() }
        val store = ProfileStore(context)
        val id = RestoreTrace.id()
        val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1)
        if (intent.action == BluetoothAdapter.ACTION_STATE_CHANGED &&
            (state == BluetoothAdapter.STATE_OFF || state == BluetoothAdapter.STATE_TURNING_OFF)) {
            WriteArbiter.SHARED.cancelAutomatic()
        }
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            intent.action == BluetoothAdapter.ACTION_STATE_CHANGED && state in
                setOf(BluetoothAdapter.STATE_OFF, BluetoothAdapter.STATE_TURNING_OFF, BluetoothAdapter.STATE_TURNING_ON, BluetoothAdapter.STATE_ON)) {
            // Foreground startup must not wait behind the profile database queue.
            StatusNotifications.ensureRunning(context)
        }
        val accepted = store.execute {
            var delegated = false
            try {
                if (!receipt.expired) delegated = receive(context, intent, store, id, receipt)
            } catch (error: RuntimeException) {
                RestoreTrace.failure(context, id, "system_broadcast", "system.event_failed", error)
                WriteArbiter.SHARED.cancelAutomatic()
                store.backgroundError("Connection event failed; restore not started: " + error)
            } finally {
                RestoreTrace.event(context, id, "system_broadcast", "system.handler_finished",
                    "receipt_delegated=$delegated")
                if (!delegated) receipt.run()
            }
        }
        if (!accepted) receipt.run()
    }

    private fun receive(
        context: Context, intent: Intent, store: ProfileStore, id: String,
        receipt: DeviceConnectionChecks.Completion,
    ): Boolean {
        when (intent.action) {
            BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED -> {
                val device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java) ?: return false
                val state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1)
                if (state !in BluetoothProfile.STATE_DISCONNECTED..BluetoothProfile.STATE_DISCONNECTING) return false
                val address = ProfileStore.address(device.address) ?: return false
                RestoreTrace.event(context, id, "a2dp_broadcast", "connection.a2dp_event", "device=$address state=$state")
                val session = store.connection(address, state == BluetoothProfile.STATE_CONNECTED)
                RestoreTrace.event(context, id, "a2dp_broadcast", "connection.session_recorded", "session=$session")
                val profile = store.get(address)
                if (receipt.expired) return false
                if (state == BluetoothProfile.STATE_CONNECTED && profile?.enabled() == true)
                    RestoreService.requestCheck(context, address, "a2dp_broadcast", id, session) { !receipt.expired }
                else RestoreTrace.event(context, id, "a2dp_broadcast", "restore.not_requested",
                    "reason=not_connected_or_profile_disabled")
                if (StatusNotifications.enabled(context) &&
                    (state == BluetoothProfile.STATE_CONNECTED || state == BluetoothProfile.STATE_DISCONNECTED)) {
                    DeviceConnectionChecks.requestWithReceipt(context, address, state == BluetoothProfile.STATE_CONNECTED,
                        false, "a2dp_status_broadcast", receipt)
                    return true
                }
            }
            BluetoothAdapter.ACTION_STATE_CHANGED -> {
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1)
                RestoreTrace.event(context, id, "bluetooth_broadcast", "bluetooth.state_changed", "state=$state")
                if (state == BluetoothAdapter.STATE_OFF || state == BluetoothAdapter.STATE_TURNING_OFF) {
                    store.resetConnections()
                } else if (state == BluetoothAdapter.STATE_ON && !receipt.expired) {
                    RestoreService.requestCheck(context, null, "bluetooth_enabled_broadcast", id, -1) { !receipt.expired }
                }
            }
            Intent.ACTION_BOOT_COMPLETED -> {
                RestoreTrace.event(context, id, "boot_broadcast", "system.event_received", "")
                store.resetConnections()
                DevicePresence.sync(context, true)
                if (!receipt.expired) RestoreService.requestCheck(context, null, "boot_broadcast", id, -1) { !receipt.expired }
            }
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                RestoreTrace.event(context, id, "package_replaced_broadcast", "system.event_received", "")
                DevicePresence.sync(context, true)
                if (!receipt.expired) RestoreService.requestCheck(context, null, "package_replaced_broadcast", id, -1) { !receipt.expired }
            }
        }
        return false
    }
}
