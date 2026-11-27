// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.myuverr.codecobo.R
import com.myuverr.codecobo.ui.components.*
import com.myuverr.codecobo.ui.theme.LocalWorkbenchColors
import com.myuverr.codecobo.ui.theme.WorkbenchInfoShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val ProjectAuthor = "myuverr"
private const val ProjectRepository = "https://github.com/$ProjectAuthor/codecobo"

@Composable
internal fun AboutScreen(
    version: String,
    onBack: () -> Unit,
    onNavigate: (SettingsRoute) -> Unit,
    onLink: (String) -> Unit,
    onOpenLicense: (LicenseDocument) -> Unit,
) {
    PageLayout(stringResource(R.string.settings_about, stringResource(R.string.app_name)), onBack) {
        Card(
            Modifier.fillMaxWidth(),
            shape = WorkbenchInfoShape,
            colors = CardDefaults.cardColors(containerColor = LocalWorkbenchColors.current.tint),
        ) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PageNote(stringResource(R.string.settings_tool))
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
                PageNote(stringResource(R.string.settings_version, version.ifBlank { stringResource(R.string.settings_not_read) }))
            }
        }
        Spacer(Modifier.height(18.dp))
        val external: @Composable () -> Unit = {
            Icon(painterResource(R.drawable.ic_home_external), null, Modifier.size(16.dp))
        }
        SectionLabel(stringResource(R.string.about_project))
        SettingsGroup(
            listOf(
                SettingsItem(
                    title = stringResource(R.string.about_developer),
                    description = ProjectAuthor,
                    trailing = external,
                    onClick = { onLink("https://github.com/$ProjectAuthor") },
                ),
                SettingsItem(
                    title = stringResource(R.string.about_source),
                    description = "$ProjectAuthor/codecobo",
                    trailing = external,
                    onClick = { onLink(ProjectRepository) },
                ),
                SettingsItem(
                    title = stringResource(R.string.about_issues),
                    description = "GitHub Issues",
                    trailing = external,
                    onClick = { onLink("$ProjectRepository/issues") },
                ),
                SettingsItem(
                    title = stringResource(R.string.about_releases),
                    description = "GitHub Releases",
                    trailing = external,
                    onClick = { onLink("$ProjectRepository/releases") },
                ),
            ),
        )
        SectionLabel(stringResource(R.string.about_legal))
        SettingsGroup(
            listOf(
                SettingsItem(
                    title = stringResource(R.string.about_project_license),
                    description = "MPL-2.0",
                    onClick = { onOpenLicense(ProjectLicense) },
                ),
                SettingsItem(
                    title = stringResource(R.string.about_third_party),
                    onClick = { onNavigate(SettingsRoute.LICENSES) },
                ),
                SettingsItem(
                    title = stringResource(R.string.about_privacy),
                    onClick = { onNavigate(SettingsRoute.PRIVACY) },
                ),
            ),
        )
    }
}

@Composable
internal fun LicensesScreen(
    onBack: () -> Unit,
    onOpen: (LicenseDocument) -> Unit,
    onNotices: () -> Unit,
) {
    PageLayout(stringResource(R.string.settings_licenses), onBack) {
        val notices = SettingsItem(
            title = stringResource(R.string.about_notices),
            icon = R.drawable.ic_settings_info,
            onClick = onNotices,
        )
        SettingsGroup(
            listOf(notices) + ThirdPartyLicenses.map { license ->
                SettingsItem(
                    title = license.title,
                    icon = R.drawable.ic_settings_info,
                    onClick = { onOpen(license) },
                )
            },
        )
    }
}

@Composable
internal fun PrivacyScreen(onBack: () -> Unit) {
    PageLayout(stringResource(R.string.about_privacy), onBack) {
        SelectionContainer {
            Text(
                stringResource(R.string.about_privacy_body, "$ProjectRepository/issues"),
                style = MaterialTheme.typography.bodyMedium,
            )
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
            try {
                context.assets.open(asset).bufferedReader().use { it.readText() }
            } catch (_: java.io.IOException) {
                failed
            }
        }
    }
    PageLayout(title, onBack) {
        SelectionContainer {
            Text(
                text ?: stringResource(R.string.about_document_loading),
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
