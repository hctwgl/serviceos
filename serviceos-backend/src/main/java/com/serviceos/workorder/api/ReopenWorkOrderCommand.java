package com.serviceos.workorder.api;

import java.util.UUID;

/** 授权重开已取消工单。 */
public record ReopenWorkOrderCommand(
        String tenantId,
        UUID workOrderId,
        long expectedVersion,
        String approvalRef,
        String correlationId,
        String causationId
) {
    public ReopenWorkOrderCommand {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        if (workOrderId == null) {
            throw new IllegalArgumentException("workOrderId must not be null");
        }
        if (expectedVersion < 1) {
            throw new IllegalArgumentException("expectedVersion must be >= 1");
        }
        if (approvalRef == null || approvalRef.isBlank() || approvalRef.length() > 128) {
            throw new IllegalArgumentException("approvalRef must be 1..128 chars");
        }
        approvalRef = approvalRef.trim();
        if (correlationId == null || correlationId.isBlank()) {
            throw new IllegalArgumentException("correlationId must not be blank");
        }
        if (causationId == null || causationId.isBlank()) {
            throw new IllegalArgumentException("causationId must not be blank");
        }
    }
}
