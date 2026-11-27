// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.myuverr.codecobo.R
import com.myuverr.codecobo.ui.components.*
import com.myuverr.codecobo.ui.theme.ThemePreference

@Composable
internal fun SettingsScreen(
    state: SettingsState,
    version: String,
    theme: ThemePreference?,
    onNavigate: (SettingsRoute) -> Unit,
    onNotification: (Boolean) -> Unit,
    onTheme: () -> Unit,
    onLanguage: () -> Unit,
) {
    PageLayout(stringResource(R.string.home_nav_settings)) {
        SettingsGroup(
            listOf(
                SettingsItem(
                    title = stringResource(R.string.settings_access),
                    icon = R.drawable.ic_settings_permissions,
                    value = modeName(state),
                    onClick = { onNavigate(SettingsRoute.ACCESS) },
                ),
                SettingsItem(
                    title = stringResource(R.string.settings_records),
                    icon = R.drawable.ic_home_headphones,
                    onClick = { onNavigate(SettingsRoute.RECORDS) },
                ),
            ),
        )
        SectionLabel(stringResource(R.string.settings_background_section))
        SettingsGroup(
            listOf(
                SettingsItem(
                    title = stringResource(R.string.settings_status),
                    icon = R.drawable.ic_settings_bell,
                    modifier = Modifier.testTag("settings-notification-row"),
                    trailing = {
                        PreferenceSwitch(
                            checked = state.notification.checked,
                            enabled = true,
                            label = stringResource(R.string.settings_status),
                            onChanged = onNotification,
                            modifier = Modifier.testTag("status-notification"),
                        )
                    },
                ),
            ),
        )
        SectionLabel(stringResource(R.string.settings_appearance_section))
        SettingsGroup(
            listOf(
                SettingsItem(
                    title = stringResource(R.string.settings_theme),
                    icon = themeIcon(theme),
                    value = theme?.let { themeName(it) } ?: stringResource(R.string.settings_not_read),
                    onClick = onTheme,
                ),
                SettingsItem(
                    title = stringResource(R.string.settings_language),
                    icon = R.drawable.ic_settings_language,
                    value = applicationLanguageName(state.applicationLocales),
                    enabled = state.canChangeLanguage,
                    onClick = onLanguage,
                ),
            ),
        )
        SectionLabel(stringResource(R.string.settings_about_section))
        SettingsGroup(
            listOf(
                SettingsItem(
                    title = stringResource(R.string.settings_diagnostics),
                    icon = R.drawable.ic_settings_scroll,
                    onClick = { onNavigate(SettingsRoute.DIAGNOSTICS) },
                ),
                SettingsItem(
                    title = stringResource(R.string.settings_about, stringResource(R.string.app_name)),
                    icon = R.drawable.ic_settings_info,
                    value = version,
                    onClick = { onNavigate(SettingsRoute.ABOUT) },
                ),
            ),
        )
    }
}

/** Follows the selected theme so the row reads without its value. */
private fun themeIcon(theme: ThemePreference?): Int = when (theme) {
    ThemePreference.SYSTEM, null -> R.drawable.ic_theme_system
    ThemePreference.LIGHT -> R.drawable.ic_theme_light
    ThemePreference.DARK -> R.drawable.ic_theme_dark
}

@Composable
internal fun modeName(state: SettingsState): String = stringResource(
    if (state.connection == null) R.string.settings_not_read
    else if (state.connection.shizukuMode()) R.string.access_mode_shizuku else R.string.access_mode_system,
)

@Composable
internal fun themeName(theme: ThemePreference): String = stringResource(
    when (theme) {
        ThemePreference.SYSTEM -> R.string.settings_theme_system
        ThemePreference.LIGHT -> R.string.settings_theme_light
        ThemePreference.DARK -> R.string.settings_theme_dark
    },
)
