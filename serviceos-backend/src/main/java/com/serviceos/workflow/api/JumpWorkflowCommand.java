package com.serviceos.workflow.api;

import java.util.UUID;

/** 授权人工跳转到流程定义内的任务节点。 */
public record JumpWorkflowCommand(
        String tenantId,
        UUID workOrderId,
        String targetNodeId,
        String approvalRef,
        String reasonCode,
        String correlationId,
        String causationId
) {
    public JumpWorkflowCommand {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        if (workOrderId == null) {
            throw new IllegalArgumentException("workOrderId must not be null");
        }
        if (targetNodeId == null || targetNodeId.isBlank()) {
            throw new IllegalArgumentException("targetNodeId must not be blank");
        }
        targetNodeId = targetNodeId.trim();
        if (approvalRef == null || approvalRef.isBlank() || approvalRef.length() > 128) {
            throw new IllegalArgumentException("approvalRef must be 1..128 chars");
        }
        approvalRef = approvalRef.trim();
        if (reasonCode == null || reasonCode.isBlank()) {
            throw new IllegalArgumentException("reasonCode must not be blank");
        }
        reasonCode = reasonCode.trim();
        if (correlationId == null || correlationId.isBlank()) {
            throw new IllegalArgumentException("correlationId must not be blank");
        }
        if (causationId == null || causationId.isBlank()) {
            throw new IllegalArgumentException("causationId must not be blank");
        }
    }
}
