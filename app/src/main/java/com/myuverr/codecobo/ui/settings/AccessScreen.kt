// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.myuverr.codecobo.R
import com.myuverr.codecobo.controller.ScreenState
import com.myuverr.codecobo.ui.components.*

@Composable
internal fun AccessScreen(
    state: SettingsState,
    onBack: () -> Unit,
    onNearby: () -> Unit,
    onBattery: () -> Unit,
    onMode: () -> Unit,
) {
    PageLayout(stringResource(R.string.settings_access), onBack = onBack) {
        Column(Modifier.fillMaxWidth()) {
            SettingsValueRow(stringResource(R.string.settings_nearby), stringResource(R.string.settings_nearby_hint),
                R.drawable.ic_choice_bluetooth,
                stringResource(when {
                    state.connection == null -> R.string.settings_not_read
                    state.connection.bluetoothPermission() -> R.string.settings_allowed
                    else -> R.string.settings_not_allowed
                }), onClick = onNearby, shapes = settingsGroupShapes(0, 2))
            Spacer(Modifier.height(2.dp))
            SettingsValueRow(stringResource(R.string.settings_battery), stringResource(R.string.settings_battery_hint),
                R.drawable.ic_settings_info,
                stringResource(when (state.data.batteryExempt) {
                    true -> R.string.settings_battery_on
                    false -> R.string.settings_battery_off
                    null -> R.string.settings_unreadable
                }), onClick = onBattery, shapes = settingsGroupShapes(1, 2))
        }
        SectionLabel(stringResource(R.string.settings_mode))
        Column(Modifier.fillMaxWidth()) {
            SettingsValueRow(stringResource(R.string.settings_mode), stringResource(R.string.settings_mode_hint),
                R.drawable.ic_home_target, modeName(state), onClick = onMode, shapes = settingsGroupShapes(0, 1))
        }
        if (state.connection?.accessMode() == ScreenState.AccessMode.AUTHORIZATION_FAILED)
            PageNote(stringResource(R.string.notice_access_request_failed), Modifier.padding(top = 9.dp))
    }
}
