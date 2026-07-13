package com.serviceos.shared;

/**
 * 客户端可提供但不包含身份的数据。tenant/actor 必须由应用服务从 CurrentPrincipal 注入。
 */
public record CommandMetadata(String correlationId, String idempotencyKey) {
    public CommandMetadata {
        if (correlationId == null || correlationId.isBlank() || correlationId.length() > 128) {
            throw new IllegalArgumentException("correlationId is invalid");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 160) {
            throw new IllegalArgumentException("idempotencyKey is invalid");
        }
        correlationId = correlationId.trim();
        idempotencyKey = idempotencyKey.trim();
    }
}
