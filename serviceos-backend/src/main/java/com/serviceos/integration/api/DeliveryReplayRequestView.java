package com.serviceos.integration.api;

import java.time.Instant;
import java.util.UUID;

/** 对同一不可变 Delivery 发起的一次人工重放授权及执行摘要。 */
public record DeliveryReplayRequestView(
        UUID replayRequestId,
        UUID deliveryId,
        UUID executionTaskId,
        String status,
        String reason,
        String approvalRef,
        String requestedBy,
        String resultCode,
        Instant requestedAt,
        Instant startedAt,
        Instant finishedAt
) {
}
