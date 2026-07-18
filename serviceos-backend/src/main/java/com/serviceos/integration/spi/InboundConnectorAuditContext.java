package com.serviceos.integration.spi;

/**
 * 入站管道审计上下文。
 *
 * <p>由适配器提供连接器主体与协议层请求摘要；管道负责写入审计动作，不猜测 OEM 身份。</p>
 */
public record InboundConnectorAuditContext(
        String actorId,
        String authPolicy,
        String capability,
        String requestDigest
) {
    public InboundConnectorAuditContext {
        actorId = required(actorId, "actorId");
        authPolicy = required(authPolicy, "authPolicy");
        capability = required(capability, "capability");
        requestDigest = required(requestDigest, "requestDigest");
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
