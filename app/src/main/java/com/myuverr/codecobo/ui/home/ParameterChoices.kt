// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.myuverr.codecobo.ui.theme.adaptiveSp
import com.myuverr.codecobo.R
import com.myuverr.codecobo.ui.theme.LocalAppTypeScale

@Composable
internal fun ChoiceTargetMarker(modifier: Modifier = Modifier, container: Color, content: Color) {
    Box(modifier.size(14.dp).background(container, CircleShape), contentAlignment = Alignment.Center) {
        Icon(painterResource(R.drawable.ic_choice_check), null, Modifier.size(10.dp), tint = content)
    }
}

internal data class ParameterOption(val value: Int, val label: String)

private val ChoiceGap = 8.dp
private val MarkerRail = 18.dp
private val RateUnitGap = 4.dp

@Composable
internal fun ParameterChoices(
    group: String,
    options: List<ParameterOption>,
    selected: Int?,
    actual: Int?,
    enabled: Boolean,
    onSelected: (Int) -> Unit,
) {
    val rate = group == "rate"
    val type = LocalAppTypeScale.current
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val textStyle = LocalTextStyle.current
    val minimumWidths = remember(options, measurer, density, rate, textStyle, type) {
        options.map { option ->
            val family = if (!rate && option.label.startsWith("aptX ")) "aptX" else option.label
            val variant = if (!rate && option.label.startsWith("aptX ")) option.label.removePrefix("aptX ") else ""
            val primary = measurer.measure(
                family, textStyle.copy(fontSize = if (rate) type.rate else type.codec,
                    lineHeight = type.sp(if (rate) 28f else 26f),
                    fontWeight = FontWeight.Medium, letterSpacing = type.sp(0f)), softWrap = false,
            ).size
            val secondary = measurer.measure(variant, textStyle.copy(fontSize = type.variant,
                lineHeight = type.sp(18f), letterSpacing = type.sp(0f)), softWrap = false).size
            val unit = if (rate) measurer.measure("kHz", textStyle.copy(fontSize = type.unit,
                letterSpacing = type.sp(0f)), softWrap = false).size else null
            with(density) {
                minimumChoiceWidth(
                    if (rate) primary.width + unit!!.width + RateUnitGap.roundToPx() else maxOf(primary.width, secondary.width),
                    if (rate) maxOf(primary.height, unit!!.height) else primary.height + if (variant.isEmpty()) 0 else secondary.height,
                    (if (rate) 67.dp else 72.dp).roundToPx(),
                ).toDp()
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val availableWidth = maxWidth
        val rows = remember(minimumWidths, availableWidth, density) {
            with(density) { choiceRows(minimumWidths.map { it.roundToPx() }, availableWidth.roundToPx(), ChoiceGap.roundToPx()) }
        }
        Column(Modifier.fillMaxWidth().selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (row in rows) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ChoiceGap)) {
                    for (index in row) {
                        val option = options[index]
                        key(option.value) {
                            val family = if (!rate && option.label.startsWith("aptX ")) "aptX" else option.label
                            val variant = if (!rate && option.label.startsWith("aptX ")) option.label.removePrefix("aptX ") else ""
                            val minimum = minimumWidths[index]
                            Choice(
                                option, group, family, variant, selected == option.value, actual == option.value,
                                enabled, onSelected, index == row.first, index == row.last,
                                Modifier.weight(minimum.value).widthIn(min = minOf(minimum, availableWidth))
                                    .heightIn(min = if (rate) 67.dp else 72.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Choice(
    option: ParameterOption,
    group: String,
    family: String,
    variant: String,
    selected: Boolean,
    actual: Boolean,
    enabled: Boolean,
    onSelected: (Int) -> Unit,
    roundStart: Boolean,
    roundEnd: Boolean,
    modifier: Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val type = LocalAppTypeScale.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val startRoundness by animateFloatAsState(
        if (!pressed && (selected || roundStart)) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(), label = "choiceStartCorner",
    )
    val endRoundness by animateFloatAsState(
        if (!pressed && (selected || roundEnd)) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(), label = "choiceEndCorner",
    )
    val shape = parameterChoiceShape(startRoundness, endRoundness)
    val foreground = if (selected) colors.onPrimary else colors.onSurface
    val rippleColor = rememberUpdatedState(foreground)
    // Updating tint must not recreate an in-flight ripple.
    val indication = remember { ripple(color = { rippleColor.value }) }

    val background = if (selected) colors.primary else colors.surfaceContainerHigh
    val status = stringResource(when {
        selected && actual -> R.string.home_state_both
        selected -> R.string.home_state_target
        actual -> R.string.home_state_actual
        else -> R.string.home_state_none
    })
    Box(
        modifier.clip(shape).background(background)
            .selectable(
                selected = selected, enabled = enabled, role = Role.RadioButton,
                interactionSource = interaction,

                indication = indication,
                onClick = { if (!selected) onSelected(option.value) },
            )
            .testTag(group + "-" + option.value)
            .semantics(mergeDescendants = true) {
                contentDescription = option.label + if (group == "rate") " kHz" else ""
                stateDescription = status
            }
            .safeChoicePadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(end = MarkerRail),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (group == "rate") {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Row(horizontalArrangement = Arrangement.spacedBy(RateUnitGap), verticalAlignment = Alignment.CenterVertically) {
                        Text(family, fontSize = type.rate, lineHeight = 28.adaptiveSp, letterSpacing = 0.adaptiveSp, color = foreground, fontWeight = FontWeight.Medium)
                        Text("kHz", fontSize = type.unit, letterSpacing = 0.adaptiveSp, color = foreground)
                    }
                }
            } else {
                Text(family, fontSize = type.codec, lineHeight = 26.adaptiveSp, letterSpacing = 0.adaptiveSp, color = foreground, fontWeight = FontWeight.Medium)
                if (variant.isNotEmpty()) Text(variant, fontSize = type.variant, lineHeight = 18.adaptiveSp, letterSpacing = 0.adaptiveSp, color = foreground)
            }
        }
        if (selected) ChoiceTargetMarker(Modifier.align(Alignment.TopEnd), foreground, background)
        if (actual) Icon(
            painterResource(R.drawable.ic_choice_bluetooth), null,
            Modifier.align(Alignment.BottomEnd).size(14.dp), tint = foreground,
        )
    }
}
