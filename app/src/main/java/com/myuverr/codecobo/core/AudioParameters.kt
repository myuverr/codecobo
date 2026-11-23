// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.core

/** AOSP parameter masks and LDAC policy values. */
object AudioParameters {
    private val bitDepths = listOf(1, 2, 4)
    private val channelModes = listOf(1, 2)
    private val ldacPolicies = 1000..1003

    fun bits(mask: Int): List<Int> = bitDepths.filter { mask and it != 0 }

    fun channels(mask: Int): List<Int> = channelModes.filter { mask and it != 0 }

    fun policies(codec: Int): List<Int> = if (codec == 4) ldacPolicies.toList() else emptyList()

    @JvmStatic
    fun policyKnown(policy: Long): Boolean = policy in ldacPolicies

    @JvmStatic
    fun single(value: Int, mask: Int): Boolean =
        value > 0 && value.countOneBits() == 1 && (mask and value) == value
}
