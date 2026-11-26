// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.ui

import android.Manifest
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.PersistableBundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.LiveData
import com.myuverr.codecobo.R
import com.myuverr.codecobo.controller.CodecController
import com.myuverr.codecobo.controller.ScreenState
import com.myuverr.codecobo.platform.BatterySettings
import com.myuverr.codecobo.platform.DevicePresence
import com.myuverr.codecobo.platform.ProfileFacts
import com.myuverr.codecobo.platform.ProfileStore
import com.myuverr.codecobo.platform.RestoreService
import com.myuverr.codecobo.platform.RestoreTrace
import com.myuverr.codecobo.ui.home.HomeSubmission
import com.myuverr.codecobo.ui.home.HomeViewModel
import com.myuverr.codecobo.ui.management.ManagementLoader
import com.myuverr.codecobo.ui.management.ManagementReader
import com.myuverr.codecobo.ui.theme.prepareWindowTheme
import java.util.concurrent.atomic.AtomicInteger

internal class MainActivity : ComponentActivity(), LibraryPanel.Actions, SettingsPanel.Actions {
    private val uiTraceId = RestoreTrace.id()
    private val home by viewModels<HomeViewModel>()
    private lateinit var library: LibraryPanel
    private lateinit var settings: SettingsPanel
    private lateinit var controller: CodecController
    private lateinit var management: ManagementLoader
    private lateinit var notificationControl: StatusNotificationControl
    private lateinit var profileFacts: LiveData<ProfileFacts>
    private var associationCode = 0
    private var closed = false
    private var lastAccessMode: ScreenState.AccessMode? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prepareWindowTheme(this)
        RestoreTrace.event(this, uiTraceId, "activity", "activity.created", "")
        library = LibraryPanel(this, this)
        settings = SettingsPanel(this, this)
        home.attachController()
        profileFacts = ProfileStore(this).facts()
        controller = CodecController(this, savedInstanceState?.getString("selectedDevice"), ::render)
        management = ManagementLoader(ManagementReader(this)) { data ->
            library.renderData(data)
            settings.renderData(data)
        }
        notificationControl = StatusNotificationControl(this, savedInstanceState) { state ->
            settings.renderNotification(state)
            management.refresh()
        }
        profileFacts.observe(this) {
            if (!closed) {
                render(controller.state())
                management.refresh()
            }
        }
        setContent {
            AppNavigation(
                home, library, settings,
                refreshManagement = ::refreshManagement,
                onSelectDevice = controller::select,
                onSubmit = ::submit,
                onOpenBluetooth = ::openBluetooth,
            )
        }
        render(controller.state())
        if (savedInstanceState == null && !getPreferences(MODE_PRIVATE).getBoolean("askedBluetooth", false)) nearby()
    }

    private fun render(state: ScreenState) {
        if (closed) return
        val facts = profileFacts.value
        home.updateSources(state, devicesKnown = controller.deviceListKnown, facts = facts)
        if (facts != null) library.render(state, facts.paused)
        settings.render(state)
        if (::notificationControl.isInitialized) settings.renderNotification(notificationControl.state())
        val accessMode = state.accessMode()
        if (accessMode == ScreenState.AccessMode.AUTHORIZATION_FAILED && accessMode != lastAccessMode) {
            Toast.makeText(this, R.string.notice_access_denied_fallback, Toast.LENGTH_LONG).show()
        }
        lastAccessMode = accessMode
    }

    private fun submit(submission: HomeSubmission) = when (submission) {
        HomeSubmission.Prepare -> controller.prepareEdit(::openAssociation, ::submissionCompleted)
        is HomeSubmission.Submit -> controller.submit(submission.request, submission.intent,
            submission.enableRestore, ::openAssociation, ::submissionCompleted)
    }

    private fun submissionCompleted(text: String) {
        if (closed) return
        Toast.makeText(this, text, Toast.LENGTH_LONG).show()
        management.refresh()
    }

    override fun refreshManagement() = management.refresh()
    override fun mode(enabled: Boolean) = controller.setShizukuMode(enabled)
    override fun notification(enabled: Boolean) = notificationControl.setEnabled(enabled)

    override fun enabled(address: String, enabled: Boolean) = controller.setAutoRestore(address, enabled) {
        submissionCompleted(getString(controller.state().messageId()))
    }

    override fun paused(paused: Boolean) = controller.pauseRestore(paused)

    override fun remove(address: String, revision: Long) = controller.removeProfile(address, revision)

    override fun batterySettings() {
        val error = BatterySettings.open(this)
        if (error.isNotEmpty()) {
            RestoreTrace.event(this, uiTraceId, "system_ui", "system.battery_settings_failed", "reason=system_ui_unavailable")
            showError(getString(R.string.notice_battery_settings_unavailable), getString(R.string.notice_battery_settings_manual))
        }
    }

    override fun clearDiagnostics() {
        RestoreTrace.clear(this)
        management.refresh()
    }

    override fun openProjectLink(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        } catch (_: ActivityNotFoundException) {
            showProjectLinkError(url)
        } catch (_: SecurityException) {
            showProjectLinkError(url)
        }
    }

    private fun showProjectLinkError(url: String) =
        showError(getString(R.string.about_link_error_title), getString(R.string.about_link_error_message, url))

    override fun copy(title: String, text: String) {
        val clip = ClipData.newPlainText(title, text)
        clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
        getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
    }

    private fun openBluetooth() = openSettings(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))

    override fun onResume() {
        super.onResume()
        RestoreTrace.event(this, uiTraceId, "activity", "activity.resumed", "")
        controller.refresh()
        RestoreService.requestCheck(this, null, "activity_resumed")
        notificationControl.resume()
        management.refresh()
        DevicePresence.sync(this)
    }

    override fun onPause() {
        RestoreTrace.event(this, uiTraceId, "activity", "activity.paused", "")
        super.onPause()
    }

    override fun nearby() {
        if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            controller.refresh()
            return
        }
        val preferences = getPreferences(MODE_PRIVATE)
        if (preferences.getBoolean("askedBluetooth", false) &&
            !shouldShowRequestPermissionRationale(Manifest.permission.BLUETOOTH_CONNECT)
        ) {
            AlertDialog.Builder(this)
                .setTitle(R.string.notice_nearby_permission_denied)
                .setMessage(R.string.notice_nearby_permission_manual)
                .setPositiveButton(R.string.notice_open_app_settings) { _, _ ->
                    openSettings(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri()))
                }
                .setNegativeButton(R.string.notice_cancel, null)
                .show()
        } else {
            preferences.edit { putBoolean("askedBluetooth", true) }
            requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), BLUETOOTH_PERMISSION)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        controller.refresh()
        if (requestCode == BLUETOOTH_PERMISSION) RestoreService.requestCheck(this, null, "nearby_permission_returned")
        notificationControl.permissionResult(requestCode)
        management.refresh()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (associationCode != 0 && requestCode == associationCode) {
            associationCode = 0
            controller.associationActivityResult(resultCode == RESULT_OK)
        }
    }

    private fun openAssociation(sender: IntentSender) {
        // Late system results must not match a newer request.
        associationCode = ASSOCIATION_CODES.incrementAndGet()
        if (associationCode > 65535) {
            associationCode = 0
            controller.associationActivityResult(false)
            showError(getString(R.string.notice_association_ui_unavailable), getString(R.string.notice_reopen_app_retry))
            return
        }
        try {
            startIntentSenderForResult(sender, associationCode, null, 0, 0, 0)
        } catch (error: IntentSender.SendIntentException) {
            associationFailed(error)
        } catch (error: RuntimeException) {
            associationFailed(error)
        }
    }

    private fun associationFailed(error: Exception) {
        associationCode = 0
        controller.associationActivityResult(false)
        RestoreTrace.failure(this, uiTraceId, "system_ui", "system.association_ui_failed", error)
        showError(getString(R.string.notice_association_ui_unavailable), getString(R.string.notice_reconnect_retry))
    }

    private fun openSettings(intent: Intent) {
        try {
            startActivity(intent)
        } catch (error: ActivityNotFoundException) {
            RestoreTrace.failure(this, uiTraceId, "system_ui", "system.settings_failed", error)
            showError(getString(R.string.notice_system_settings_unavailable), getString(R.string.notice_system_settings_manual))
        }
    }

    private fun showError(title: String, text: String) {
        AlertDialog.Builder(this).setTitle(title).setMessage(text).setPositiveButton(R.string.notice_close, null).show()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        notificationControl.save(outState)
        outState.putString("selectedDevice", controller.state().selectedAddress())
    }

    override fun onDestroy() {
        closed = true
        management.close()
        notificationControl.close()
        controller.close()
        super.onDestroy()
    }

    private companion object {
        const val BLUETOOTH_PERMISSION = 10
        val ASSOCIATION_CODES = AtomicInteger(1000)
    }
}
