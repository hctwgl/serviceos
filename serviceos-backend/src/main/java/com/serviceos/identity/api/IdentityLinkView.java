package com.serviceos.identity.api;

import java.time.Instant;
import java.util.UUID;

/** 仅 identity.readSensitive 可读取的外部身份绑定。 */
public record IdentityLinkView(
        UUID id,
        String issuer,
        String subject,
        String clientId,
        Instant linkedAt
) {
}
