package com.serviceos.readmodel.api;

import java.time.Instant;
import java.util.List;

/** 工单概览使用的最近规范化时间线条目；不额外猜测“关键事件”分类。 */
public record WorkOrderActivitySummary(
        long resourceVersion,
        List<WorkOrderTimelineItem> items,
        Instant lastProjectedAt,
        WorkOrderWorkspace.WorkOrderWorkspaceMeta meta
) {
    public WorkOrderActivitySummary {
        items = List.copyOf(items);
    }
}
