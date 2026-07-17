package com.serviceos.organization.api;

import java.time.Instant;
import java.util.UUID;

public record ReassignmentWorkItemView(
        UUID id,
        UUID organizationId,
        UUID membershipId,
        UUID principalId,
        String workItemStatus,
        String reason,
        String createdBy,
        Instant createdAt,
        String correlationId
) {
}
