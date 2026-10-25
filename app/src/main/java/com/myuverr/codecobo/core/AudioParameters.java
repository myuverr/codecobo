// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.core;
import java.util.ArrayList;
import java.util.List;

/** AOSP parameter masks and LDAC policy values. */
public final class AudioParameters {
    private AudioParameters() {}
    public static List<Integer> bits(int mask) {
        return values(mask, 4);
    }
    public static List<Integer> channels(int mask) {
        return values(mask, 2);
    }
    private static List<Integer> values(int mask, int last) {
        List<Integer> result = new ArrayList<>();
        for (int bit = 1; bit <= last; bit <<= 1)
            if ((mask & bit) != 0)
                result.add(bit);
        return List.copyOf(result);
    }
    public static List<Integer> policies(int codec) {
        return codec == 4 ? List.of(1000, 1001, 1002, 1003) : List.of();
    }
    public static boolean policyKnown(long policy) {
        return policy >= 1000 && policy <= 1003;
    }
    public static boolean single(int value, int mask) {
        return value > 0 && Integer.bitCount(value) == 1 && (value & mask) == value;
    }
}
