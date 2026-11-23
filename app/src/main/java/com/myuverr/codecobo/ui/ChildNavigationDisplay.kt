// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.NavigationBackHandler
import androidx.navigation3.scene.SinglePaneSceneStrategy
import androidx.navigation3.scene.rememberNavigationEventState
import androidx.navigation3.scene.rememberSceneState
import androidx.navigationevent.NavigationEventTransitionState.InProgress
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch

private val PredictiveExitEasing = CubicBezierEasing(0.2f, 0.3f, 0.3f, 1f)

@Composable
internal fun ChildNavigationDisplay(
    entries: List<NavEntry<NavKey>>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strategies = remember { listOf(SinglePaneSceneStrategy<NavKey>()) }
    val scenes = rememberSceneState(entries, strategies, onBack = onBack)
    val events = rememberNavigationEventState(scenes)
    NavigationBackHandler(scenes, events, onBackCompleted = onBack)

    val current = scenes.currentScene
    val previous = scenes.previousScenes.lastOrNull()
    val state = remember { SeekableTransitionState(current) }
    val transition = rememberTransition(state, label = "child navigation")
    val gesture = events.transitionState as? InProgress
    val latestEvent by rememberUpdatedState(gesture?.latestEvent)
    var swipe by remember { mutableStateOf<BackSwipe?>(null) }

    LaunchedEffect(current, previous, gesture != null) {
        if (gesture != null && previous != null) {
            // Seeking to the previous scene during a push would reverse the push itself.
            if (state.currentState != current) state.animateTo(current)
            snapshotFlow { latestEvent }.filterNotNull().collectLatest { event ->
                swipe = BackSwipe(event.swipeEdge)
                state.seekTo(event.progress * PreviewFraction, previous)
            }
            return@LaunchedEffect
        }

        val activeSwipe = swipe
        when {
            activeSwipe == null -> state.animateTo(current)
            current == state.currentState -> state.rewindPreview()
            else -> {
                swipe = activeSwipe.copy(releaseFraction = state.fraction)
                state.animateTo(current, tween(280, easing = PredictiveExitEasing))
            }
        }
        swipe = null
    }

    transition.AnimatedChildContent(swipe, modifier.background(MaterialTheme.colorScheme.scrim))
}

private suspend fun <S> SeekableTransitionState<S>.rewindPreview() {
    val foreground = currentState
    val underlay = targetState
    val releaseFraction = fraction
    // Rewind the same scene pair so the underlay stays in place until covered.
    if (releaseFraction > 0f) {
        val progress = Animatable(releaseFraction)
        coroutineScope {
            launch { progress.animateTo(0f, tween(180)) }
            snapshotFlow { progress.value }.takeWhile { it > 0f }.collect { seekTo(it, underlay) }
        }
    }
    snapTo(foreground)
}
