package com.serviceos.configuration.api;

import java.util.Objects;
import java.util.Set;

/**
 * 派单候选快照。由调用方提供已解析事实；运行时不临时读网点“当前值”。
 */
public record DispatchCandidate(
        String candidateId,
        boolean enabled,
        boolean blacklisted,
        boolean qualified,
        Set<String> brandCodes,
        Set<String> regionCodes,
        Set<String> businessTypes,
        int remainingCapacity,
        double fulfillmentRate,
        double networkScore,
        double currentLoad,
        double allocationRatioGap
) {
    public DispatchCandidate {
        candidateId = required(candidateId, "candidateId");
        brandCodes = Set.copyOf(Objects.requireNonNullElse(brandCodes, Set.of()));
        regionCodes = Set.copyOf(Objects.requireNonNullElse(regionCodes, Set.of()));
        businessTypes = Set.copyOf(Objects.requireNonNullElse(businessTypes, Set.of()));
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
