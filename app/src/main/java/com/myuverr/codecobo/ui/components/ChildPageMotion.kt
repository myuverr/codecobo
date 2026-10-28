// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*

private data class ChildPage(val key: String, val depth: Int)

/** Animates child pages while retaining their saveable state. */
@Composable
internal fun ChildPageMotion(
    key: String, depth: Int, modifier: Modifier = Modifier, enabled: Boolean = true,
    content: @Composable (String) -> Unit,
) {
    val holder = rememberSaveableStateHolder()
    val density = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val distance = with(density) { 32.dp.roundToPx() }
    val focus = LocalFocusManager.current
    LaunchedEffect(key) { focus.clearFocus(force = true) }
    val target = ChildPage(key, depth)
    val motion = remember { SeekableTransitionState(target) }
    val transition = rememberTransition(motion, label = "child-route")
    LaunchedEffect(key, depth, enabled) {
        val duration = coroutineContext[MotionDurationScale]
        snapshotFlow { duration?.scaleFactor ?: 1f }.collectLatest { factor ->
            // Dispose outgoing content when motion is disabled mid-flight.
            if (!enabled || factor == 0f) motion.snapTo(target) else motion.animateTo(target)
        }
    }
    transition.AnimatedContent(
        modifier = modifier.fillMaxSize().clipToBounds(),
        contentKey = { it.key },
        transitionSpec = {
            val sign = (if (targetState.depth >= initialState.depth) 1 else -1) * if (rtl) -1 else 1
            val transform = if (!enabled) EnterTransition.None togetherWith ExitTransition.None else
                (fadeIn(tween(200, 40)) + slideInHorizontally(tween(280, easing = FastOutSlowInEasing)) { sign * distance })
                    .togetherWith(fadeOut(tween(110)) + slideOutHorizontally(tween(240, easing = FastOutSlowInEasing)) { -sign * distance })
            transform.using(null)
        },
    ) { shown ->
        val active = shown.key == key
        val inert = if (active) Modifier else Modifier
            .clearAndSetSemantics { hideFromAccessibility(); testTag = "outgoing-child-page" }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                }
            }
        Box(Modifier.fillMaxSize().testTag("child-page-" + shown.key).then(inert)
            .focusProperties { canFocus = active; onEnter = { if (!active) cancelFocusChange() } }.focusGroup()) {
            holder.SaveableStateProvider(shown.key) { content(shown.key) }
        }
    }
}
