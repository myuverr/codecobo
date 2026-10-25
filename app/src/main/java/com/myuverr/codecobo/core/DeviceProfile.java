// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.core;
/** Saved configuration and reconnect preference. */
public record DeviceProfile(
    String address, String name, String codecName, CodecRequest request, boolean enabled, long revision) {}
