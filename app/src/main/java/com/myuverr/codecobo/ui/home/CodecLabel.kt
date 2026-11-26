// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.ui.home

internal data class CodecLabel(val family: String, val variant: String?)

/**
 * Splits "aptX HD" so the family can lead and the variant can follow at a smaller size.
 */
internal fun codecLabel(name: String): CodecLabel {
    val family = "aptX"
    val prefix = "$family "
    return if (name.startsWith(prefix)) CodecLabel(family, name.removePrefix(prefix))
    else CodecLabel(name, null)
}
