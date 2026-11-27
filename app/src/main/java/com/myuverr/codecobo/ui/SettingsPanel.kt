// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.ui

import android.app.LocaleManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.myuverr.codecobo.R
import com.myuverr.codecobo.controller.ScreenState
import com.myuverr.codecobo.ui.components.*
import com.myuverr.codecobo.ui.management.ManagementData
import com.myuverr.codecobo.ui.settings.*
import com.myuverr.codecobo.ui.theme.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

internal class SettingsPanel(private val context: Context, private val actions: Actions) : AutoCloseable {
    interface Actions {
        fun nearby()
        fun mode(enabled: Boolean)
        fun notification(enabled: Boolean)
        fun batterySettings()
        fun refreshManagement()
        fun copy(title: String, text: String)
        fun clearDiagnostics()
        fun themeSaveFailed()
        fun openProjectLink(url: String)
    }
    private enum class Sheet { THEME, MODE, LANGUAGE, CLEAR_TRACE }
    private val localeManager = requireNotNull(context.getSystemService(LocaleManager::class.java))
    // Writes must outlive the sheet that started them; only the activity ends this scope.
    private val writes = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var state by mutableStateOf(SettingsState(
        applicationLocales = localeManager.applicationLocales, systemLocales = localeManager.systemLocales,
    ))

    override fun close() { writes.cancel() }

    fun render(connection: ScreenState) { state = state.copy(connection = connection) }
    fun renderNotification(value: StatusNotificationControl.State) { state = state.copy(notification = value) }
    fun refreshLanguageFacts() {
        state = state.copy(applicationLocales = localeManager.applicationLocales, systemLocales = localeManager.systemLocales)
    }

    @Composable
    fun Content(
        route: SettingsRoute,
        data: ManagementData,
        onBack: () -> Unit,
        onNavigate: (SettingsRoute) -> Unit,
        onOpenRecord: (String?, Int) -> Unit,
        onOpenLicense: (LicenseDocument) -> Unit,
    ) {
        val theme = LocalThemePreference.current
        var sheet by remember { mutableStateOf<Sheet?>(null) }
        when (route) {
            SettingsRoute.ROOT -> SettingsScreen(
                state, data.version, theme, onNavigate, actions::notification,
                onTheme = { sheet = Sheet.THEME },
                onLanguage = {
                    if (state.canChangeLanguage) {
                        refreshLanguageFacts()
                        sheet = Sheet.LANGUAGE
                    }
                },
            )
            SettingsRoute.ACCESS -> AccessScreen(
                state, data.batteryExempt, onBack = onBack, onNearby = actions::nearby,
                onBattery = actions::batterySettings, onMode = { sheet = Sheet.MODE },
            )
            SettingsRoute.RECORDS -> RecordsScreen(data, onBack, actions::refreshManagement) {
                onOpenRecord(it.address, it.id)
            }
            SettingsRoute.DIAGNOSTICS -> DiagnosticsScreen(
                data, onBack, actions::refreshManagement,
                { actions.copy(context.getString(R.string.settings_diagnostics), it) },
                { sheet = Sheet.CLEAR_TRACE },
            )
            SettingsRoute.ABOUT -> AboutScreen(
                data.version, onBack, onNavigate, actions::openProjectLink, onOpenLicense,
            )
            SettingsRoute.LICENSES -> LicensesScreen(onBack, onOpenLicense) { onNavigate(SettingsRoute.NOTICES) }
            SettingsRoute.NOTICES -> AssetTextScreen(
                ThirdPartyNoticesAsset, stringResource(R.string.about_notices), onBack,
            )
            SettingsRoute.PRIVACY -> PrivacyScreen(onBack)
        }
        sheet?.let { SettingsSheet(it, theme) { sheet = null } }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun SettingsSheet(value: Sheet, theme: ThemePreference?, dismiss: () -> Unit) {
        val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden)
        val scope = rememberCoroutineScope()
        // Every way out animates the same way; dropping the sheet first would cut it off.
        val dismissSheet: () -> Unit = { scope.launch { sheetState.hide(); dismiss() } }
        ModalBottomSheet(sheetState = sheetState, onDismissRequest = dismiss) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(stringResource(when (value) {
                    Sheet.THEME -> R.string.settings_theme
                    Sheet.MODE -> R.string.settings_mode
                    Sheet.LANGUAGE -> R.string.settings_language
                    Sheet.CLEAR_TRACE -> R.string.settings_trace_clear_title
                }), style = MaterialTheme.typography.titleLarge)
                when (value) {
                    Sheet.THEME -> Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (choice in ThemePreference.entries) {
                            ChoiceSheetItem(themeName(choice), choice == theme, "theme-" + choice.name) {
                                dismissSheet()
                                // Commit what the user picked; the shown value may lag behind the store.
                                writes.launch {
                                    if (!AppearanceStore.write(context, choice)) actions.themeSaveFailed()
                                }
                            }
                        }
                    }
                    Sheet.MODE -> Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val shizuku = state.connection?.shizukuMode() == true
                        ChoiceSheetItem(stringResource(R.string.access_mode_system), !shizuku, "mode-plain",
                            stringResource(R.string.settings_mode_plain_hint)) {
                            dismissSheet()
                            if (shizuku) actions.mode(false)
                        }
                        ChoiceSheetItem(stringResource(R.string.access_mode_shizuku), shizuku, "mode-shizuku",
                            stringResource(R.string.settings_mode_shizuku_hint)) {
                            dismissSheet()
                            if (!shizuku) actions.mode(true)
                        }
                    }
                    Sheet.LANGUAGE -> LanguageChoices(state.applicationLocales, state.systemLocales,
                        state.canChangeLanguage) { locales ->
                        if (!state.canChangeLanguage) return@LanguageChoices
                        if (localeManager.applicationLocales != locales) {
                            localeManager.applicationLocales = locales
                        }
                        dismissSheet()
                    }
                    Sheet.CLEAR_TRACE -> {
                        PageNote(stringResource(R.string.settings_trace_clear_hint))
                        Button({ dismissSheet(); actions.clearDiagnostics() }, Modifier.fillMaxWidth().testTag("confirm-clear-diagnostics")) {
                            Text(stringResource(R.string.restore_diagnostics_clear))
                        }
                    }
                }
                TextButton(dismissSheet, Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.home_close))
                }
            }
        }
    }
}
