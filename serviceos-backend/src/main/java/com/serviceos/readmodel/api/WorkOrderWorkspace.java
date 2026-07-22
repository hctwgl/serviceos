package com.serviceos.readmodel.api;

import com.serviceos.workorder.api.WorkOrderView;

import java.time.Instant;
import java.util.Map;
import java.util.List;
import java.util.UUID;
import com.serviceos.workorder.api.WorkOrderProjectPersonnelView;

/**
 * 工单工作区顶层快照。
 *
 * <p>M423：含服务端脱敏客户联系字段（masked*）；不得返回完整手机号/地址原文。
 * 大区块按需加载不在本响应内。</p>
 */
public record WorkOrderWorkspace(
        WorkOrderView header,
        WorkOrderWorkspaceTaskSummary currentTaskSummary,
        Map<String, String> sectionAvailability,
        String allowedActionLink,
        WorkOrderWorkspaceServiceAssignmentSummary serviceAssignmentSummary,
        WorkOrderWorkspaceSlaSummary slaSummary,
        WorkOrderWorkspaceExceptionSummary exceptionSummary,
        List<WorkOrderProjectPersonnelView> projectPersonnel,
        String timelineFreshnessStatus,
        WorkOrderWorkspaceSourceVersions sourceVersions,
        WorkOrderWorkspaceMeta meta,
        String maskedCustomerName,
        String maskedCustomerPhone,
        String maskedServiceAddress
) {
    public WorkOrderWorkspace {
        sectionAvailability = Map.copyOf(sectionAvailability);
        projectPersonnel = projectPersonnel == null ? List.of() : List.copyOf(projectPersonnel);
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

    /**
     * 当前 Task 的 ACTIVE 服务责任；网点与师傅分别保留权威生效时间。
     */
    public record WorkOrderWorkspaceServiceAssignmentSummary(
            UUID taskId,
            String networkId,
            Instant networkEffectiveFrom,
            String networkReassignmentReasonCode,
            String technicianId,
            Instant technicianEffectiveFrom,
            String technicianReassignmentReasonCode
    ) {
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
