package com.serviceos.identity.api;

import java.util.Objects;

/**
 * 已通过 Resource Server issuer/audience/签名验证的外部身份。
 * tenant、issuer、subject 与 clientId 只能来自受信 JWT，禁止由业务请求正文提供。
 */
public record AuthenticatedIdentity(
        String tenantId,
        String issuer,
        String subject,
        String clientId,
        CurrentPrincipal.PrincipalType principalType,
        String displayName
) {
    public AuthenticatedIdentity {
        tenantId = requireText(tenantId, "tenantId", 64);
        issuer = requireText(issuer, "issuer", 512);
        subject = requireText(subject, "subject", 255);
        clientId = requireText(clientId, "clientId", 128);
        principalType = Objects.requireNonNull(principalType, "principalType must not be null");
        displayName = displayName == null || displayName.isBlank() ? "未命名主体" : displayName.trim();
        if (displayName.length() > 200) {
            throw new IllegalArgumentException("displayName is too long");
        }
    }

    private static String requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank() || !value.equals(value.trim()) || value.length() > maxLength) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        return value;
    }
}
