package com.serviceos.sla.application;

import java.time.Instant;
import java.util.UUID;

/** SLA 模块内部的时钟片段持久化只读行。 */
public record SlaStoredSegment(
        UUID segmentId, int segmentNo, String segmentType,
        Instant startedAt, Instant endedAt, Long elapsedSeconds,
        UUID startEventId, UUID endEventId
) {
}
