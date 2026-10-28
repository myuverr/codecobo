// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.ui

import android.app.LocaleManager
import android.content.Context
import android.os.Bundle
import android.os.LocaleList
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.myuverr.codecobo.R
import com.myuverr.codecobo.controller.ScreenState
import com.myuverr.codecobo.ui.components.*
import com.myuverr.codecobo.ui.management.ManagementData
import com.myuverr.codecobo.ui.settings.*
import com.myuverr.codecobo.ui.theme.*
import kotlinx.coroutines.launch

class SettingsPanel(private val context: Context, private val actions: Actions,
    private val navigationOverlay: FloatingOverlayState) {
    interface Actions {
        fun nearby()
        fun mode(enabled: Boolean)
        fun notification(enabled: Boolean)
        fun batterySettings()
        fun refreshManagement()
        fun openRecord(address: String?, id: Int)
        fun copy(title: String, text: String)
        fun clearDiagnostics()
        fun openProjectLink(url: String)
    }
    private enum class Sheet { THEME, MODE, LANGUAGE, CLEAR_TRACE }
    private val localeManager = requireNotNull(context.getSystemService(LocaleManager::class.java))
    private var state by mutableStateOf(SettingsState(
        applicationLocales = localeManager.applicationLocales, systemLocales = localeManager.systemLocales,
    ))
    private var route by mutableStateOf(SettingsRoute.ROOT)
    private var sheet by mutableStateOf<Sheet?>(null)
    private var pageActive by mutableStateOf(false)
    private var animateRoute by mutableStateOf(false)
    private var licenseFile by mutableStateOf("MPL-2.0.txt")
    private val root = ComposeView(context).apply {
        id = R.id.settings_compose
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent { CodecTheme(navigationOverlay = navigationOverlay) { SettingsRoutes() } }
    }

    fun view(): View = root
    fun render(connection: ScreenState) { state = state.copy(connection = connection) }
    fun renderData(data: ManagementData) { state = state.copy(data = data) }
    fun renderNotification(value: StatusNotificationControl.State) { state = state.copy(notification = value) }
    fun setActive(value: Boolean) {
        if (value && !pageActive) refreshLanguageFacts()
        pageActive = value
        if (!value) sheet = null
    }

    private fun refreshLanguageFacts() {
        state = state.copy(applicationLocales = localeManager.applicationLocales, systemLocales = localeManager.systemLocales)
    }

    private fun openLanguage() {
        if (!pageActive || !state.canChangeLanguage) return
        refreshLanguageFacts()
        sheet = Sheet.LANGUAGE
    }

    private fun selectLanguage(locales: LocaleList) {
        if (sheet != Sheet.LANGUAGE || !state.canChangeLanguage) return
        if (localeManager.applicationLocales != locales) localeManager.applicationLocales = locales
        refreshLanguageFacts()
        sheet = null
    }
    fun showRoot() { animateRoute = pageActive; route = SettingsRoute.ROOT; sheet = null }
    fun showAccess() { animateRoute = pageActive; route = SettingsRoute.ACCESS; sheet = null }
    fun showRecords() { animateRoute = pageActive; route = SettingsRoute.RECORDS; sheet = null }

    fun save(bundle: Bundle) {
        bundle.putString("settings.route", route.name)
        bundle.putString("settings.license", licenseFile)
    }
    fun restore(bundle: Bundle) {
        route = try { SettingsRoute.valueOf(bundle.getString("settings.route", "ROOT")) }
            catch (_: IllegalArgumentException) { SettingsRoute.ROOT }
        licenseFile = bundle.getString("settings.license", "MPL-2.0.txt").takeIf { it in ShippedLicenses } ?: "MPL-2.0.txt"
    }

    private fun navigate(next: SettingsRoute) {
        animateRoute = true
        route = next
    }

    private fun back() {
        animateRoute = true
        route = when (route) {
            SettingsRoute.LICENSES, SettingsRoute.PROJECT_LICENSE, SettingsRoute.PRIVACY -> SettingsRoute.ABOUT
            SettingsRoute.LICENSE_TEXT, SettingsRoute.NOTICES -> SettingsRoute.LICENSES
            else -> SettingsRoute.ROOT
        }
    }

    @Composable
    private fun SettingsRoutes() {
        BackHandler(pageActive && route != SettingsRoute.ROOT) { back() }
        val theme = rememberThemePreference()
        val routeKey = if (route == SettingsRoute.LICENSE_TEXT) route.name + ":" + licenseFile else route.name
        val depth = when (route) {
            SettingsRoute.ROOT -> 0
            SettingsRoute.LICENSES, SettingsRoute.PROJECT_LICENSE, SettingsRoute.PRIVACY -> 2
            SettingsRoute.LICENSE_TEXT, SettingsRoute.NOTICES -> 3
            else -> 1
        }
        ChildPageMotion(routeKey, depth, enabled = animateRoute && pageActive) { shown ->
            when (SettingsRoute.valueOf(shown.substringBefore(':'))) {
                SettingsRoute.ROOT -> SettingsScreen(state, theme, onNavigate = ::navigate,
                    onNotification = actions::notification, onTheme = { sheet = Sheet.THEME }, onLanguage = ::openLanguage)
                SettingsRoute.ACCESS -> AccessScreen(state, onBack = ::back, onNearby = actions::nearby,
                    onBattery = actions::batterySettings, onMode = { sheet = Sheet.MODE })
                SettingsRoute.RECORDS -> RecordsScreen(state, ::back, actions::refreshManagement) { actions.openRecord(it.address, it.id) }
                SettingsRoute.DIAGNOSTICS -> DiagnosticsScreen(state, ::back, actions::refreshManagement,
                    { actions.copy(context.getString(R.string.settings_diagnostics), it) }, { sheet = Sheet.CLEAR_TRACE })
                SettingsRoute.ABOUT -> AboutScreen(state, ::back, ::navigate, actions::openProjectLink)
                SettingsRoute.LICENSES -> LicensesScreen(::back,
                    { licenseFile = it; navigate(SettingsRoute.LICENSE_TEXT) }, { navigate(SettingsRoute.NOTICES) })
                SettingsRoute.LICENSE_TEXT -> AssetTextScreen("licenses/" + shown.substringAfter(':'),
                    ShippedLicenses.getValue(shown.substringAfter(':')), ::back)
                SettingsRoute.PROJECT_LICENSE -> AssetTextScreen("licenses/MPL-2.0.txt",
                    ShippedLicenses.getValue("MPL-2.0.txt"), ::back)
                SettingsRoute.NOTICES -> AssetTextScreen("THIRD_PARTY_NOTICES.md", stringResource(R.string.about_notices), ::back)
                SettingsRoute.PRIVACY -> PrivacyScreen(::back)
            }
        }
        sheet?.let { SettingsSheet(it, theme) }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun SettingsSheet(value: Sheet, theme: ThemePreference) {
        val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden)
        val scope = rememberCoroutineScope()
        ModalBottomSheet(sheetState = sheetState, onDismissRequest = { sheet = null }) {
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
                                sheet = null
                                if (choice != theme) AppearanceStore.write(context, choice)
                            }
                        }
                    }
                    Sheet.MODE -> Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val shizuku = state.connection?.shizukuMode() == true
                        ChoiceSheetItem(stringResource(R.string.access_mode_system), !shizuku, "mode-plain",
                            stringResource(R.string.settings_mode_plain_hint)) {
                            sheet = null
                            if (shizuku) actions.mode(false)
                        }
                        ChoiceSheetItem(stringResource(R.string.access_mode_shizuku), shizuku, "mode-shizuku",
                            stringResource(R.string.settings_mode_shizuku_hint)) {
                            sheet = null
                            if (!shizuku) actions.mode(true)
                        }
                    }
                    Sheet.LANGUAGE -> LanguageChoices(state.applicationLocales, state.systemLocales,
                        state.canChangeLanguage, ::selectLanguage)
                    Sheet.CLEAR_TRACE -> {
                        PageNote(stringResource(R.string.settings_trace_clear_hint))
                        Button({ sheet = null; actions.clearDiagnostics() }, Modifier.fillMaxWidth().testTag("confirm-clear-diagnostics")) {
                            Text(stringResource(R.string.restore_diagnostics_clear))
                        }
                    }
                }
                TextButton({ scope.launch { sheetState.hide(); sheet = null } }, Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.home_close))
                }
            }
        }
    }
}
