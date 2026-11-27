// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.State
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.myuverr.codecobo.ui.capsuleGeometry
import com.myuverr.codecobo.ui.components.FloatingOverlayState
import com.myuverr.codecobo.ui.components.LocalFloatingNavigationClearance

internal val LocalPageSurface = staticCompositionLocalOf<State<Color>> { error("Page surface requires CodecTheme") }

@Composable
internal fun CodecTheme(
    navigationOverlay: FloatingOverlayState? = null,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    // The single observation point; Settings renders the same value through LocalThemePreference.
    val preference by remember(context) { AppearanceStore.theme(context) }.collectAsState(initial = null)
    val systemDark = isSystemInDarkTheme()
    val darkTheme = preference?.isDark(systemDark) ?: systemDark
    val colors = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    val motion = MotionScheme.expressive()
    // Animate page/window paint, not the scheme consumed by native color animations.
    val surface = animateColorAsState(colors.surface, motion.defaultEffectsSpec(), label = "themeSurface")
    SystemChrome(darkTheme, surface)
    val workbench = WorkbenchColors.fromSystem(context, colors, darkTheme)
    val configuration = LocalConfiguration.current
    val window = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current.density
    val width = if (window.width > 0) window.width / density else configuration.screenWidthDp.toFloat()
    val height = if (window.height > 0) window.height / density else configuration.screenHeightDp.toFloat()
    val tier = WindowTier.forWindow(width, height)
    val type = remember(tier) { AppTypeScale(tier) }
    val typography = remember(type) { type.materialTypography() }
    val navigationClearance = if (navigationOverlay == null) 0.dp else with(LocalDensity.current) {
        // Navigation may not be measured on the first frame; use the height it will settle on.
        (if (navigationOverlay.navigationHeightPixels > 0) navigationOverlay.navigationHeightPixels.toDp()
        else capsuleGeometry(typography.labelMedium.lineHeight.toDp(),
            LocalMinimumInteractiveComponentSize.current).height) + 12.dp
    }
    CompositionLocalProvider(LocalWorkbenchColors provides workbench, LocalAppTypeScale provides type,
        LocalPageSurface provides surface, LocalThemePreference provides preference,
        LocalFloatingNavigationClearance provides navigationClearance) {
        MaterialExpressiveTheme(colorScheme = colors, typography = typography, motionScheme = motion, content = content)
    }
}
