// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.core;
/** Zero leaves extended preferences unspecified. */
public record CodecRequest(int type, int sampleRate, int bitsPerSample, int channelMode, long ldacQuality) {
    public CodecRequest(int type, int sampleRate) {
        this(type, sampleRate, 0, 0, 0);
    }
}
