package com.serviceos.shared;

import java.util.Objects;

/**
 * 所有写命令必须携带的稳定调用上下文。
 *
 * @param tenantId      租户边界，任何 repository 查询都必须使用
 * @param actorId       实际操作主体，不允许由请求正文覆盖
 * @param correlationId 一次业务链路的关联标识
 * @param idempotencyKey 客户端为本次业务操作生成的幂等键
 */
public record CommandContext(
        String tenantId,
        String actorId,
        String correlationId,
        String idempotencyKey
) {
    public CommandContext {
        tenantId = requireText(tenantId, "tenantId", 64);
        actorId = requireText(actorId, "actorId", 128);
        correlationId = requireText(correlationId, "correlationId", 128);
        idempotencyKey = requireText(idempotencyKey, "idempotencyKey", 160);
    }

    private static String requireText(String value, String name, int maxLength) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw new IllegalArgumentException(name + " exceeds " + maxLength + " characters");
        }
        return trimmed;
    }
}
