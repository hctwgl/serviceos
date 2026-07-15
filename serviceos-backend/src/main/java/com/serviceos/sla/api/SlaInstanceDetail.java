package com.serviceos.sla.api;

import java.time.Instant;
import java.util.List;

/** SLA 详情包含权威实例、完整 segment 和 milestone 历史。 */
public record SlaInstanceDetail(
        SlaInstanceItem instance,
        List<SlaClockSegmentItem> segments,
        List<SlaMilestoneItem> milestones,
        Instant asOf
) {
    public SlaInstanceDetail {
        segments = List.copyOf(segments);
        milestones = List.copyOf(milestones);
    }
}
