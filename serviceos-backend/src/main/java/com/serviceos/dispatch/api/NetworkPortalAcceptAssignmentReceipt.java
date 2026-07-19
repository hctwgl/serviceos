package com.serviceos.dispatch.api;

import java.time.Instant;
import java.util.UUID;

/** Network Portal 网点接单回执（仅 NETWORK 责任）。 */
public record NetworkPortalAcceptAssignmentReceipt(
        UUID taskId,
        UUID workOrderId,
        UUID networkServiceAssignmentId,
        String networkAssigneeId,
        Instant occurredAt
) {
}
