// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.myuverr.codecobo.R
import com.myuverr.codecobo.ui.theme.LocalWorkbenchColors
import com.myuverr.codecobo.ui.theme.WorkbenchInfoShape
import com.myuverr.codecobo.ui.components.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val ProjectAuthor = "myuverr"
private const val ProjectRepository = "https://github.com/$ProjectAuthor/codecobo"

internal val ShippedLicenses = linkedMapOf(
    "MPL-2.0.txt" to "Mozilla Public License 2.0",
    "Apache-2.0.txt" to "Apache License 2.0",
    "Shizuku-MIT.txt" to "Shizuku API · MIT",
    "PHOSPHOR-MIT.txt" to "Phosphor · MIT",
    "Lucide-LICENSE.txt" to "Lucide · ISC / MIT",
    "Lottie-Apache-2.0.txt" to "Lottie · Apache 2.0",
)

@Composable
internal fun AboutScreen(state: SettingsState, onBack: () -> Unit,
    onNavigate: (SettingsRoute) -> Unit, onLink: (String) -> Unit) {
    PageLayout(stringResource(R.string.settings_about, stringResource(R.string.app_name)), onBack) {
        Card(Modifier.fillMaxWidth(), shape = WorkbenchInfoShape,
            colors = CardDefaults.cardColors(containerColor = LocalWorkbenchColors.current.tint)) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PageNote(stringResource(R.string.settings_tool))
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
                PageNote(stringResource(R.string.settings_version, state.data.version.ifBlank { stringResource(R.string.settings_not_read) }))
            }
        }
        Spacer(Modifier.height(18.dp))
        val external: @Composable () -> Unit = {
            Icon(painterResource(R.drawable.ic_home_external), null, Modifier.size(16.dp))
        }
        SectionLabel(stringResource(R.string.about_project))
        Column(Modifier.fillMaxWidth()) {
            SettingRow(stringResource(R.string.about_developer), ProjectAuthor,
                onClick = { onLink("https://github.com/$ProjectAuthor") },
                shapes = settingsGroupShapes(0, 4), trailing = external)
            Spacer(Modifier.height(2.dp))
            SettingRow(stringResource(R.string.about_source), "$ProjectAuthor/codecobo",
                onClick = { onLink(ProjectRepository) }, shapes = settingsGroupShapes(1, 4), trailing = external)
            Spacer(Modifier.height(2.dp))
            SettingRow(stringResource(R.string.about_issues), "GitHub Issues",
                onClick = { onLink("$ProjectRepository/issues") }, shapes = settingsGroupShapes(2, 4), trailing = external)
            Spacer(Modifier.height(2.dp))
            SettingRow(stringResource(R.string.about_releases), "GitHub Releases",
                onClick = { onLink("$ProjectRepository/releases") }, shapes = settingsGroupShapes(3, 4), trailing = external)
        }
        SectionLabel(stringResource(R.string.about_legal))
        Column(Modifier.fillMaxWidth()) {
            SettingRow(stringResource(R.string.about_project_license), "MPL-2.0",
                onClick = { onNavigate(SettingsRoute.PROJECT_LICENSE) }, shapes = settingsGroupShapes(0, 3))
            Spacer(Modifier.height(2.dp))
            SettingRow(stringResource(R.string.about_third_party),
                onClick = { onNavigate(SettingsRoute.LICENSES) }, shapes = settingsGroupShapes(1, 3))
            Spacer(Modifier.height(2.dp))
            SettingRow(stringResource(R.string.about_privacy),
                onClick = { onNavigate(SettingsRoute.PRIVACY) }, shapes = settingsGroupShapes(2, 3))
        }
    }
}

@Composable
internal fun LicensesScreen(onBack: () -> Unit, onOpen: (String) -> Unit, onNotices: () -> Unit) {
    PageLayout(stringResource(R.string.settings_licenses), onBack) {
        val thirdParty = ShippedLicenses.filterKeys { it != "MPL-2.0.txt" }
        Column(Modifier.fillMaxWidth()) {
            SettingRow(stringResource(R.string.about_notices), icon = R.drawable.ic_settings_info,
                onClick = onNotices, shapes = settingsGroupShapes(0, thirdParty.size + 1))
            for ((index, entry) in thirdParty.entries.withIndex()) {
                Spacer(Modifier.height(2.dp))
                SettingRow(entry.value, icon = R.drawable.ic_settings_info,
                    onClick = { onOpen(entry.key) }, shapes = settingsGroupShapes(index + 1, thirdParty.size + 1))
            }
        }
    }
}

@Composable
internal fun PrivacyScreen(onBack: () -> Unit) {
    PageLayout(stringResource(R.string.about_privacy), onBack) {
        SelectionContainer {
            Text(stringResource(R.string.about_privacy_body, stringResource(R.string.app_name),
                ProjectAuthor, "$ProjectRepository/issues"), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
internal fun AssetTextScreen(asset: String, title: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val failed = stringResource(R.string.about_document_error)
    var text by rememberSaveable(asset) { mutableStateOf<String?>(null) }
    LaunchedEffect(asset) {
        if (text == null) text = withContext(Dispatchers.IO) {
            try { context.assets.open(asset).bufferedReader().use { it.readText() } }
            catch (_: java.io.IOException) { failed }
        }
    }
    PageLayout(title, onBack) {
        SelectionContainer {
            Text(text ?: stringResource(R.string.about_document_loading), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
