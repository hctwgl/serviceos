package com.serviceos.workorder.api;

import java.util.UUID;

/** 取消 ACTIVE 工单。 */
public record CancelWorkOrderCommand(
        String tenantId,
        UUID workOrderId,
        long expectedVersion,
        String reasonCode,
        String approvalRef,
        String correlationId,
        String causationId
) {
    public CancelWorkOrderCommand {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        if (workOrderId == null) {
            throw new IllegalArgumentException("workOrderId must not be null");
        }
        if (expectedVersion < 1) {
            throw new IllegalArgumentException("expectedVersion must be >= 1");
        }
        if (reasonCode == null || reasonCode.isBlank() || reasonCode.length() > 64) {
            throw new IllegalArgumentException("reasonCode must be 1..64 chars");
        }
        reasonCode = reasonCode.trim();
        approvalRef = approvalRef == null || approvalRef.isBlank() ? null : approvalRef.trim();
        if (correlationId == null || correlationId.isBlank()) {
            throw new IllegalArgumentException("correlationId must not be blank");
        }
        if (causationId == null || causationId.isBlank()) {
            throw new IllegalArgumentException("causationId must not be blank");
        }
    }
}
