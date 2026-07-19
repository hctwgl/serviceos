package com.serviceos.dispatch.api;

import java.util.Objects;
import java.util.UUID;

/**
 * Inbox 路径：按冻结 DISPATCH 决议激活 NETWORK ServiceAssignment。
 *
 * <p>{@code sourceDecisionId} 必须锁定策略资产 versionId 与候选网点，保证可审计重放。</p>
 */
public record ActivateNetworkFromFrozenDispatchCommand(
        UUID workOrderId,
        UUID taskId,
        String networkAssigneeId,
        String businessType,
        String sourceDecisionId,
        long expectedCapacityVersion
) {
    public ActivateNetworkFromFrozenDispatchCommand {
        workOrderId = Objects.requireNonNull(workOrderId, "workOrderId");
        taskId = Objects.requireNonNull(taskId, "taskId");
        networkAssigneeId = text(networkAssigneeId, "networkAssigneeId", 128);
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
