// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.ui.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext

enum class ThemePreference {
    SYSTEM, LIGHT, DARK;

    internal fun isDark(systemDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemDark
        LIGHT -> false
        DARK -> true
    }
}

object AppearanceStore {
    fun read(context: Context): ThemePreference = try {
        ThemePreference.valueOf(preferences(context).getString("theme", "SYSTEM") ?: "SYSTEM")
    } catch (_: IllegalArgumentException) {
        ThemePreference.SYSTEM
    } catch (_: ClassCastException) {
        ThemePreference.SYSTEM
    }

    fun write(context: Context, preference: ThemePreference) {
        preferences(context).edit { putString("theme", preference.name) }
    }

    internal fun preferences(context: Context) = context.applicationContext.getSharedPreferences("appearance", Context.MODE_PRIVATE)
}

@Composable
internal fun rememberThemePreference(): ThemePreference {
    val context = LocalContext.current
    var preference by remember(context) { mutableStateOf(AppearanceStore.read(context)) }
    DisposableEffect(context) {
        val preferences = AppearanceStore.preferences(context)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "theme") preference = AppearanceStore.read(context)
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return preference
}

@Composable
internal fun preferredDarkTheme(): Boolean = rememberThemePreference().isDark(isSystemInDarkTheme())
