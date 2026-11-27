// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LoadingIndicatorDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.unit.dp
import com.myuverr.codecobo.R
import com.myuverr.codecobo.controller.SubmissionFeedback
import kotlin.coroutines.coroutineContext

/** Which of the button's three faces is showing: the ask, the work, or the receipt. */
private enum class SubmitStage { SUBMIT, SAVING, SAVED }

/**
 * The faces' box. Icon Monks' floppy inks more of its artboard than the tab glyphs do and sits white on
 * a full primary circle, so it is drawn at 18dp to read the same size as their 24dp glyphs.
 */
private val FaceSize = 18.dp

/**
 * The loading indicator keeps its own margins inside its box, so this box is a little wider than the
 * faces and the spinning shape lands just under them.
 */
private val SavingSize = 22.dp

@Composable
internal fun SubmitFeedback(state: HomeState) {
    val stage = when {
        state.submitting -> SubmitStage.SAVING
        state.showCompletedPackage -> SubmitStage.SAVED
        else -> SubmitStage.SUBMIT
    }
    // A receipt is inked once, and only while its face is on screen: editing the draft away and back
    // cancels the reveal midway, so that receipt shows up finished rather than half drawn.
    val receipt = state.currentSubmission?.takeIf { it.status() != SubmissionFeedback.Status.RUNNING }
    val shown = state.showCompletedPackage
    val walk = remember { Animatable(1f) }
    var inked by remember { mutableStateOf(receipt) }
    LaunchedEffect(receipt, shown) {
        if (receipt == null || !shown) return@LaunchedEffect
        if (receipt == inked) {
            walk.snapTo(1f)
            return@LaunchedEffect
        }
        inked = receipt
        walk.snapTo(0f)
        walk.animateTo(1f, tween(RevealMillis.toInt(), easing = LinearEasing))
    }
    val effects = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    AnimatedContent(
        targetState = stage,
        modifier = Modifier.size(SavingSize),
        transitionSpec = { fadeIn(effects) togetherWith fadeOut(effects) },
        contentAlignment = Alignment.Center,
        label = "submit-stage",
    ) { face ->
        when (face) {
            SubmitStage.SUBMIT -> Icon(
                painterResource(R.drawable.ic_action_save), null,
                Modifier.size(FaceSize).testTag("submit-save"),
            )
            SubmitStage.SAVING -> SavingIndicator()
            SubmitStage.SAVED -> SavedPackage(walk.asState(), Modifier.size(FaceSize).testTag("submit-package"))
        }
    }
}

/** An indeterminate spinner never settles, so a held frame stands in its place when animations are off. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SavingIndicator() {
    val spinning = (rememberCoroutineScope().coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f) > 0f
    val color = MaterialTheme.colorScheme.primary
    val busy = Modifier.fillMaxSize()
        .clearAndSetSemantics { progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate }
        .testTag("submit-loading")
    if (spinning) LoadingIndicator(modifier = busy, color = color)
    else LoadingIndicator(
        progress = { 0f }, modifier = busy, color = color,
        polygons = LoadingIndicatorDefaults.IndeterminateIndicatorPolygons,
    )
}
