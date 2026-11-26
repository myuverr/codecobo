// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.ui.home

import androidx.compose.ui.platform.LocalResources
import com.myuverr.codecobo.presentation.AudioText

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.myuverr.codecobo.ui.theme.adaptiveSp
import com.myuverr.codecobo.R
import com.myuverr.codecobo.core.AudioParameters
import com.myuverr.codecobo.ui.theme.LocalWorkbenchColors
import com.myuverr.codecobo.ui.theme.LocalAppTypeScale
import com.myuverr.codecobo.ui.theme.WorkbenchInfoShape

/** Approximate glyphs reserved for the codec name before the artwork gets the remaining width. */
private const val CodecNameReserve = 7

@Composable
internal fun ActualOutputCard(state: HomeState) {
    val audio = AudioText(LocalResources.current)
    val snapshot = state.connection?.snapshot()
    val colors = LocalWorkbenchColors.current
    val type = LocalAppTypeScale.current
    val preferredTextWidth = with(LocalDensity.current) { type.codec.toDp() * CodecNameReserve }
    val actualDescription = if (snapshot != null) {
        stringResource(R.string.home_actual_description, snapshot.codecName(), audio.rate(snapshot.actual().sampleRate())) + audio.suffix(snapshot.actual())
    } else stringResource(R.string.home_actual_unreadable_description)
    Column(
        Modifier.fillMaxWidth()
            .clip(WorkbenchInfoShape)
            .background(colors.tint).testTag("actual-output")
            .semantics(mergeDescendants = true) { contentDescription = actualDescription }
            .padding(horizontal = 18.dp, vertical = 12.dp),
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val minimumTextWidth = minOf(preferredTextWidth, maxWidth)
            FlowRow(
                Modifier.fillMaxWidth().heightIn(min = 120.dp * type.factor),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                // Reserve text space before placing the artwork.
                Column(Modifier.weight(1f).widthIn(min = minimumTextWidth)) {
                    if (snapshot == null) {
                        Text(stringResource(R.string.home_actual_unreadable), color = colors.onTint, fontSize = 21.adaptiveSp, lineHeight = 29.adaptiveSp, fontWeight = FontWeight.SemiBold)
                    } else {
                        val codec = codecLabel(snapshot.codecName())
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                codec.family,
                                Modifier.alignByBaseline(), color = colors.onTint, fontSize = 36.adaptiveSp,
                                fontWeight = FontWeight.SemiBold, lineHeight = 40.adaptiveSp, letterSpacing = (-1).adaptiveSp,
                            )
                            codec.variant?.let {
                                Text(it, Modifier.alignByBaseline(), color = colors.onTint, fontSize = 20.adaptiveSp, lineHeight = 24.adaptiveSp)
                            }
                        }
                        Spacer(Modifier.height(7.dp))
                        val rate = snapshot.actual().sampleRate()
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text(audio.rateValue(rate), Modifier.alignByBaseline(), color = colors.onTint, fontSize = 20.adaptiveSp, lineHeight = 25.adaptiveSp)
                                if (AudioParameters.single(rate, 63)) Text(stringResource(R.string.audio_rate_unit), Modifier.alignByBaseline(), color = colors.onTint, fontSize = 11.adaptiveSp)
                            }
                        }
                        val details = audio.details(snapshot.actual())
                        if (details.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            Text(details, Modifier.testTag("actual-details"), color = colors.onTint,
                                style = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr), fontSize = 11.adaptiveSp, lineHeight = 17.adaptiveSp)
                        }
                    }
                }
                Box(Modifier.size(104.dp * type.factor, 120.dp * type.factor)) {
                    val artworkWidth = 180.dp * type.factor
                    HeadphoneIllustration(
                        // Crop the artwork against the bottom-right corner instead of shrinking it.
                        Modifier.align(Alignment.BottomEnd).wrapContentSize(Alignment.BottomEnd, unbounded = true).offset(x = 55.dp * type.factor, y = 50.dp * type.factor)
                            .size(artworkWidth, artworkWidth / HeadphoneArtworkAspectRatio).rotate(-8f),
                    )
                }
            }
        }
    }
}
