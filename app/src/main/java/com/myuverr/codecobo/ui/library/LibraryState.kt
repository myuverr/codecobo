// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.ui.library

import com.myuverr.codecobo.controller.ScreenState
import com.myuverr.codecobo.core.DeviceProfile
import com.myuverr.codecobo.platform.BluetoothRepository.Snapshot
import com.myuverr.codecobo.ui.management.*

data class DeviceKey(val address: String?, val associationId: Int? = null)

internal data class DeviceDetail(
    val key: DeviceKey,
    val name: String?,
    val saved: DeviceProfile?,
    val connected: Boolean,
    val actual: Snapshot?,
)

internal data class LibraryState(
    val connection: ScreenState? = null,
    val data: ManagementData = ManagementData(loading = true),
) {
    fun detail(key: DeviceKey): DeviceDetail? {
        val saved = data.profiles.find { sameAddress(it.address(), key.address) }
        val association = data.associations.find {
            if (key.associationId != null) it.id == key.associationId else sameAddress(it.address, key.address)
        }
        val device = connection?.devices()?.find { sameAddress(it.address(), key.address) }
        if (saved == null && association == null && device == null) return null
        return DeviceDetail(
            key, saved?.name() ?: device?.name() ?: association?.name?.takeIf { it.isNotBlank() },
            saved, device != null,
            connection?.snapshot()?.takeIf { device != null && sameAddress(connection.selectedAddress(), key.address) },
        )
    }
}

internal fun sameAddress(a: String?, b: String?) = a != null && b != null && a.equals(b, ignoreCase = true)
