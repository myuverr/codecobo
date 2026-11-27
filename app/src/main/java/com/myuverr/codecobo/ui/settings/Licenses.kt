// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.ui.settings

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** A text document shipped in the `licenses` asset folder. */
internal data class LicenseDocument(val file: String, val title: String) {
    val assetPath: String get() = "licenses/$file"
}

internal val ProjectLicense = LicenseDocument("MPL-2.0.txt", "Mozilla Public License 2.0")

internal val ThirdPartyLicenses = listOf(
    LicenseDocument("Apache-2.0.txt", "Apache License 2.0"),
    LicenseDocument("Shizuku-MIT.txt", "Shizuku API · MIT"),
    LicenseDocument("PHOSPHOR-MIT.txt", "Phosphor · MIT"),
    LicenseDocument("Lucide-LICENSE.txt", "Lucide · ISC / MIT"),
    LicenseDocument("Lottie-Apache-2.0.txt", "Lottie · Apache 2.0"),
)

internal const val ThirdPartyNoticesAsset = "THIRD_PARTY_NOTICES.md"

@Serializable
internal data class LicenseDocumentKey(val file: String) : NavKey

internal fun licenseDocument(file: String): LicenseDocument =
    (listOf(ProjectLicense) + ThirdPartyLicenses).first { it.file == file }
