package com.serviceos.identity.api;

import java.time.Instant;
import java.util.UUID;

/** 普通目录可见的最小主体摘要，不包含 issuer、subject 或联系方式。 */
public record SecurityPrincipalView(
        UUID id,
        String type,
        String status,
        String displayName,
        String employeeNumber,
        long version,
        Instant createdAt,
        Instant updatedAt
) {
}
