// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.ui.unit.IntOffset
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.Scene
import androidx.navigation3.ui.defaultTransitionSpec

private const val TAB_KEY = "codecobo.topLevelPage"
private const val CHILD_DURATION = 240
private const val CHILD_FADE = 60
private const val CHILD_SCALE = 0.94f

internal fun NavKey.topLevelPage(): TopLevelPage? = TopLevelPage.entries.firstOrNull { it.route == this }

internal fun NavKey.tabMotionMetadata(): Map<String, Any> =
    topLevelPage()?.let { mapOf(TAB_KEY to it) }.orEmpty()

internal fun AnimatedContentTransitionScope<Scene<NavKey>>.forwardNavigationTransition(
    tabOffset: Int,
    childOffset: Int,
    tabSpring: FiniteAnimationSpec<IntOffset>,
): ContentTransform {
    val targetTab = targetState.tab()
    if (targetTab == null) {
        val enter = slideInHorizontally(tween(CHILD_DURATION)) { childOffset } +
            scaleIn(tween(CHILD_DURATION), initialScale = CHILD_SCALE) + fadeIn(tween(CHILD_FADE))
        // The parent stays in place under the incoming child.
        return enter togetherWith ExitTransition.KeepUntilTransitionsFinished
    }

    val initialTab = initialState.tab() ?: return defaultTransitionSpec<NavKey>().invoke(this)
    if (initialTab == targetTab) return EnterTransition.None togetherWith ExitTransition.None
    val offset = if (targetTab.ordinal > initialTab.ordinal) tabOffset else -tabOffset
    val enter = slideInHorizontally(tabSpring) { offset } + fadeIn(tween(100))
    return enter togetherWith slideOutHorizontally(tabSpring) { -offset }
}

internal fun backNavigationTransition(childOffset: Int): ContentTransform {
    val exit = slideOutHorizontally(tween(CHILD_DURATION)) { childOffset } +
        scaleOut(tween(CHILD_DURATION), targetScale = CHILD_SCALE) +
        fadeOut(tween(CHILD_FADE, delayMillis = CHILD_DURATION - CHILD_FADE))
    return EnterTransition.None togetherWith exit
}

private fun Scene<NavKey>.tab() = entries.last().metadata[TAB_KEY] as? TopLevelPage
