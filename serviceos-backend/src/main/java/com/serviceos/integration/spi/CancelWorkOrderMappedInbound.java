package com.serviceos.integration.spi;

import java.util.Objects;

/**
 * 适配器完成反腐映射后的外部取消意图。
 *
 * <p>{@code createBusinessKey} 用于关联建单 Canonical / 工单外部订单；
 * {@code businessKey} 是本条取消消息自身的幂等键。</p>
 */
public record CancelWorkOrderMappedInbound(
        String businessKey,
        String createBusinessKey,
        String externalOrderCode,
        String clientCode,
        String reasonCode,
        String approvalRef,
        String mappingVersionId,
        byte[] canonicalPayload
) {
    public static final String MESSAGE_TYPE_CANCEL_WORK_ORDER = "CANCEL_WORK_ORDER";

    public CancelWorkOrderMappedInbound {
        businessKey = required(businessKey, "businessKey");
        createBusinessKey = required(createBusinessKey, "createBusinessKey");
        externalOrderCode = required(externalOrderCode, "externalOrderCode");
        clientCode = required(clientCode, "clientCode");
        reasonCode = required(reasonCode, "reasonCode");
        if (reasonCode.length() > 64) {
            throw new IllegalArgumentException("reasonCode must be <= 64 chars");
        }
        approvalRef = approvalRef == null || approvalRef.isBlank() ? null : approvalRef.trim();
        mappingVersionId = required(mappingVersionId, "mappingVersionId");
        Objects.requireNonNull(canonicalPayload, "canonicalPayload must not be null");
        if (canonicalPayload.length == 0) {
            throw new IllegalArgumentException("canonicalPayload must not be empty");
        }
        canonicalPayload = canonicalPayload.clone();
    }

    @Override
    public byte[] canonicalPayload() {
        return canonicalPayload.clone();
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
