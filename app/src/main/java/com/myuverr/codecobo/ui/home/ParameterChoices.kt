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
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.myuverr.codecobo.R
import com.myuverr.codecobo.ui.theme.LocalAppTypeScale

internal enum class ParameterKind(val tag: String) {
    CODEC("codec"), RATE("rate"), BITS("bits"), CHANNEL("channel"), QUALITY("quality")
}

/**
 * One selectable value. [label] is the complete spoken name; the visual parts are supplied by the
 * caller so this file does not need to know codec families or rate units.
 */
internal data class ParameterOption(
    val value: Int,
    val label: String,
    val primary: String = label,
    val secondary: String? = null,
    val unit: String? = null,
)

private val ChoiceGap = 8.dp

/** Space reserved at the end of a choice for the target check and the actual-output glyph. */
private val MarkerRail = 18.dp
private val RateUnitGap = 4.dp

@Composable
internal fun ChoiceTargetMarker(modifier: Modifier = Modifier, container: Color, content: Color) {
    Box(modifier.size(14.dp).background(container, CircleShape), contentAlignment = Alignment.Center) {
        Icon(painterResource(R.drawable.ic_choice_check), null, Modifier.size(10.dp), tint = content)
    }
}

/**
 * Choice label styles are shared by measurement and drawing: measuring with a different style than
 * the one drawn would wrap rows at the wrong place.
 */
private data class ChoiceTypography(
    val primary: TextStyle,
    val secondary: TextStyle,
    val unit: TextStyle,
)

@Composable
private fun choiceTypography(kind: ParameterKind): ChoiceTypography {
    val base = LocalTextStyle.current
    val type = LocalAppTypeScale.current
    return remember(base, type, kind) {
        val primarySize = if (kind == ParameterKind.RATE) type.rate else type.codec
        val primaryLine = if (kind == ParameterKind.RATE) 28f else 26f
        ChoiceTypography(
            primary = base.copy(fontSize = primarySize, lineHeight = type.sp(primaryLine),
                fontWeight = FontWeight.Medium, letterSpacing = type.sp(0f)),
            secondary = base.copy(fontSize = type.variant, lineHeight = type.sp(18f),
                letterSpacing = type.sp(0f)),
            unit = base.copy(fontSize = type.unit, letterSpacing = type.sp(0f)),
        )
    }
}

@Composable
internal fun ParameterChoices(
    kind: ParameterKind,
    options: List<ParameterOption>,
    selected: Int?,
    actual: Int?,
    enabled: Boolean,
    onSelected: (Int) -> Unit,
) {
    val typography = choiceTypography(kind)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val minHeight = if (kind == ParameterKind.RATE) 67.dp else 72.dp
    // The option's visual minimum follows the theme's touch-target size.
    val minimumInteractiveSize = LocalMinimumInteractiveComponentSize.current
    val minWidths = remember(options, typography, minHeight, minimumInteractiveSize, measurer, density) {
        options.map { option ->
            with(density) {
                val primary = measurer.measure(option.primary, typography.primary, softWrap = false).size
                val secondary = option.secondary?.let {
                    measurer.measure(it, typography.secondary, softWrap = false).size
                }
                val unit = option.unit?.let {
                    measurer.measure(it, typography.unit, softWrap = false).size
                }
                val contentWidth = if (unit != null) primary.width + unit.width + RateUnitGap.roundToPx()
                    else maxOf(primary.width, secondary?.width ?: 0)
                val contentHeight = if (unit != null) maxOf(primary.height, unit.height)
                    else primary.height + (secondary?.height ?: 0)
                minimumChoiceWidth(
                    contentWidth, contentHeight, minHeight.roundToPx(), MarkerRail.roundToPx(),
                    minimumInteractiveSize.roundToPx(),
                ).toDp()
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val availableWidth = maxWidth
        val rows = remember(minWidths, availableWidth, density) {
            with(density) {
                choiceRows(minWidths.map { it.roundToPx() }, availableWidth.roundToPx(),
                    ChoiceGap.roundToPx())
            }
        }
        Column(Modifier.fillMaxWidth().selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(ChoiceGap)) {
            for (row in rows) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ChoiceGap)) {
                    for (index in row) {
                        val option = options[index]
                        val minimum = minWidths[index]
                        key(option.value) {
                            Choice(
                                option, kind.tag, typography,
                                selected == option.value, actual == option.value,
                                enabled, onSelected, index == row.first, index == row.last,
                                Modifier.weight(minimum.value)
                                    .widthIn(min = minOf(minimum, availableWidth))
                                    .heightIn(min = minHeight),
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
    tagPrefix: String,
    typography: ChoiceTypography,
    selected: Boolean,
    actual: Boolean,
    enabled: Boolean,
    onSelected: (Int) -> Unit,
    roundStart: Boolean,
    roundEnd: Boolean,
    modifier: Modifier,
) {
    val colors = MaterialTheme.colorScheme
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
            .testTag("$tagPrefix-${option.value}")
            .semantics(mergeDescendants = true) {
                contentDescription = option.label
                stateDescription = status
            }
            .safeChoicePadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(end = MarkerRail),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val unit = option.unit
            if (unit != null) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Row(horizontalArrangement = Arrangement.spacedBy(RateUnitGap),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(option.primary, style = typography.primary, color = foreground)
                        Text(unit, style = typography.unit, color = foreground)
                    }
                }
            } else {
                Text(option.primary, style = typography.primary, color = foreground)
                option.secondary?.let { Text(it, style = typography.secondary, color = foreground) }
            }
        }
        if (selected) ChoiceTargetMarker(Modifier.align(Alignment.TopEnd), foreground, background)
        if (actual) Icon(
            painterResource(R.drawable.ic_choice_bluetooth), null,
            Modifier.align(Alignment.BottomEnd).size(14.dp), tint = foreground,
        )
    }
}
