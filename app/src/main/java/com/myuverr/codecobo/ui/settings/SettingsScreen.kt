// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.myuverr.codecobo.R
import com.myuverr.codecobo.ui.components.*
import com.myuverr.codecobo.ui.theme.ThemePreference

@Composable
internal fun SettingsScreen(
    state: SettingsState,
    theme: ThemePreference,
    onNavigate: (SettingsRoute) -> Unit,
    onNotification: (Boolean) -> Unit,
    onTheme: () -> Unit,
    onLanguage: () -> Unit,
) {
    PageLayout(stringResource(R.string.home_nav_settings)) {
        Column(Modifier.fillMaxWidth()) {
            SettingsValueRow(stringResource(R.string.settings_access), stringResource(R.string.settings_access_hint),
                R.drawable.ic_choice_bluetooth, modeName(state), shapes = settingsGroupShapes(0, 2),
                onClick = { onNavigate(SettingsRoute.ACCESS) })
            Spacer(Modifier.height(2.dp))
            SettingRow(stringResource(R.string.settings_records), stringResource(R.string.settings_records_hint),
                R.drawable.ic_home_headphones, shapes = settingsGroupShapes(1, 2),
                onClick = { onNavigate(SettingsRoute.RECORDS) })
        }
        SectionLabel(stringResource(R.string.settings_background_section))
        Column(Modifier.fillMaxWidth()) {
            Box(Modifier.testTag("settings-notification-row")) {
                SettingRow(
                    stringResource(R.string.settings_status),
                    stringResource(when {
                        state.notification.pending() -> R.string.settings_status_pending
                        !state.notification.checked() -> R.string.settings_status_off
                        state.connection?.devices().isNullOrEmpty() -> R.string.settings_status_offline
                        else -> R.string.settings_status_online
                    }) + state.notification.error().let {
                        when {
                            it.isBlank() -> ""
                            state.connection?.bluetoothPermission() == false -> stringResource(R.string.notice_status_nearby_hint)
                            else -> stringResource(R.string.notice_status_notification_hint)
                        }
                    },
                    R.drawable.ic_settings_bell, shapes = settingsGroupShapes(0, 1),
                    trailing = {
                        PreferenceSwitch(state.notification.checked(), true, stringResource(R.string.settings_status),
                            onNotification, Modifier.testTag("status-notification"))
                    },
                )
            }
        }
        SectionLabel(stringResource(R.string.settings_appearance_section))
        Column(Modifier.fillMaxWidth()) {
            SettingsValueRow(stringResource(R.string.settings_theme), icon = R.drawable.ic_settings_moon,
                value = themeName(theme), shapes = settingsGroupShapes(0, 2), onClick = onTheme)
            Spacer(Modifier.height(2.dp))
            SettingsValueRow(stringResource(R.string.settings_language), icon = R.drawable.ic_settings_language,
                value = applicationLanguageName(state.applicationLocales), enabled = state.canChangeLanguage,
                shapes = settingsGroupShapes(1, 2), onClick = onLanguage)
        }
        SectionLabel(stringResource(R.string.settings_about_section))
        Column(Modifier.fillMaxWidth()) {
            SettingRow(stringResource(R.string.settings_diagnostics), stringResource(R.string.settings_diagnostics_hint),
                R.drawable.ic_settings_scroll, shapes = settingsGroupShapes(0, 2),
                onClick = { onNavigate(SettingsRoute.DIAGNOSTICS) })
            Spacer(Modifier.height(2.dp))
            SettingsValueRow(stringResource(R.string.settings_about, stringResource(R.string.app_name)), "",
                R.drawable.ic_settings_info, state.data.version, shapes = settingsGroupShapes(1, 2),
                onClick = { onNavigate(SettingsRoute.ABOUT) })
        }
    }
}

@Composable
internal fun modeName(state: SettingsState): String = stringResource(
    if (state.connection == null) R.string.settings_not_read
    else if (state.connection.shizukuMode()) R.string.access_mode_shizuku else R.string.access_mode_system,
)

@Composable
internal fun themeName(theme: ThemePreference): String = stringResource(when (theme) {
    ThemePreference.SYSTEM -> R.string.settings_theme_system
    ThemePreference.LIGHT -> R.string.settings_theme_light
    ThemePreference.DARK -> R.string.settings_theme_dark
})
