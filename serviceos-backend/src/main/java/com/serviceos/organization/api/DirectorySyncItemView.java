package com.serviceos.organization.api;

import java.time.Instant;
import java.util.UUID;

public record DirectorySyncItemView(
        UUID id,
        int itemIndex,
        String operationType,
        String sourceKey,
        long externalVersion,
        String itemStatus,
        String resultCode,
        String resultMessage,
        String resourceType,
        UUID resourceId,
        Instant processedAt
) {
}
