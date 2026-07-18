package com.serviceos.workflow.application;

import java.util.Objects;
import java.util.UUID;

/** WAIT_EVENT 关联键模板解析；仅支持受控占位符，禁止任意表达式。 */
final class WorkflowCorrelationKeys {
    private WorkflowCorrelationKeys() {
    }

    static String resolve(
            String template,
            String tenantId,
            UUID projectId,
            UUID workOrderId,
            UUID workflowInstanceId
    ) {
        Objects.requireNonNull(template, "template");
        String resolved = template.trim()
                .replace("{tenantId}", tenantId)
                .replace("{projectId}", projectId.toString())
                .replace("{workOrderId}", workOrderId.toString())
                .replace("{workflowInstanceId}", workflowInstanceId.toString());
        if (resolved.contains("{") || resolved.contains("}")) {
            throw new IllegalArgumentException(
                    "correlationKeyTemplate contains unsupported placeholders: " + template);
        }
        if (resolved.isBlank() || resolved.length() > 300) {
            throw new IllegalArgumentException("resolved correlation key is invalid");
        }
        return resolved;
    }
}
