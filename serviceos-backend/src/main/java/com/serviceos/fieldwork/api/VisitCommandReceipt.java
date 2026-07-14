package com.serviceos.fieldwork.api;

import java.time.Instant;
import java.util.UUID;

/** Visit 写命令的幂等冻结响应。 */
public record VisitCommandReceipt(
        UUID visitId,
        String status,
        long aggregateVersion,
        String geofenceResult,
        String policyDecision,
        Instant occurredAt
) {
}
