// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.Scene

private const val CHILD_DURATION = 240
private const val CHILD_FADE = 60
private const val CHILD_SCALE = 0.94f

internal fun AnimatedContentTransitionScope<Scene<NavKey>>.forwardNavigationTransition(childOffset: Int): ContentTransform {
    val enter = slideInHorizontally(tween(CHILD_DURATION)) { childOffset } +
        scaleIn(tween(CHILD_DURATION), initialScale = CHILD_SCALE) + fadeIn(tween(CHILD_FADE))
    // The parent stays in place under the incoming child.
    return enter togetherWith ExitTransition.KeepUntilTransitionsFinished
}

internal fun backNavigationTransition(childOffset: Int): ContentTransform {
    val exit = slideOutHorizontally(tween(CHILD_DURATION)) { childOffset } +
        scaleOut(tween(CHILD_DURATION), targetScale = CHILD_SCALE) +
        fadeOut(tween(CHILD_FADE, delayMillis = CHILD_DURATION - CHILD_FADE))
    return EnterTransition.None togetherWith exit
}
