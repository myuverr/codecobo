// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.myuverr.codecobo.R
import com.myuverr.codecobo.controller.SubmissionFeedback

/** The loading fades out, the package draws, its check mark follows, then the tint settles. */
private const val LoadingFadeMillis = 160
private const val PackageDrawMillis = 400
private const val CheckDrawMillis = 220
private const val TintMillis = 300

private const val CheckStartMillis = LoadingFadeMillis + PackageDrawMillis
private const val TintStartMillis = CheckStartMillis + CheckDrawMillis
private const val RevealMillis = TintStartMillis + TintMillis

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SubmitFeedback(state: HomeState) {
    val receipt = state.currentSubmission
    // Mount at the current endpoint without replaying a completed save.
    val initialTerminal = remember {
        receipt?.takeIf { it.status() != SubmissionFeedback.Status.RUNNING }
    }
    var lastStarted by remember { mutableStateOf(initialTerminal) }
    var active by remember { mutableStateOf<SubmissionFeedback?>(null) }
    val progress = remember { Animatable(0f) }
    val motion = rememberCoroutineScope().coroutineContext[MotionDurationScale]
    val animate = (motion?.scaleFactor ?: 1f) > 0f
    val success = state.completedTargetMatches
    val reveal = success && animate && (receipt != lastStarted || active == receipt)
    // Set the first frame before effects run to avoid an icon flash.
    val elapsed = if (active == receipt) progress.value * RevealMillis else 0f
    LaunchedEffect(receipt, success, animate) {
        active = null
        if (success && receipt != lastStarted) {
            lastStarted = receipt
            if (animate) {
                progress.snapTo(0f)
                active = receipt
                progress.animateTo(1f, tween(RevealMillis, easing = LinearEasing))
                active = null
            }
        }
    }
    Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
        if (state.submitting || (reveal && elapsed < LoadingFadeMillis)) {
            val fade = if (state.submitting) 0f else (elapsed / LoadingFadeMillis).coerceIn(0f, 1f)
            val loadingModifier = Modifier.size(32.dp).testTag("submit-loading")
                .graphicsLayer { alpha = 1f - fade; scaleX = 1f - fade * .15f; scaleY = scaleX }
                .clearAndSetSemantics { progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate }
            if (animate) LoadingIndicator(modifier = loadingModifier, color = MaterialTheme.colorScheme.primary)
            else LoadingIndicator(progress = { 0f }, modifier = loadingModifier,
                color = MaterialTheme.colorScheme.primary, polygons = LoadingIndicatorDefaults.IndeterminateIndicatorPolygons)
        } else if (reveal) {
            SavedPackageGlyph(
                bodyProgress = ((elapsed - LoadingFadeMillis) / PackageDrawMillis).coerceIn(0f, 1f),
                checkProgress = ((elapsed - CheckStartMillis) / CheckDrawMillis).coerceIn(0f, 1f),
                tint = lerp(MaterialTheme.colorScheme.primary, LocalContentColor.current,
                    ((elapsed - TintStartMillis) / TintMillis).coerceIn(0f, 1f)),
                modifier = Modifier.size(28.dp).testTag("submit-package"),
            )
        } else {
            Icon(painterResource(if (state.showCompletedPackage) R.drawable.ic_action_package else R.drawable.ic_action_save),
                null, Modifier.size(28.dp).testTag(if (state.showCompletedPackage) "submit-package" else "submit-save"))
        }
    }
}
