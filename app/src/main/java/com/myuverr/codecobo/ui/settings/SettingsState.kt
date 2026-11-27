// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.ui.settings

import android.os.LocaleList
import androidx.navigation3.runtime.NavKey
import com.myuverr.codecobo.controller.ScreenState
import com.myuverr.codecobo.ui.StatusNotificationControl

import kotlinx.serialization.Serializable

@Serializable
internal enum class SettingsRoute : NavKey {
    ROOT, ACCESS, RECORDS, DIAGNOSTICS, ABOUT, LICENSES, NOTICES, PRIVACY,
}

internal data class SettingsState(
    val connection: ScreenState? = null,
    val notification: StatusNotificationControl.State = StatusNotificationControl.State(false),
    val applicationLocales: LocaleList,
    val systemLocales: LocaleList,
) {
    val canChangeLanguage: Boolean
        get() = connection?.let { !it.busy() && !it.savePending() } != false
}
