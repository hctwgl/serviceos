package com.serviceos.integration.byd.api;

import java.time.Instant;
import java.util.Objects;

/**
 * CPIM 请求认证头。字段名保持领域中立，HTTP 头名由 Web 适配层负责映射。
 */
public record BydCpimSignatureHeaders(
        String appKey,
        String nonce,
        Instant currentTime,
        String signature
) {
    public BydCpimSignatureHeaders {
        appKey = requireText(appKey, "appKey");
        nonce = requireText(nonce, "nonce");
        Objects.requireNonNull(currentTime, "currentTime");
        signature = requireText(signature, "signature").toLowerCase(java.util.Locale.ROOT);
        if (!signature.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("signature must be a 64-character SHA-256 hex string");
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
