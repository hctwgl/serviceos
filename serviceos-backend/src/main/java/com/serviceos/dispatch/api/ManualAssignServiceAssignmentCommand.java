package com.serviceos.dispatch.api;

import java.util.Objects;
import java.util.UUID;

/**
 * Admin 试点人工初派命令：一次性激活 Task 的 NETWORK + TECHNICIAN ACTIVE 责任。
 * <p>
 * 不包含评分、硬过滤、DispatchRequest/Decision 或改派；这些仍属 Proposed 表面。
 */
public record ManualAssignServiceAssignmentCommand(
        UUID taskId,
        String networkAssigneeId,
        String technicianAssigneeId,
        String businessType
) {
    public ManualAssignServiceAssignmentCommand {
        taskId = Objects.requireNonNull(taskId, "taskId");
        networkAssigneeId = text(networkAssigneeId, "networkAssigneeId", 128);
        technicianAssigneeId = text(technicianAssigneeId, "technicianAssigneeId", 128);
        businessType = text(businessType, "businessType", 100);
        if (networkAssigneeId.equals(technicianAssigneeId)) {
            throw new IllegalArgumentException("network and technician assignees must differ");
        }
    }

    private static String text(String value, String name, int max) {
        String normalized = Objects.requireNonNull(value, name).trim();
        if (normalized.isEmpty() || normalized.length() > max) {
            throw new IllegalArgumentException(name + " must contain at most " + max + " characters");
        }
        return normalized;
    }
}
