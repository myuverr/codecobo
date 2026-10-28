// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp

internal enum class WindowTier(val textScale: Float) {
    COMPACT(1f), MEDIUM(1.10f), EXPANDED(1.20f);

    companion object {
        fun forWindow(widthDp: Float, heightDp: Float): WindowTier = when {
            !widthDp.isFinite() || !heightDp.isFinite() || widthDp < 600f || heightDp < 480f -> COMPACT
            widthDp < 840f -> MEDIUM
            else -> EXPANDED
        }
    }
}

/** Window size scaling; Compose applies the system font scale separately. */
@Immutable
internal data class AppTypeScale(val tier: WindowTier) {
    val factor get() = tier.textScale
    fun sp(base: Float): TextUnit = (base * factor).sp
    val codec get() = sp(20f)
    val rate get() = sp(22f)
    val variant get() = sp(14f)
    val unit get() = sp(12f)
    val gutter: Dp get() = when (tier) { WindowTier.COMPACT -> 20.dp; WindowTier.MEDIUM -> 24.dp; WindowTier.EXPANDED -> 32.dp }
    val homeMaxWidth: Dp get() = if (tier == WindowTier.MEDIUM) 800.dp else 1180.dp
    val pageMaxWidth: Dp get() = 860.dp
    val offlineMaxWidth: Dp get() = 720.dp

    private fun TextStyle.scaled() = copy(
        fontSize = if (fontSize.isSpecified) fontSize * factor else fontSize,
        lineHeight = if (lineHeight.isSpecified) lineHeight * factor else lineHeight,
    )

    /** Nested themes must start from unscaled defaults. */
    fun materialTypography(): Typography {
        val base = Typography()
        return base.copy(
            displayLarge = base.displayLarge.scaled(),
            displayLargeEmphasized = base.displayLargeEmphasized.scaled(),
            displayMedium = base.displayMedium.scaled(),
            displayMediumEmphasized = base.displayMediumEmphasized.scaled(),
            displaySmall = base.displaySmall.scaled(),
            displaySmallEmphasized = base.displaySmallEmphasized.scaled(),
            headlineLarge = base.headlineLarge.scaled(),
            headlineLargeEmphasized = base.headlineLargeEmphasized.scaled(),
            headlineMedium = base.headlineMedium.scaled(),
            headlineMediumEmphasized = base.headlineMediumEmphasized.scaled(),
            headlineSmall = base.headlineSmall.scaled(),
            headlineSmallEmphasized = base.headlineSmallEmphasized.scaled(),
            titleLarge = base.titleLarge.scaled(),
            titleLargeEmphasized = base.titleLargeEmphasized.scaled(),
            titleMedium = base.titleMedium.scaled(),
            titleMediumEmphasized = base.titleMediumEmphasized.scaled(),
            titleSmall = base.titleSmall.scaled(),
            titleSmallEmphasized = base.titleSmallEmphasized.scaled(),
            bodyLarge = base.bodyLarge.scaled(),
            bodyLargeEmphasized = base.bodyLargeEmphasized.scaled(),
            bodyMedium = base.bodyMedium.scaled(),
            bodyMediumEmphasized = base.bodyMediumEmphasized.scaled(),
            bodySmall = base.bodySmall.scaled(),
            bodySmallEmphasized = base.bodySmallEmphasized.scaled(),
            labelLarge = base.labelLarge.scaled(),
            labelLargeEmphasized = base.labelLargeEmphasized.scaled(),
            labelMedium = base.labelMedium.scaled(),
            labelMediumEmphasized = base.labelMediumEmphasized.scaled(),
            labelSmall = base.labelSmall.scaled(),
            labelSmallEmphasized = base.labelSmallEmphasized.scaled(),
        )
    }
}

internal val LocalAppTypeScale = staticCompositionLocalOf { AppTypeScale(WindowTier.COMPACT) }

internal val Int.adaptiveSp: TextUnit
    @Composable @ReadOnlyComposable get() = LocalAppTypeScale.current.sp(toFloat())

internal val Float.adaptiveSp: TextUnit
    @Composable @ReadOnlyComposable get() = LocalAppTypeScale.current.sp(this)
