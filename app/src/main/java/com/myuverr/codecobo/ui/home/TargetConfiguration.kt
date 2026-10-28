// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.ui.home

import androidx.compose.ui.platform.LocalResources
import com.myuverr.codecobo.presentation.AudioText

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.myuverr.codecobo.ui.theme.adaptiveSp
import com.myuverr.codecobo.R
import com.myuverr.codecobo.core.AudioParameters

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
                color = if (state.submission != com.myuverr.codecobo.core.SubmissionIntent.NONE)
                    MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun TargetConfiguration(
    state: HomeState,
    onCodecSelected: (Int) -> Unit,
    onRateSelected: (Int) -> Unit,
    onBitsSelected: (Int) -> Unit,
    onChannelSelected: (Int) -> Unit,
    onQualitySelected: (Int) -> Unit,
) {
    val audio = AudioText(LocalResources.current)
    val connection = state.connection ?: return
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(17.dp)) {
        Column(Modifier.testTag("codec-group"), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            ParameterHeading(stringResource(R.string.home_codec)) { ChoiceLegend() }
            ParameterChoices(
                "codec", state.capabilities.map { ParameterOption(it.type(), it.name()) },
                state.editor.codecType, connection.snapshot()?.actual()?.type(),
                state.canEditDraft, onCodecSelected,
            )
        }
        Column(Modifier.testTag("rate-group"), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            ParameterHeading(stringResource(R.string.home_rate))
            ParameterChoices(
                "rate", state.rates.map { ParameterOption(it, audio.rateValue(it)) },
                state.editor.sampleRate, connection.snapshot()?.actual()?.sampleRate(),
                state.canEditDraft, onRateSelected,
            )
        }
        val actual = connection.snapshot()?.actual()?.takeIf { it.type() == state.editor.codecType }
        if (state.bits.size > 1) ExtendedParameterGroup(
            "bits", stringResource(R.string.home_bits), state.bits.map { ParameterOption(it, audio.bitsLabel(it)) },
            state.editor.bitsPerSample, actual?.bitsPerSample(), state.canEditDraft, onBitsSelected)
        if (state.channels.size > 1) ExtendedParameterGroup(
            "channel", stringResource(R.string.home_channel), state.channels.map { ParameterOption(it, audio.channelLabel(it)) },
            state.editor.channelMode, actual?.channelMode(), state.canEditDraft, onChannelSelected)
        if (state.qualities.isNotEmpty()) ExtendedParameterGroup(
            "quality", stringResource(R.string.home_quality), state.qualities.map { ParameterOption(it, audio.policyLabel(it.toLong())) },
            state.editor.ldacQuality?.takeIf { AudioParameters.policyKnown(it) }?.toInt(),
            actual?.ldacQuality()?.takeIf { AudioParameters.policyKnown(it) }?.toInt(), state.canEditDraft, onQualitySelected)
    }
}

@Composable
private fun ExtendedParameterGroup(group: String, title: String, options: List<ParameterOption>,
    selected: Int?, actual: Int?, enabled: Boolean, onSelected: (Int) -> Unit) {
    Column(Modifier.testTag("$group-group"), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        ParameterHeading(title)
        ParameterChoices(group, options, selected, actual, enabled, onSelected)
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
internal fun SubmitTarget(state: HomeState, onSubmit: () -> Unit, modifier: Modifier = Modifier) {
    val savedTargetMatches = state.savedTargetMatches
    val savedDescription = stringResource(R.string.home_saved_unchanged)
    val actionDescription = stringResource(if (state.capabilities.isEmpty()) R.string.home_prepare else R.string.home_submit)
    val submittingDescription = stringResource(R.string.home_submitting)
    val completedDescription = stringResource(if (state.currentSubmission?.status() == com.myuverr.codecobo.controller.SubmissionFeedback.Status.SAVED)
        R.string.home_completed_saved else R.string.home_completed_applied)
    FilledIconButton(
        onClick = onSubmit,
        enabled = state.canSubmit,
        modifier = modifier.size(56.dp).testTag("apply").semantics {
            contentDescription = actionDescription
            if (state.submitting) stateDescription = submittingDescription
            else if (savedTargetMatches) stateDescription = savedDescription
            else if (state.completedTargetMatches) stateDescription = completedDescription
        },
        shape = MaterialShapes.Cookie9Sided.toShape(),
    ) {
        SubmitFeedback(state)
    }
}
