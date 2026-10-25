// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.core;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
public final class CodecRules {
    private CodecRules() {}
    public static List<Integer> rates(int mask) {
        List<Integer> values = new ArrayList<>();
        for (int bit = 1; bit <= 32; bit <<= 1)
            if ((mask & bit) != 0)
                values.add(bit);
        return List.copyOf(values);
    }
    public static boolean valid(CodecRequest request, List<CodecCapability> capabilities) {
        if (!sane(request))
            return false;
        int rate = request.sampleRate();
        return capabilities.stream().anyMatch(c
            -> c.type() == request.type() && (c.sampleRateMask() & rate) == rate
                && (request.bitsPerSample() == 0
                    || (c.bitsPerSampleMask() & request.bitsPerSample()) == request.bitsPerSample())
                && (request.channelMode() == 0
                    || (c.channelModeMask() & request.channelMode()) == request.channelMode()));
    }
    public static boolean matches(CodecRequest target, CodecState actual) {
        return target != null && actual != null && actual.type() == target.type()
            && actual.sampleRate() == target.sampleRate()
            && (target.bitsPerSample() == 0 || target.bitsPerSample() == actual.bitsPerSample())
            && (target.channelMode() == 0 || target.channelMode() == actual.channelMode())
            && (target.ldacQuality() == 0 || target.ldacQuality() == actual.ldacQuality());
    }
    public static boolean sane(CodecRequest request) {
        return request != null && request.type() >= 0 && AudioParameters.single(request.sampleRate(), 63)
            && (request.bitsPerSample() == 0 || AudioParameters.single(request.bitsPerSample(), 7))
            && (request.channelMode() == 0 || AudioParameters.single(request.channelMode(), 3))
            && (request.ldacQuality() == 0
                || request.type() == 4 && AudioParameters.policyKnown(request.ldacQuality()));
    }
    /** Match only fields explicitly owned by the saved configuration. */
    public static boolean savedMatches(CodecRequest target, CodecRequest saved) {
        return target != null && saved != null
            && matches(saved,
                new CodecState(target.type(), target.sampleRate(), target.bitsPerSample(),
                    target.channelMode(), target.ldacQuality()));
    }
    public static boolean needsSave(CodecRequest target, CodecState actual, CodecRequest saved) {
        if (!savedMatches(target, saved))
            return true;
        return actual != null
            && (saved.bitsPerSample() == 0 && target.bitsPerSample() != 0
                    && target.bitsPerSample() != actual.bitsPerSample()
                || saved.channelMode() == 0 && target.channelMode() != 0
                    && target.channelMode() != actual.channelMode()
                || saved.ldacQuality() == 0 && target.ldacQuality() != 0
                    && target.ldacQuality() != actual.ldacQuality());
    }
    public static List<CodecCapability> merge(List<CodecCapability> values) {
        var byType = new LinkedHashMap<Integer, CodecCapability>();
        for (CodecCapability c : values) {
            CodecCapability old = byType.get(c.type());
            byType.put(c.type(),
                old == null
                    ? c
                    : new CodecCapability(c.type(), c.name(), old.sampleRateMask() | c.sampleRateMask(),
                          old.bitsPerSampleMask() | c.bitsPerSampleMask(),
                          old.channelModeMask() | c.channelModeMask()));
        }
        return List.copyOf(byType.values());
    }
}
