// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.core;
public record
    CodecCapability(int type, String name, int sampleRateMask, int bitsPerSampleMask, int channelModeMask) {
}
