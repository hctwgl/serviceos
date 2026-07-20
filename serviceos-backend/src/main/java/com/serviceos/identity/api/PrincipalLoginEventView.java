package com.serviceos.identity.api;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** 主体最近登录项；不含 subject/密码。 */
public record PrincipalLoginEventView(
        UUID loginEventId,
        UUID principalId,
        String clientId,
        String issuer,
        String authChannel,
        String outcome,
        Instant occurredAt
) {
    public PrincipalLoginEventView {
        Objects.requireNonNull(loginEventId, "loginEventId");
        Objects.requireNonNull(principalId, "principalId");
        Objects.requireNonNull(clientId, "clientId");
        Objects.requireNonNull(issuer, "issuer");
        Objects.requireNonNull(authChannel, "authChannel");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }
}
