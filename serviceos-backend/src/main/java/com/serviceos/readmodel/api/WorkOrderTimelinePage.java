package com.serviceos.readmodel.api;

import java.time.Instant;
import java.util.List;

public record WorkOrderTimelinePage(
        long resourceVersion,
        List<WorkOrderTimelineItem> items,
        String nextCursor,
        Instant asOf,
        Instant lastProjectedAt,
        String freshnessStatus
) {
    public WorkOrderTimelinePage {
        items = List.copyOf(items);
    }
}
