// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.ui.settings

import android.app.LocaleConfig
import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.myuverr.codecobo.R
import com.myuverr.codecobo.ui.components.ChoiceSheetItem

private fun resolvedLanguageName(context: Context, locales: LocaleList): String =
    context.createConfigurationContext(Configuration(context.resources.configuration).apply {
        setLocales(locales)
    }).getString(R.string.language_name)

@Composable
internal fun applicationLanguageName(locales: LocaleList): String = when {
    locales.isEmpty -> stringResource(R.string.settings_language_system)
    else -> stringResource(R.string.language_name)
}

@Composable
internal fun LanguageChoices(
    applicationLocales: LocaleList,
    systemLocales: LocaleList,
    enabled: Boolean,
    onSelected: (LocaleList) -> Unit,
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val supported = remember(context, configuration) {
        val locales = requireNotNull(LocaleConfig(context).supportedLocales)
        (0 until locales.size()).map { locales[it] }.sortedBy { it.toLanguageTag() }
    }
    // Each name needs its own configuration context; resolve them once, not per recomposition.
    val names = remember(context, configuration, supported) {
        supported.associateWith { resolvedLanguageName(context, LocaleList(it)) }
    }
    val followsSystem = applicationLocales.isEmpty
    val systemDescription = if (followsSystem) {
        val name = remember(context, configuration, systemLocales) { resolvedLanguageName(context, systemLocales) }
        stringResource(R.string.settings_language_system_hint, name)
    } else ""
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ChoiceSheetItem(
            stringResource(R.string.settings_language_system), followsSystem, "language-system",
            systemDescription, enabled = enabled,
        ) { onSelected(LocaleList.getEmptyLocaleList()) }
        for (locale in supported) {
            val locales = LocaleList(locale)
            val selected = !applicationLocales.isEmpty &&
                LocaleList.matchesLanguageAndScript(applicationLocales[0], locale)
            ChoiceSheetItem(
                names.getValue(locale), selected, "language-" + locale.toLanguageTag(),
                enabled = enabled,
            ) { onSelected(locales) }
        }
    }
}
