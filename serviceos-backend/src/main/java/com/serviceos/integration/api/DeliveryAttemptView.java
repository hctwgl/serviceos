package com.serviceos.integration.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** 对外只暴露交付尝试摘要，不暴露签名、响应正文引用或凭据。 */
public record DeliveryAttemptView(
        UUID deliveryAttemptId,
        int attemptNo,
        UUID taskExecutionAttemptId,
        LocalDate requestDate,
        String requestDigest,
        String status,
        Integer httpStatus,
        String responseDigest,
        String resultCode,
        Instant startedAt,
        Instant finishedAt
) {
}
