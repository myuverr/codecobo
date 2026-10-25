// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.core;
/** Zero bits/channels are unknown; LDAC -1 is unknown and 0 is the system default. */
public record CodecState(int type, int sampleRate, int bitsPerSample, int channelMode, long ldacQuality) {
}
