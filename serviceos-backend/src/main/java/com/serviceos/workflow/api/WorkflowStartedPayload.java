package com.serviceos.workflow.api;

import java.time.Instant;
import java.util.UUID;

public record WorkflowStartedPayload(
        UUID workflowInstanceId,
        UUID projectId,
        UUID workOrderId,
        UUID configurationBundleId,
        UUID workflowDefinitionVersionId,
        String workflowKey,
        String workflowVersion,
        String definitionDigest,
        Instant startedAt
) {
}
