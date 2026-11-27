// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.ui.theme

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.myuverr.codecobo.platform.RestoreTrace
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

enum class ThemePreference {
    SYSTEM, LIGHT, DARK;

    internal fun isDark(systemDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemDark
        LIGHT -> false
        DARK -> true
    }
}

/** Null until the first stored value arrives; callers fall back to the system scheme. */
internal val LocalThemePreference = staticCompositionLocalOf<ThemePreference?> {
    error("Theme preference requires CodecTheme")
}

private const val AppearanceStoreName = "appearance"
private const val ThemeKey = "theme"

private val Context.appearanceStore: DataStore<Preferences> by preferencesDataStore(name = AppearanceStoreName)

internal object AppearanceStore {
    private val themeKey = stringPreferencesKey(ThemeKey)

    fun theme(context: Context): Flow<ThemePreference> =
        context.applicationContext.appearanceStore.data
            .catch { error ->
                if (error !is IOException) throw error
                // An unreadable store must not block startup; keep the failure diagnosable.
                RestoreTrace.failure(context, "", "appearance", "preferences.read_failed", error)
                emit(emptyPreferences())
            }
            .map { preferences -> decode(preferences[themeKey]) }

    /** False when the choice could not be stored; the caller keeps showing the previous one. */
    suspend fun write(context: Context, preference: ThemePreference): Boolean = try {
        context.applicationContext.appearanceStore.edit { it[themeKey] = preference.name }
        true
    } catch (error: IOException) {
        RestoreTrace.failure(context, "", "appearance", "preferences.write_failed", error)
        false
    }

    internal fun decode(stored: String?): ThemePreference =
        ThemePreference.entries.firstOrNull { it.name == stored } ?: ThemePreference.SYSTEM
}
