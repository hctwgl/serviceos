package com.serviceos.sla.api;

import java.time.Instant;
import java.util.UUID;

/** 不可变 SLA 时钟片段投影。 */
public record SlaClockSegmentItem(
        UUID segmentId,
        int segmentNo,
        String segmentType,
        Instant startedAt,
        Instant endedAt,
        Long elapsedSeconds,
        UUID startEventId,
        UUID endEventId
) {
}
