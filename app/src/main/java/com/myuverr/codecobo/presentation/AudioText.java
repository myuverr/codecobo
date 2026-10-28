// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.presentation;

import android.content.res.Resources;
import com.myuverr.codecobo.R;
import com.myuverr.codecobo.core.AudioParameters;
import com.myuverr.codecobo.core.CodecRequest;
import com.myuverr.codecobo.core.CodecState;
import java.util.ArrayList;
import java.util.List;

/** Resource-backed audio presentation shared by Compose and read-only notifications. */
public final class AudioText {
    private final Resources resources;

    public AudioText(Resources resources) {
        this.resources = resources;
    }

    public String rateValue(int rate) {
        return switch (rate) {
            case 1 -> "44.1";
            case 2 -> "48";
            case 4 -> "88.2";
            case 8 -> "96";
            case 16 -> "176.4";
            case 32 -> "192";
            case 0 -> resources.getString(R.string.audio_rate_not_reported);
            default -> resources.getString(R.string.audio_rate_unknown, rate);
        };
    }

    public String rate(int rate) {
        return AudioParameters.single(rate, 63)
            ? resources.getString(R.string.audio_rate_format, rateValue(rate))
            : rateValue(rate);
    }

    public String bitsLabel(int bits) {
        return switch (bits) {
            case 1 -> resources.getString(R.string.audio_bits_format, "16");
            case 2 -> resources.getString(R.string.audio_bits_format, "24");
            case 4 -> resources.getString(R.string.audio_bits_format, "32");
            default -> resources.getString(R.string.audio_bits_unknown);
        };
    }

    public String channelLabel(int channel) {
        return resources.getString(switch (channel) {
            case 1 -> R.string.audio_channel_mono;
            case 2 -> R.string.audio_channel_stereo;
            default -> R.string.audio_channel_unknown;
        });
    }

    public String policyLabel(long quality) {
        int label = quality == 0 ? R.string.audio_policy_default
            : quality == 1000    ? R.string.audio_policy_quality
            : quality == 1001    ? R.string.audio_policy_balanced
            : quality == 1002    ? R.string.audio_policy_connection
            : quality == 1003    ? R.string.audio_policy_adaptive
                                 : R.string.audio_policy_unknown;
        return resources.getString(label);
    }

    public String details(CodecState value) {
        return details(
            value.bitsPerSample(), value.channelMode(), value.type() == 4 ? value.ldacQuality() : -1, true);
    }

    public String details(CodecRequest value) {
        return details(
            value.bitsPerSample(), value.channelMode(), value.type() == 4 ? value.ldacQuality() : -1, false);
    }

    private String details(int bits, int channel, long quality, boolean actual) {
        List<String> parts = new ArrayList<>();
        if (AudioParameters.single(bits, 7))
            parts.add(bitsLabel(bits));
        if (AudioParameters.single(channel, 3))
            parts.add(channelLabel(channel));
        if (AudioParameters.policyKnown(quality) || actual && quality >= 0)
            parts.add(policyLabel(quality));
        return String.join(resources.getString(R.string.audio_field_separator), parts);
    }

    public String suffix(CodecState value) {
        String text = details(value);
        return text.isEmpty() ? "" : resources.getString(R.string.audio_detail_suffix, text);
    }

    public String suffix(CodecRequest value) {
        String text = details(value);
        return text.isEmpty() ? "" : resources.getString(R.string.audio_detail_suffix, text);
    }
}
