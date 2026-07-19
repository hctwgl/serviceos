package com.serviceos.integration.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** UNKNOWN OutboundDelivery 的人工确认/放弃处置摘要。 */
public record ManualDispositionView(
        UUID dispositionId,
        UUID deliveryId,
        String result,
        String reason,
        String approvalRef,
        String externalRef,
        List<String> evidenceRefs,
        String requestedBy,
        Instant requestedAt,
        long deliveryAggregateVersion
) {
}
