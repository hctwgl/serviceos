package com.serviceos.configuration.api;

import java.util.Map;
import java.util.Objects;

/** 单次通知发送请求；idempotencyKey 必须稳定。 */
public record NotificationSendRequest(
        String channel,
        String recipientPrincipalId,
        String templateKey,
        String idempotencyKey,
        Map<String, String> templateVariables
) {
    public NotificationSendRequest {
        channel = required(channel, "channel");
        recipientPrincipalId = required(recipientPrincipalId, "recipientPrincipalId");
        templateKey = required(templateKey, "templateKey");
        idempotencyKey = required(idempotencyKey, "idempotencyKey");
        templateVariables = Map.copyOf(Objects.requireNonNullElse(templateVariables, Map.of()));
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
