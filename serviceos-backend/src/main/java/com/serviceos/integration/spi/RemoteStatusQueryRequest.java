package com.serviceos.integration.spi;

import java.util.Objects;
import java.util.UUID;

/** 远端状态查询请求；payload 为冻结出站正文，供协议需要原文回放的场景。 */
public record RemoteStatusQueryRequest(
        String tenantId,
        UUID deliveryId,
        String connectorVersionId,
        String externalOrderCode,
        String businessKey,
        String payloadDigest,
        byte[] frozenPayload
) {
    public RemoteStatusQueryRequest {
        tenantId = required(tenantId, "tenantId");
        Objects.requireNonNull(deliveryId, "deliveryId");
        connectorVersionId = required(connectorVersionId, "connectorVersionId");
        externalOrderCode = required(externalOrderCode, "externalOrderCode");
        businessKey = required(businessKey, "businessKey");
        payloadDigest = required(payloadDigest, "payloadDigest");
        Objects.requireNonNull(frozenPayload, "frozenPayload");
        if (frozenPayload.length == 0) {
            throw new IllegalArgumentException("frozenPayload must not be empty");
        }
        frozenPayload = frozenPayload.clone();
    }

    @Override
    public byte[] frozenPayload() {
        return frozenPayload.clone();
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
