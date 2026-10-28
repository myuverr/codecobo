// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.ui.theme

import android.content.Context
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
internal data class WorkbenchColors(val tint: Color, val onTint: Color, val accent: Color) {
    companion object {
        // Android palette tones: _100=T90, _200=T80, _700=T30, _800=T20.
        fun fromSystem(context: Context, scheme: ColorScheme, dark: Boolean) = WorkbenchColors(
            Color(context.getColor(if (dark) android.R.color.system_accent1_800 else android.R.color.system_accent1_100)),
            scheme.onSurface,
            Color(context.getColor(if (dark) android.R.color.system_accent1_700 else android.R.color.system_accent1_200)),
        )
    }
}

internal val LocalWorkbenchColors = staticCompositionLocalOf<WorkbenchColors> {
    error("Workbench colors require CodecTheme")
}
