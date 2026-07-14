package com.serviceos.workorder.api;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Workflow 到达受支持 END 后，将工单推进为履约完成。 */
public record FulfillWorkOrderCommand(
        String tenantId,
        UUID workOrderId,
        UUID workflowInstanceId,
        UUID triggerEventId,
        String correlationId,
        List<String> completedStageCodes
) {
    public FulfillWorkOrderCommand {
        if (tenantId == null || tenantId.isBlank() || tenantId.length() > 64) {
            throw new IllegalArgumentException("tenantId must not be blank and must not exceed 64 characters");
        }
        tenantId = tenantId.trim();
        workOrderId = Objects.requireNonNull(workOrderId, "workOrderId");
        workflowInstanceId = Objects.requireNonNull(workflowInstanceId, "workflowInstanceId");
        triggerEventId = Objects.requireNonNull(triggerEventId, "triggerEventId");
        if (correlationId == null || correlationId.isBlank() || correlationId.length() > 128) {
            throw new IllegalArgumentException("correlationId must not be blank and must not exceed 128 characters");
        }
        correlationId = correlationId.trim();
        completedStageCodes = List.copyOf(Objects.requireNonNull(completedStageCodes, "completedStageCodes"));
        if (completedStageCodes.isEmpty() || completedStageCodes.stream().anyMatch(
                code -> code == null || code.isBlank() || code.length() > 100)) {
            throw new IllegalArgumentException("completedStageCodes must contain valid stage codes");
        }
    }
}
