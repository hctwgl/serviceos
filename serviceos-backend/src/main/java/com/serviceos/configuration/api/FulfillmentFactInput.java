package com.serviceos.configuration.api;

/**
 * 调用方提供的最小履约事实输入（写入 cfg_fulfillment_fact）。
 */
public record FulfillmentFactInput(
        String factCode,
        String valueType,
        String valueText
) {
    public FulfillmentFactInput {
        if (factCode == null || factCode.isBlank()) {
            throw new IllegalArgumentException("factCode must not be blank");
        }
        factCode = factCode.trim();
        if (valueType == null || valueType.isBlank()) {
            throw new IllegalArgumentException("valueType must not be blank");
        }
        valueType = valueType.trim();
        if (valueText != null && valueText.length() > 500) {
            throw new IllegalArgumentException("valueText exceeds max length 500");
        }
    }
}
