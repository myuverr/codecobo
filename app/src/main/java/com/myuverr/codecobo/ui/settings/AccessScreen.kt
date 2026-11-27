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
    batteryExempt: Boolean?,
    onBack: () -> Unit,
    onNearby: () -> Unit,
    onBattery: () -> Unit,
    onMode: () -> Unit,
) {
    PageLayout(stringResource(R.string.settings_access), onBack = onBack) {
        SettingsGroup(
            listOf(
                SettingsItem(
                    title = stringResource(R.string.settings_nearby),
                    description = stringResource(R.string.settings_nearby_hint),
                    icon = R.drawable.ic_settings_nearby,
                    value = nearbyStatus(state),
                    onClick = onNearby,
                ),
                SettingsItem(
                    title = stringResource(R.string.settings_battery),
                    description = stringResource(R.string.settings_battery_hint),
                    icon = R.drawable.ic_settings_battery,
                    value = batteryStatus(batteryExempt),
                    onClick = onBattery,
                ),
                SettingsItem(
                    title = stringResource(R.string.settings_mode),
                    icon = R.drawable.ic_settings_bluetooth_connected,
                    value = modeName(state),
                    onClick = onMode,
                ),
            ),
        )
        if (state.connection?.accessMode() == ScreenState.AccessMode.AUTHORIZATION_FAILED) {
            PageNote(stringResource(R.string.notice_access_request_failed), Modifier.padding(top = 9.dp))
        }
    }
}

@Composable
private fun nearbyStatus(state: SettingsState): String = stringResource(
    when {
        state.connection == null -> R.string.settings_not_read
        state.connection.bluetoothPermission() -> R.string.settings_allowed
        else -> R.string.settings_not_allowed
    },
)

@Composable
private fun batteryStatus(exempt: Boolean?): String = stringResource(
    when (exempt) {
        true -> R.string.settings_battery_on
        false -> R.string.settings_battery_off
        null -> R.string.settings_unreadable
    },
)
