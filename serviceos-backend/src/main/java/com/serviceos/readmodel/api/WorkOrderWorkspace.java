package com.serviceos.readmodel.api;

import com.serviceos.workorder.api.WorkOrderView;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * 工单工作区顶层快照。不含客户 PII；大区块按需加载不在本响应内。
 */
public record WorkOrderWorkspace(
        WorkOrderView header,
        WorkOrderWorkspaceTaskSummary currentTaskSummary,
        Map<String, String> sectionAvailability,
        String allowedActionLink,
        WorkOrderWorkspaceSlaSummary slaSummary,
        WorkOrderWorkspaceExceptionSummary exceptionSummary,
        String timelineFreshnessStatus,
        WorkOrderWorkspaceSourceVersions sourceVersions,
        WorkOrderWorkspaceMeta meta
) {
    public WorkOrderWorkspace {
        sectionAvailability = Map.copyOf(sectionAvailability);
    }

    public record WorkOrderWorkspaceTaskSummary(
            UUID taskId,
            String taskType,
            String taskKind,
            String status,
            String stageCode,
            String claimedBy,
            long version
    ) {
    }

    public record WorkOrderWorkspaceSlaSummary(int openCount, int breachedCount) {
    }

    public record WorkOrderWorkspaceExceptionSummary(int openCount) {
    }

    public record WorkOrderWorkspaceSourceVersions(long workOrderVersion) {
    }

    public record WorkOrderWorkspaceMeta(
            Instant asOf,
            String projectionCheckpoint,
            String freshnessStatus,
            String queryId
    ) {
    }
}
