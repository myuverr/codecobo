// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.ui.settings

import android.os.LocaleList
import com.myuverr.codecobo.controller.ScreenState
import com.myuverr.codecobo.ui.StatusNotificationControl
import com.myuverr.codecobo.ui.management.ManagementData

enum class SettingsRoute { ROOT, ACCESS, RECORDS, DIAGNOSTICS, ABOUT, LICENSES, LICENSE_TEXT, PROJECT_LICENSE, NOTICES, PRIVACY }

internal data class SettingsState(
    val connection: ScreenState? = null,
    val data: ManagementData = ManagementData(loading = true),
    val notification: StatusNotificationControl.State = StatusNotificationControl.State(false, false, ""),
    val applicationLocales: LocaleList,
    val systemLocales: LocaleList,
) {
    val canChangeLanguage: Boolean
        get() = connection?.let { !it.busy() && !it.savePending() } != false
}
