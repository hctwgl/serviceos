package com.serviceos.readmodel.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 工单工作区按需区块。M87/M88：TASKS、TIMELINE_AUDIT、APPOINTMENTS_VISITS 三选一载荷。
 */
public record WorkOrderWorkspaceSection(
        String section,
        WorkOrderWorkspace.WorkOrderWorkspaceSourceVersions sourceVersions,
        WorkOrderWorkspace.WorkOrderWorkspaceMeta meta,
        WorkOrderWorkspaceTasksSectionData tasks,
        WorkOrderWorkspaceTimelineSectionData timeline,
        WorkOrderWorkspaceAppointmentsVisitsSectionData appointmentsVisits
) {
    public WorkOrderWorkspaceSection {
        int payloads = (tasks != null ? 1 : 0)
                + (timeline != null ? 1 : 0)
                + (appointmentsVisits != null ? 1 : 0);
        if (payloads != 1) {
            throw new IllegalArgumentException("exactly one section payload is required");
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

    /**
     * visits / appointments 为 null 表示对应读权不可用；空列表表示有权但无数据。
     */
    public record WorkOrderWorkspaceAppointmentsVisitsSectionData(
            List<WorkOrderWorkspaceVisitSummary> visits,
            List<WorkOrderWorkspaceAppointmentSummary> appointments,
            String nextCursor
    ) {
        public WorkOrderWorkspaceAppointmentsVisitsSectionData {
            visits = visits == null ? null : List.copyOf(visits);
            appointments = appointments == null ? null : List.copyOf(appointments);
        }
    }

    public record WorkOrderWorkspaceVisitSummary(
            UUID visitId,
            UUID taskId,
            UUID appointmentId,
            int visitSequence,
            String technicianId,
            String networkId,
            String status,
            Instant checkInCapturedAt,
            Instant checkInReceivedAt,
            String geofenceResult,
            String policyDecision,
            Instant checkOutCapturedAt,
            Instant checkOutReceivedAt,
            String resultCode,
            String exceptionCode,
            long aggregateVersion
    ) {
    }

    public record WorkOrderWorkspaceAppointmentSummary(
            UUID appointmentId,
            UUID taskId,
            String type,
            String status,
            String assignedNetworkId,
            String technicianId,
            int currentRevisionNo,
            Instant windowStart,
            Instant windowEnd,
            String timezone,
            Integer estimatedDurationMinutes,
            long aggregateVersion,
            Instant createdAt
    ) {
    }
}
