package com.serviceos.integration.spi;

/**
 * 连接器身份。
 *
 * <p>{@code connectorCode} 标识逻辑连接器（如 {@code BYD_CPIM}）；
 * {@code connectorVersionId} 是写入 Envelope/Canonical 的不可变适配版本。</p>
 */
public record ConnectorIdentity(String connectorCode, String connectorVersionId) {
    public ConnectorIdentity {
        connectorCode = required(connectorCode, "connectorCode");
        connectorVersionId = required(connectorVersionId, "connectorVersionId");
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
