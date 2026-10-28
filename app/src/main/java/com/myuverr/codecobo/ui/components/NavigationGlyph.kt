// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.painterResource
import com.airbnb.lottie.AsyncUpdates
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.compose.*

/** Animates project-authored keyframes derived from Phosphor icon paths. */
@Composable
internal fun NavigationGlyph(outline: Int, filled: Int, motion: Int, selected: Boolean, tint: Color, modifier: Modifier) {
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(motion))
    // Show the static endpoint until decoding completes.
    if (composition == null) {
        Icon(painterResource(if (selected) filled else outline), null, modifier, tint = tint)
    } else {
        // Initialize after decoding to avoid replaying a partial entrance.
        val progress by animateFloatAsState(
            if (selected) 1f else 0f,
            animationSpec = tween(100, easing = FastOutSlowInEasing),
            label = "navigation-glyph",
        )
        val properties = rememberLottieDynamicProperties(
            rememberLottieDynamicProperty(LottieProperty.COLOR, tint.toArgb(), "**"),
        )
        LottieAnimation(composition, progress = { progress }, modifier = modifier,
            dynamicProperties = properties, asyncUpdates = AsyncUpdates.DISABLED)
    }
}
