// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.myuverr.codecobo.R
import com.myuverr.codecobo.controller.SubmissionFeedback
import com.myuverr.codecobo.core.AudioParameters
import com.myuverr.codecobo.core.SubmissionIntent
import com.myuverr.codecobo.presentation.AudioText
import com.myuverr.codecobo.ui.LocalNavigationActionSize
import com.myuverr.codecobo.ui.theme.adaptiveSp

@Composable
internal fun PendingTarget(state: HomeState) {
    val summary = homePendingSummary(state, LocalResources.current)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 27.dp)
            .semantics { liveRegion = LiveRegionMode.Polite }.padding(horizontal = 2.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        if (state.changingDevice || state.savedDraftUnavailable) {
            Text(stringResource(if (state.changingDevice) R.string.home_switching_device else R.string.home_saved_unavailable),
                fontSize = 11.adaptiveSp, lineHeight = 17.adaptiveSp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else if (summary != null) {
            Icon(painterResource(R.drawable.ic_home_target), null, Modifier.size(13.dp),
                tint = MaterialTheme.colorScheme.primary)
            Text(summary, Modifier.weight(1f).testTag("pending-summary"),
                fontSize = 11.adaptiveSp, lineHeight = 17.adaptiveSp,
                color = if (state.submission != SubmissionIntent.NONE)
                    MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun TargetConfiguration(state: HomeState, actions: HomeActions) {
    val audio = AudioText(LocalResources.current)
    if (state.connection == null) return
    val actual = state.actual
    val selection = state.selection
    val rateUnit = stringResource(R.string.audio_rate_unit)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(17.dp)) {
        ParameterGroup(
            ParameterKind.CODEC, stringResource(R.string.home_codec),
            state.capabilities.map {
                val label = codecLabel(it.name())
                ParameterOption(it.type(), it.name(), label.family, label.variant)
            },
            state.selectedCodecType, actual?.type(), state.canEditDraft, actions,
        ) { ChoiceLegend() }
        ParameterGroup(
            ParameterKind.RATE, stringResource(R.string.home_rate),
            state.rates.map { ParameterOption(it, audio.rate(it), audio.rateValue(it), unit = rateUnit) },
            selection?.sampleRate, actual?.sampleRate(), state.canEditDraft, actions,
        )
        if (state.bits.size > 1) ParameterGroup(
            ParameterKind.BITS, stringResource(R.string.home_bits),
            state.bits.map { ParameterOption(it, audio.bitsLabel(it)) },
            selection?.bitsPerSample, actual?.bitsPerSample(), state.canEditDraft, actions,
        )
        if (state.channels.size > 1) ParameterGroup(
            ParameterKind.CHANNEL, stringResource(R.string.home_channel),
            state.channels.map { ParameterOption(it, audio.channelLabel(it)) },
            selection?.channelMode, actual?.channelMode(), state.canEditDraft, actions,
        )
        if (state.qualities.isNotEmpty()) ParameterGroup(
            ParameterKind.QUALITY, stringResource(R.string.home_quality),
            state.qualities.map { ParameterOption(it, audio.policyLabel(it.toLong())) },
            selection?.ldacQuality?.toInt(),
            actual?.ldacQuality()?.takeIf { AudioParameters.policyKnown(it) }?.toInt(),
            state.canEditDraft, actions,
        )
    }
}

@Composable
private fun ParameterGroup(
    kind: ParameterKind,
    title: String,
    options: List<ParameterOption>,
    selected: Int?,
    actual: Int?,
    enabled: Boolean,
    actions: HomeActions,
    trailing: @Composable () -> Unit = {},
) {
    Column(Modifier.testTag("${kind.tag}-group"), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        ParameterHeading(title, trailing)
        ParameterChoices(kind, options, selected, actual, enabled) {
            actions.onParameterSelected(kind, it)
        }
    }
}

@Composable
private fun ParameterHeading(title: String, trailing: @Composable () -> Unit = {}) {
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(5.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, fontSize = 12.adaptiveSp, lineHeight = 17.adaptiveSp, fontWeight = FontWeight.SemiBold)
        trailing()
    }
}

@Composable
private fun ChoiceLegend() {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp), itemVerticalAlignment = Alignment.CenterVertically) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_choice_bluetooth), null, Modifier.size(13.dp), tint = MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.home_actual_legend), fontSize = 10.adaptiveSp, lineHeight = 15.adaptiveSp, color = MaterialTheme.colorScheme.primary)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            ChoiceTargetMarker(container = MaterialTheme.colorScheme.primary, content = MaterialTheme.colorScheme.onPrimary)
            Text(stringResource(R.string.home_target_legend), fontSize = 10.adaptiveSp, lineHeight = 15.adaptiveSp, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SubmitTarget(state: HomeState, onSubmit: () -> Unit, shown: Boolean, modifier: Modifier = Modifier) {
    val enabled = state.canSubmit && shown
    val savedTargetMatches = state.savedTargetMatches
    val savedDescription = stringResource(R.string.home_saved_unchanged)
    val actionDescription = stringResource(if (state.capabilities.isEmpty()) R.string.home_prepare else R.string.home_submit)
    val submittingDescription = stringResource(R.string.home_submitting)
    val completedDescription = stringResource(if (state.currentSubmission?.status() == SubmissionFeedback.Status.SAVED)
        R.string.home_completed_saved else R.string.home_completed_applied)
    FilledIconButton(
        onClick = onSubmit,
        enabled = enabled,
        modifier = modifier.size(LocalNavigationActionSize.current).testTag("apply").semantics {
            contentDescription = actionDescription
            if (state.submitting) stateDescription = submittingDescription
            else if (savedTargetMatches) stateDescription = savedDescription
            else if (state.completedTargetMatches) stateDescription = completedDescription
        },
        colors = submitButtonColors(enabled),
        shape = MaterialShapes.Cookie9Sided.toShape(),
    ) {
        SubmitFeedback(state)
    }
}

/**
 * Saving usually leaves nothing to submit, so the button disables while the receipt is still drawing.
 * Resolving both palettes into one animated role keeps that handover from flashing.
 */
@Composable
private fun submitButtonColors(enabled: Boolean): IconButtonColors {
    val base = IconButtonDefaults.filledIconButtonColors()
    val live by animateFloatAsState(
        if (enabled) 1f else 0f,
        MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "submit-enabled",
    )
    val container = lerp(base.disabledContainerColor, base.containerColor, live)
    val content = lerp(base.disabledContentColor, base.contentColor, live)
    return base.copy(
        containerColor = container, contentColor = content,
        disabledContainerColor = container, disabledContentColor = content,
    )
}
