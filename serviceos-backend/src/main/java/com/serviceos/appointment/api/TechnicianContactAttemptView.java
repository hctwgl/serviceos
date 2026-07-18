package com.serviceos.appointment.api;

import java.time.Instant;
import java.util.UUID;

/**
 * Technician Portal 联系历史安全摘要。
 *
 * <p>刻意不包含 contactedPartyRef、note、recordingRef 与 actorId，防止任务详情旁路泄露
 * 联系对象、自由文本、录音引用或人员标识。</p>
 */
public record TechnicianContactAttemptView(
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
