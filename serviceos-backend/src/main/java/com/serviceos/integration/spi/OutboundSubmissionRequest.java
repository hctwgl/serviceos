package com.serviceos.integration.spi;

import java.util.Objects;
import java.util.UUID;

/** 通用出站提审请求：已加载的冻结 payload 与执行身份。 */
public record OutboundSubmissionRequest(
        String tenantId,
        UUID deliveryId,
        UUID attemptId,
        byte[] payload,
        String payloadDigest
) {
    public OutboundSubmissionRequest {
        tenantId = required(tenantId, "tenantId");
        Objects.requireNonNull(deliveryId, "deliveryId must not be null");
        Objects.requireNonNull(attemptId, "attemptId must not be null");
        Objects.requireNonNull(payload, "payload must not be null");
        if (payload.length == 0) {
            throw new IllegalArgumentException("payload must not be empty");
        }
        payload = payload.clone();
        payloadDigest = required(payloadDigest, "payloadDigest");
    }

    @Override
    public byte[] payload() {
        return payload.clone();
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
