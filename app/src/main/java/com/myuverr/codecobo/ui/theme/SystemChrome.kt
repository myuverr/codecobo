// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.os.Build
import android.view.WindowInsetsController
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

fun prepareWindowTheme(activity: Activity) {
    WindowCompat.setDecorFitsSystemWindows(activity.window, false)
    val systemDark = activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
        Configuration.UI_MODE_NIGHT_YES
    val dark = AppearanceStore.read(activity).isDark(systemDark)
    val colors = if (dark) dynamicDarkColorScheme(activity) else dynamicLightColorScheme(activity)
    // A recreated window can draw before any ComposeView is attached.
    applyWindowTheme(activity, dark, colors.surface)
}

@Composable
internal fun SystemChrome(dark: Boolean, surface: State<Color>) {
    val view = LocalView.current
    val activity = view.context.activity() ?: return
    if (view.isInEditMode) return
    val color = surface.value
    SideEffect { applyWindowTheme(activity, dark, color) }
}

@Suppress("DEPRECATION")
private fun applyWindowTheme(activity: Activity, dark: Boolean, surface: Color) {
    val window = activity.window
    window.decorView.setBackgroundColor(surface.toArgb())
    val mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
        WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
    window.insetsController?.setSystemBarsAppearance(if (dark) 0 else mask, mask)
    if (Build.VERSION.SDK_INT < 35) {
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = surface.toArgb()
    }
}

private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> if (baseContext !== this) baseContext.activity() else null
    else -> null
}
