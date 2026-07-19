package com.serviceos.integration.spi;

import java.time.LocalDate;
import java.util.Objects;

/** 适配器完成协议签名后的一次性发送请求。 */
public record SignedOutboundRequest(
        byte[] payload,
        String nonce,
        LocalDate requestDate,
        String signature,
        String credentialVersionId,
        String appKey
) {
    public SignedOutboundRequest {
        Objects.requireNonNull(payload, "payload must not be null");
        if (payload.length == 0) {
            throw new IllegalArgumentException("payload must not be empty");
        }
        payload = payload.clone();
        nonce = required(nonce, "nonce");
        Objects.requireNonNull(requestDate, "requestDate must not be null");
        signature = required(signature, "signature");
        credentialVersionId = required(credentialVersionId, "credentialVersionId");
        appKey = required(appKey, "appKey");
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
