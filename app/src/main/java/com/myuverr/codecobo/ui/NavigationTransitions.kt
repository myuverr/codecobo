// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.Scene
import androidx.navigationevent.NavigationEvent
import kotlin.math.roundToInt

internal const val PreviewFraction = 0.4f
private const val PredictiveDuration = 400
private const val UnderlayAlpha = 0.8f
private val ChildEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

internal data class BackSwipe(val edge: Int, val releaseFraction: Float? = null)

@Composable
internal fun Transition<Scene<NavKey>>.AnimatedChildContent(swipe: BackSwipe?, modifier: Modifier) {
    val direction = if (LocalLayoutDirection.current == LayoutDirection.Ltr) 1 else -1
    AnimatedContent(
        modifier = modifier,
        contentKey = { it.key },
        transitionSpec = {
            val depth = targetState.previousEntries.size
            val motion = when {
                swipe != null -> {
                    val enter = fadeIn(keyframes {
                        durationMillis = PredictiveDuration
                        UnderlayAlpha at (PreviewFraction * PredictiveDuration).roundToInt()
                    }, initialAlpha = UnderlayAlpha)
                    enter togetherWith ExitTransition.KeepUntilTransitionsFinished
                }
                depth < initialState.previousEntries.size -> {
                    val enter = fadeIn(tween(220, easing = ChildEasing), initialAlpha = UnderlayAlpha)
                    val exit = slideOutHorizontally(tween(220, easing = ChildEasing)) { direction * it } +
                        ExitTransition.KeepUntilTransitionsFinished
                    enter togetherWith exit
                }
                else -> {
                    val enter = slideInHorizontally(tween(280, easing = ChildEasing)) { direction * it }
                    val exit = slideOutHorizontally(tween(280, easing = ChildEasing)) { (-direction * it * 0.22f).roundToInt() } +
                        fadeOut(tween(280, easing = ChildEasing), targetAlpha = UnderlayAlpha) +
                        ExitTransition.KeepUntilTransitionsFinished
                    enter togetherWith exit
                }
            }
            motion.using(null).apply { targetContentZIndex = depth.toFloat() }
        },
    ) { scene ->
        val progress = transition.animateFloat(
            transitionSpec = { if (swipe == null) snap() else tween(PredictiveDuration, easing = LinearEasing) },
            label = "predictive back surface",
        ) { phase ->
            if (phase == EnterExitState.PostExit && swipe != null) 1f else 0f
        }
        val surface = if (swipe == null) Modifier else Modifier.graphicsLayer {
            // Once released, keep the preview's scale and corner radius while the same layer slides out.
            val exitStart = swipe.releaseFraction ?: PreviewFraction
            val preview = minOf(progress.value, exitStart) / PreviewFraction
            val exit = ((progress.value - exitStart) / (1f - exitStart)).coerceIn(0f, 1f)
            val swipeDirection = if (swipe.edge == NavigationEvent.EDGE_RIGHT) -1 else 1
            translationX = swipeDirection * (24.dp.toPx() * preview + size.width * exit)
            scaleX = 1f - 0.14f * preview
            scaleY = scaleX
            transformOrigin = TransformOrigin(if (swipeDirection > 0) 0.8f else 0.2f, 0.5f)
            shape = RoundedCornerShape(24.dp * preview)
            clip = preview > 0f
        }
        Box(Modifier.fillMaxSize().then(surface)) { scene.content() }
    }
}
