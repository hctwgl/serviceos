package com.serviceos.appointment.api;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** 记录一次联系事实；时间由业务发生时刻表达，接收时间由服务端生成。 */
public record RecordContactAttemptCommand(
        UUID taskId,
        String channel,
        String contactedPartyRef,
        Instant startedAt,
        Instant endedAt,
        ContactResultCode resultCode,
        String note,
        Instant nextContactAt,
        String recordingRef
) {
    public RecordContactAttemptCommand {
        taskId = Objects.requireNonNull(taskId, "taskId");
        channel = text(channel, "channel", 80);
        contactedPartyRef = text(contactedPartyRef, "contactedPartyRef", 200);
        startedAt = Objects.requireNonNull(startedAt, "startedAt");
        endedAt = Objects.requireNonNull(endedAt, "endedAt");
        resultCode = Objects.requireNonNull(resultCode, "resultCode");
        if (endedAt.isBefore(startedAt)) throw new IllegalArgumentException("endedAt must not precede startedAt");
        note = optional(note, 500, "note");
        recordingRef = optional(recordingRef, 500, "recordingRef");
    }

    private static String text(String value, String name, int max) {
        value = Objects.requireNonNull(value, name).trim();
        if (value.isEmpty() || value.length() > max) throw new IllegalArgumentException(name + " is invalid");
        return value;
    }

    private static String optional(String value, int max, String name) {
        if (value == null) return null;
        value = value.trim();
        if (value.length() > max) throw new IllegalArgumentException(name + " is too long");
        return value.isEmpty() ? null : value;
    }
}
