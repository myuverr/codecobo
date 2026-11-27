// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class AppearanceStoreTest {
    @Test
    fun storedNamesDecodeToTheirPreference() {
        assertEquals(ThemePreference.SYSTEM, AppearanceStore.decode("SYSTEM"))
        assertEquals(ThemePreference.LIGHT, AppearanceStore.decode("LIGHT"))
        assertEquals(ThemePreference.DARK, AppearanceStore.decode("DARK"))
    }

    @Test
    fun missingOrUnknownValuesFollowTheSystem() {
        assertEquals(ThemePreference.SYSTEM, AppearanceStore.decode(null))
        assertEquals(ThemePreference.SYSTEM, AppearanceStore.decode(""))
        assertEquals(ThemePreference.SYSTEM, AppearanceStore.decode("night"))
    }

    @Test
    fun darkSelectionIgnoresTheSystemScheme() {
        assertEquals(true, ThemePreference.DARK.isDark(false))
        assertEquals(false, ThemePreference.LIGHT.isDark(true))
        assertEquals(true, ThemePreference.SYSTEM.isDark(true))
        assertEquals(false, ThemePreference.SYSTEM.isDark(false))
    }
}
