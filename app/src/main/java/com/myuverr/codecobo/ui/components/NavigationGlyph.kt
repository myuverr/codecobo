// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource

/**
 * Dissolves a tab's outlined glyph into its filled twin and carries the tint along, so selection reads
 * as one move shared with the indicator and the label.
 */
@Composable
internal fun NavigationGlyph(outline: Int, filled: Int, selected: Boolean, tint: Color, modifier: Modifier) {
    // Shape changes are drawn as opacity, so the blend and the tint ride the effects springs; the
    // default speed keeps the dissolve readable.
    val fill by animateFloatAsState(
        if (selected) 1f else 0f,
        MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "navigation-fill",
    )
    val ink by animateColorAsState(tint, MaterialTheme.motionScheme.defaultEffectsSpec(), label = "navigation-tint")
    Box(modifier, contentAlignment = Alignment.Center) {
        GlyphLayer(outline, ink, 1f - fill)
        GlyphLayer(filled, ink, fill)
    }
}

@Composable
private fun GlyphLayer(icon: Int, tint: Color, opacity: Float) {
    Icon(painterResource(icon), null, Modifier.fillMaxSize(), tint = tint.copy(alpha = tint.alpha * opacity))
}
