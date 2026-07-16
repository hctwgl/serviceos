package com.serviceos.readmodel.api;

import java.time.Instant;
import java.util.List;

/**
 * 工单工作区按需区块。M87 仅 TASKS / TIMELINE_AUDIT；二者互斥。
 */
public record WorkOrderWorkspaceSection(
        String section,
        WorkOrderWorkspace.WorkOrderWorkspaceSourceVersions sourceVersions,
        WorkOrderWorkspace.WorkOrderWorkspaceMeta meta,
        WorkOrderWorkspaceTasksSectionData tasks,
        WorkOrderWorkspaceTimelineSectionData timeline
) {
    public WorkOrderWorkspaceSection {
        if (tasks != null && timeline != null) {
            throw new IllegalArgumentException("tasks and timeline payloads are mutually exclusive");
        }
        if (tasks == null && timeline == null) {
            throw new IllegalArgumentException("section payload is required");
        }
    }

    public record WorkOrderWorkspaceTasksSectionData(
            List<WorkOrderWorkspace.WorkOrderWorkspaceTaskSummary> items,
            String nextCursor
    ) {
        public WorkOrderWorkspaceTasksSectionData {
            items = List.copyOf(items);
        }
    }

    public record WorkOrderWorkspaceTimelineSectionData(
            List<WorkOrderTimelineItem> items,
            String nextCursor,
            Instant lastProjectedAt,
            String freshnessStatus
    ) {
        public WorkOrderWorkspaceTimelineSectionData {
            items = List.copyOf(items);
        }
    }
}
