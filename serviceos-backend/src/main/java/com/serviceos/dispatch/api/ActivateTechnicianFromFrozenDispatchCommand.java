package com.serviceos.dispatch.api;

import java.util.Objects;
import java.util.UUID;

/**
 * Inbox 路径：按冻结 DISPATCH 决议激活 TECHNICIAN ServiceAssignment。
 *
 * <p>{@code sourceDecisionId} 必须锁定策略资产 versionId 与师傅 assignee，保证可审计重放。
 * {@code technicianAssigneeId} 使用 {@code technicianProfileId} 字符串，与人工指派一致。</p>
 */
public record ActivateTechnicianFromFrozenDispatchCommand(
        UUID workOrderId,
        UUID taskId,
        String technicianAssigneeId,
        String businessType,
        String sourceDecisionId,
        long expectedCapacityVersion
) {
    public ActivateTechnicianFromFrozenDispatchCommand {
        workOrderId = Objects.requireNonNull(workOrderId, "workOrderId");
        taskId = Objects.requireNonNull(taskId, "taskId");
        technicianAssigneeId = text(technicianAssigneeId, "technicianAssigneeId", 128);
        businessType = text(businessType, "businessType", 100);
        sourceDecisionId = text(sourceDecisionId, "sourceDecisionId", 160);
        if (expectedCapacityVersion < 1) {
            throw new IllegalArgumentException("expectedCapacityVersion must be positive");
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
