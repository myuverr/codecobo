// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.core

// AOSP masks: rates 1/2 = 44.1/48 kHz; depth 1 = 16 bit; channel 2 = stereo.
internal val aac44State = CodecState(1, 1, 1, 2, 0)
internal val aac48State = CodecState(1, 2, 1, 2, 0)
internal val aac48Request = CodecRequest(1, 2, 1, 2, 0)
internal val aacCapabilities = listOf(CodecCapability(1, "AAC", 3, 1, 2))

internal class FakeDevice(var actual: CodecState? = aac44State) : RestoreEngine.Host {
    var isConnected = true
    var isAssociated = true
    var capabilities = aacCapabilities
    val writes = mutableListOf<CodecRequest>()

    override fun connected() = isConnected
    override fun associated() = isAssociated
    override fun snapshot() = RestoreEngine.Ready(actual, capabilities)
    override fun read() = actual
    override fun write(request: CodecRequest) {
        writes += request
    }
}
