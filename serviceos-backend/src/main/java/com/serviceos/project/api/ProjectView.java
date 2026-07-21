package com.serviceos.project.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ProjectView(
        UUID id,
        String tenantId,
        String code,
        String clientId,
        String name,
        LocalDate startsOn,
        LocalDate endsOn,
        List<String> regionCodes,
        List<String> networkIds,
        String status,
        long version,
        Instant createdAt,
        /** soft-gate：缺 project.fulfillment.read 时为 null，前端显示「—」。 */
        Integer publishedSchemeCount,
        /** soft-gate：缺 project.fulfillment.read 时为 null，前端显示「—」。 */
        Integer draftSchemeCount
) {
}
