// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.ui.management

import com.myuverr.codecobo.core.DeviceProfile

data class OwnAssociation(val id: Int, val address: String?, val name: String?)

data class ManagementData(
    val profiles: List<DeviceProfile> = emptyList(),
    val associations: List<OwnAssociation> = emptyList(),
    val associationError: String? = null,
    val restorePaused: Boolean = false,
    val batteryExempt: Boolean? = null,
    val diagnostics: String = "",
    val version: String = "",
    val loading: Boolean = false,
    val readError: String? = null,
)
