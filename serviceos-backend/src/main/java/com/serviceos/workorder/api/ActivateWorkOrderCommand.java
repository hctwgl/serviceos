package com.serviceos.workorder.api;

import java.util.Objects;
import java.util.UUID;

/** 初始 Workflow/Stage/Task 已可靠建立后激活工单。 */
public record ActivateWorkOrderCommand(
        String tenantId,
        UUID workOrderId,
        UUID triggerEventId,
        String correlationId
) {
    public ActivateWorkOrderCommand {
        tenantId = requiredText(tenantId, "tenantId", 64);
        workOrderId = Objects.requireNonNull(workOrderId, "workOrderId");
        triggerEventId = Objects.requireNonNull(triggerEventId, "triggerEventId");
        correlationId = requiredText(correlationId, "correlationId", 128);
    }

    private static String requiredText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(field + " exceeds max length " + maxLength);
        }
        return normalized;
    }
}
