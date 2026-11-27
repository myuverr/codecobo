// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.ui.management

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** The single metadata snapshot that Library and Settings render from. */
internal class ManagementSnapshot {
    var data by mutableStateOf(ManagementData(loading = true))
        private set

    fun publish(value: ManagementData) {
        data = value
    }
}
