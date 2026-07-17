package com.serviceos.organization.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DirectorySyncBatchView(
        UUID id,
        UUID organizationId,
        String sourceSystem,
        String externalBatchKey,
        String batchStatus,
        Instant receivedAt,
        Instant completedAt,
        int successCount,
        int failedCount,
        int skippedCount,
        List<DirectorySyncItemView> items
) {
}
