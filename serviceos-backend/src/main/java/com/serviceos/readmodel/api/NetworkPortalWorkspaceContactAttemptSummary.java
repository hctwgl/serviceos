package com.serviceos.readmodel.api;

import java.time.Instant;
import java.util.UUID;

/**
 * M227：Network Portal 工作区联系尝试摘要；字段对齐 Admin
 * {@code WorkOrderWorkspaceContactAttemptSummary}，故意不含 contactedPartyRef/note/
 * recordingRef/actorId。
 */
public record NetworkPortalWorkspaceContactAttemptSummary(
        UUID contactAttemptId,
        UUID taskId,
        UUID projectId,
        UUID workOrderId,
        String channel,
        Instant startedAt,
        Instant endedAt,
        String resultCode,
        Instant nextContactAt,
        Instant createdAt
) {
}
