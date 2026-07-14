package com.serviceos.appointment.api;

import java.time.Instant;
import java.util.UUID;

/** 不可变联系事实；只保存受控对象引用，不复制电话号码等敏感主数据。 */
public record ContactAttemptView(
        UUID contactAttemptId,
        UUID projectId,
        UUID workOrderId,
        UUID taskId,
        String channel,
        String contactedPartyRef,
        Instant startedAt,
        Instant endedAt,
        ContactResultCode resultCode,
        String note,
        Instant nextContactAt,
        String recordingRef,
        String actorId,
        Instant createdAt
) {
}
