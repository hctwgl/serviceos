package com.serviceos.dispatch.api;

import java.util.Objects;
import java.util.UUID;

/** 创建 PENDING ServiceAssignment、HELD capacity reservation 与激活 saga。 */
public record PrepareServiceAssignmentCommand(
        UUID sagaId,
        UUID workOrderId,
        UUID taskId,
        ResponsibilityLevel responsibilityLevel,
        String assigneeId,
        String businessType,
        String sourceDecisionId,
        UUID supersedesServiceAssignmentId,
        String reasonCode,
        long expectedCapacityVersion,
        String authorityAssignmentId,
        long authorityVersion,
        String fenceDecisionId,
        String fencePolicyVersion
) {
    public PrepareServiceAssignmentCommand {
        sagaId = Objects.requireNonNull(sagaId, "sagaId");
        workOrderId = Objects.requireNonNull(workOrderId, "workOrderId");
        taskId = Objects.requireNonNull(taskId, "taskId");
        responsibilityLevel = Objects.requireNonNull(responsibilityLevel, "responsibilityLevel");
        assigneeId = text(assigneeId, "assigneeId", 128);
        businessType = text(businessType, "businessType", 100);
        sourceDecisionId = text(sourceDecisionId, "sourceDecisionId", 160);
        if (supersedesServiceAssignmentId != null) {
            if (reasonCode == null || !reasonCode.matches("[A-Z][A-Z0-9_]{1,99}")) {
                throw new IllegalArgumentException("reassignment reasonCode is required");
            }
        } else if (reasonCode != null) {
            throw new IllegalArgumentException("initial assignment must not carry reassignment reasonCode");
        }
        if (expectedCapacityVersion < 1) {
            throw new IllegalArgumentException("expectedCapacityVersion must be positive");
        }
        boolean hasAuthorityProof = authorityAssignmentId != null || authorityVersion != 0
                || fenceDecisionId != null || fencePolicyVersion != null;
        if (hasAuthorityProof) {
            authorityAssignmentId = text(authorityAssignmentId, "authorityAssignmentId", 160);
            if (authorityVersion < 1) {
                throw new IllegalArgumentException("authorityVersion must be positive");
            }
            fenceDecisionId = text(fenceDecisionId, "fenceDecisionId", 160);
            fencePolicyVersion = text(fencePolicyVersion, "fencePolicyVersion", 160);
            if (supersedesServiceAssignmentId == null) {
                throw new IllegalArgumentException("v2 activation proof is only supported for reassignment");
            }
        }
    }

    /** M24 兼容构造器：由调用方显式执行后续握手，不启用 M25 Inbox 协议。 */
    public PrepareServiceAssignmentCommand(
            UUID sagaId, UUID workOrderId, UUID taskId,
            ResponsibilityLevel responsibilityLevel, String assigneeId, String businessType,
            String sourceDecisionId, UUID supersedesServiceAssignmentId, String reasonCode,
            long expectedCapacityVersion
    ) {
        this(sagaId, workOrderId, taskId, responsibilityLevel, assigneeId, businessType,
                sourceDecisionId, supersedesServiceAssignmentId, reasonCode, expectedCapacityVersion,
                null, 0, null, null);
    }

    public boolean usesReliableReassignmentProtocol() {
        return authorityAssignmentId != null;
    }

    private static String text(String value, String name, int max) {
        String normalized = Objects.requireNonNull(value, name).trim();
        if (normalized.isEmpty() || normalized.length() > max) {
            throw new IllegalArgumentException(name + " must contain at most " + max + " characters");
        }
        return normalized;
    }
}
