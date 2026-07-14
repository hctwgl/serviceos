package com.serviceos.dispatch.api;

import java.util.Objects;

/** 创建或按版本修改某责任主体、业务类型的权威容量上限。 */
public record ConfigureCapacityCommand(
        ResponsibilityLevel responsibilityLevel,
        String assigneeId,
        String businessType,
        int maxUnits,
        long expectedVersion
) {
    public ConfigureCapacityCommand {
        responsibilityLevel = Objects.requireNonNull(responsibilityLevel, "responsibilityLevel");
        assigneeId = text(assigneeId, "assigneeId", 128);
        businessType = text(businessType, "businessType", 100);
        if (maxUnits < 1) throw new IllegalArgumentException("maxUnits must be positive");
        if (expectedVersion < 0) throw new IllegalArgumentException("expectedVersion cannot be negative");
    }

    private static String text(String value, String name, int max) {
        String normalized = Objects.requireNonNull(value, name).trim();
        if (normalized.isEmpty() || normalized.length() > max) {
            throw new IllegalArgumentException(name + " must contain at most " + max + " characters");
        }
        return normalized;
    }
}
