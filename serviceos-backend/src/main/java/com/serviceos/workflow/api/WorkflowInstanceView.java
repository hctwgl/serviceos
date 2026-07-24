package com.serviceos.workflow.api;

import java.time.Instant;
import java.util.UUID;

public record WorkflowInstanceView(
        UUID id, UUID projectId, UUID workOrderId, UUID configurationBundleId,
        UUID workflowDefinitionVersionId, String workflowKey, String workflowVersion,
        String definitionDigest, String status,
        String currentPhaseCode, String currentPhaseName,
        String currentNodeCode, String currentNodeName, String currentResponsibility,
        long version, Instant startedAt, Instant completedAt) {
}
