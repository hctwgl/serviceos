package com.serviceos.integration.spi;

import java.util.List;
import java.util.Objects;

/**
 * 适配器完成反腐映射后的单条审核回调 Canonical 意图。
 *
 * <p>{@code itemKey} 用于 batch Envelope 的 item 结果表；{@code domainResult} 使用
 * ServiceOS 统一语义（APPROVED / REJECTED），不得直接透传 OEM 原文字段名。</p>
 */
public record ReviewCallbackMappedItem(
        String itemKey,
        String businessKey,
        String externalOrderCode,
        String domainResult,
        List<String> reasonCodes,
        String mappingVersionId,
        byte[] canonicalPayload
) {
    public static final String MESSAGE_TYPE_RECORD_CLIENT_REVIEW_RESULT = "RECORD_CLIENT_REVIEW_RESULT";

    public ReviewCallbackMappedItem {
        itemKey = required(itemKey, "itemKey");
        businessKey = required(businessKey, "businessKey");
        externalOrderCode = required(externalOrderCode, "externalOrderCode");
        domainResult = required(domainResult, "domainResult");
        if (!"APPROVED".equals(domainResult) && !"REJECTED".equals(domainResult)) {
            throw new IllegalArgumentException("domainResult must be APPROVED or REJECTED");
        }
        Objects.requireNonNull(reasonCodes, "reasonCodes must not be null");
        reasonCodes = List.copyOf(reasonCodes);
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
