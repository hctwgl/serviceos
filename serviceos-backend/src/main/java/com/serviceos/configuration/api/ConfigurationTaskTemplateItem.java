package com.serviceos.configuration.api;

import java.time.Instant;
import java.util.List;

/** 任务模板产品读模型条目（由 WORKFLOW 资产投影）。 */
public record ConfigurationTaskTemplateItem(
        String templateKey,
        String templateName,
        String taskTypeCode,
        String category,
        String categoryLabel,
        String executionRoleLabel,
        String assignmentStrategyLabel,
        String formSummary,
        String evidenceSummary,
        String slaSummary,
        String status,
        String statusLabel,
        int referencedWorkflowCount,
        List<String> referencedWorkflowNames,
        Instant lastUpdatedAt,
        List<String> gaps
) {
    public ConfigurationTaskTemplateItem {
        referencedWorkflowNames = List.copyOf(
                referencedWorkflowNames == null ? List.of() : referencedWorkflowNames);
        gaps = List.copyOf(gaps == null ? List.of() : gaps);
    }
}
