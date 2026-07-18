package com.serviceos.readmodel.api;

import java.time.Instant;
import java.util.UUID;

/** Technician 任务详情中的联系历史安全摘要。 */
public record TechnicianPortalContactAttemptItem(
        UUID contactAttemptId,
        UUID taskId,
        String channel,
        Instant startedAt,
        Instant endedAt,
        String resultCode,
        Instant nextContactAt,
        Instant createdAt
) {
}
