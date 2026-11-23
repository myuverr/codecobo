// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.platform

import android.bluetooth.BluetoothCodecConfig
import android.bluetooth.BluetoothCodecType
import android.content.Context
import android.os.Build
import com.myuverr.codecobo.R

internal object CodecNames {
    // Savitech uses the same vendor codec ID (0x4C33) for LHDC V3 and V4.
    private const val LHDC_V3_V4_ID = 0x4C33_053A_FFL

    @JvmStatic
    @Suppress("DEPRECATION") // Older Android versions and some stacks expose only the native type.
    fun name(context: Context, config: BluetoothCodecConfig): String {
        if (Build.VERSION.SDK_INT >= 35) {
            config.extendedCodecType?.let { return extendedName(it.codecId, it.codecName) }
        }
        return when (val type = config.codecType) {
            BluetoothCodecConfig.SOURCE_CODEC_TYPE_SBC -> "SBC"
            BluetoothCodecConfig.SOURCE_CODEC_TYPE_AAC -> "AAC"
            BluetoothCodecConfig.SOURCE_CODEC_TYPE_APTX -> "aptX"
            BluetoothCodecConfig.SOURCE_CODEC_TYPE_APTX_HD -> "aptX HD"
            BluetoothCodecConfig.SOURCE_CODEC_TYPE_LDAC -> "LDAC"
            BluetoothCodecConfig.SOURCE_CODEC_TYPE_LC3 -> "LC3"
            BluetoothCodecConfig.SOURCE_CODEC_TYPE_OPUS -> "Opus"
            else -> context.getString(R.string.notification_codec_unknown, type)
        }
    }

    fun extendedName(codecId: Long, platformName: String): String = when (codecId) {
        LHDC_V3_V4_ID -> "LHDC V3/V4"
        BluetoothCodecType.CODEC_ID_LHDCV5 -> "LHDC V5"
        else -> platformName
    }
}
