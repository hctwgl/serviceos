package com.serviceos.readmodel.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 工单工作区按需区块。M87～M89：TASKS、TIMELINE_AUDIT、APPOINTMENTS_VISITS、FORMS_EVIDENCE
 * 四选一载荷。
 */
public record WorkOrderWorkspaceSection(
        String section,
        WorkOrderWorkspace.WorkOrderWorkspaceSourceVersions sourceVersions,
        WorkOrderWorkspace.WorkOrderWorkspaceMeta meta,
        WorkOrderWorkspaceTasksSectionData tasks,
        WorkOrderWorkspaceTimelineSectionData timeline,
        WorkOrderWorkspaceAppointmentsVisitsSectionData appointmentsVisits,
        WorkOrderWorkspaceFormsEvidenceSectionData formsEvidence
) {
    public WorkOrderWorkspaceSection {
        int payloads = (tasks != null ? 1 : 0)
                + (timeline != null ? 1 : 0)
                + (appointmentsVisits != null ? 1 : 0)
                + (formsEvidence != null ? 1 : 0);
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

    /**
     * forms / evidenceSlots 为 null 表示对应读权不可用；空列表表示有权但无数据。
     */
    public record WorkOrderWorkspaceFormsEvidenceSectionData(
            List<WorkOrderWorkspaceFormSummary> forms,
            List<WorkOrderWorkspaceEvidenceSlotSummary> evidenceSlots,
            String nextCursor
    ) {
        public WorkOrderWorkspaceFormsEvidenceSectionData {
            forms = forms == null ? null : List.copyOf(forms);
            evidenceSlots = evidenceSlots == null ? null : List.copyOf(evidenceSlots);
        }
    }

    /** 不含 definitionJson，避免工作区泄露完整表单 schema。 */
    public record WorkOrderWorkspaceFormSummary(
            UUID taskId,
            UUID formVersionId,
            String formKey,
            String semanticVersion,
            String schemaVersion,
            String contentDigest
    ) {
    }

    /** 不含 requirement/explanation JSON 与提交明细。 */
    public record WorkOrderWorkspaceEvidenceSlotSummary(
            UUID slotId,
            UUID taskId,
            UUID projectId,
            String templateKey,
            String templateVersion,
            String requirementCode,
            String occurrenceKey,
            String requirementName,
            String mediaType,
            boolean required,
            int minCount,
            Integer maxCount,
            String status,
            Instant resolvedAt,
            int slotGeneration,
            boolean active,
            String transition,
            String requiredDisposition
    ) {
    }
}
