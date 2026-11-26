// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.ui.home

import androidx.compose.runtime.Immutable

@Immutable
internal class HomeActions(
    val onDeviceSelected: (String) -> Unit,
    val onParameterSelected: (ParameterKind, Int) -> Unit,
    val onOpenBluetooth: () -> Unit,
    val onOpenSettings: () -> Unit,
    val onRestoreChanged: (Boolean) -> Unit,
)
