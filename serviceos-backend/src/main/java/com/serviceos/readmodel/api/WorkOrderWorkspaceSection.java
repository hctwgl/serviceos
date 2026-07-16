package com.serviceos.readmodel.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 工单工作区按需区块。M87～M90 的已接受区块五选一载荷。
 */
public record WorkOrderWorkspaceSection(
        String section,
        WorkOrderWorkspace.WorkOrderWorkspaceSourceVersions sourceVersions,
        WorkOrderWorkspace.WorkOrderWorkspaceMeta meta,
        WorkOrderWorkspaceTasksSectionData tasks,
        WorkOrderWorkspaceTimelineSectionData timeline,
        WorkOrderWorkspaceAppointmentsVisitsSectionData appointmentsVisits,
        WorkOrderWorkspaceFormsEvidenceSectionData formsEvidence,
        WorkOrderWorkspaceReviewsCorrectionsSectionData reviewsCorrections,
        WorkOrderWorkspaceIntegrationSectionData integration
) {
    public WorkOrderWorkspaceSection {
        int payloads = (tasks != null ? 1 : 0)
                + (timeline != null ? 1 : 0)
                + (appointmentsVisits != null ? 1 : 0)
                + (formsEvidence != null ? 1 : 0)
                + (reviewsCorrections != null ? 1 : 0)
                + (integration != null ? 1 : 0);
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
     * visits / appointments / contactAttempts 为 null 表示对应读权不可用；空列表表示有权但无数据。
     */
    public record WorkOrderWorkspaceAppointmentsVisitsSectionData(
            List<WorkOrderWorkspaceVisitSummary> visits,
            List<WorkOrderWorkspaceAppointmentSummary> appointments,
            List<WorkOrderWorkspaceContactAttemptSummary> contactAttempts,
            String nextCursor
    ) {
        public WorkOrderWorkspaceAppointmentsVisitsSectionData {
            visits = visits == null ? null : List.copyOf(visits);
            appointments = appointments == null ? null : List.copyOf(appointments);
            contactAttempts = contactAttempts == null ? null : List.copyOf(contactAttempts);
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

    /** 不含 contactedPartyRef、note、recordingRef 与 actorId。 */
    public record WorkOrderWorkspaceContactAttemptSummary(
            UUID contactAttemptId,
            UUID taskId,
            UUID projectId,
            UUID workOrderId,
            String channel,
            Instant startedAt,
            Instant endedAt,
            String resultCode,
            Instant nextContactAt,
            Instant createdAt
    ) {
    }

    /**
     * forms / formSubmissions / evidenceSlots / evidenceItems 为 null 表示对应读权不可用。
     */
    public record WorkOrderWorkspaceFormsEvidenceSectionData(
            List<WorkOrderWorkspaceFormSummary> forms,
            List<WorkOrderWorkspaceFormSubmissionSummary> formSubmissions,
            List<WorkOrderWorkspaceEvidenceSlotSummary> evidenceSlots,
            List<WorkOrderWorkspaceEvidenceItemSummary> evidenceItems,
            String nextCursor
    ) {
        public WorkOrderWorkspaceFormsEvidenceSectionData {
            forms = forms == null ? null : List.copyOf(forms);
            formSubmissions = formSubmissions == null ? null : List.copyOf(formSubmissions);
            evidenceSlots = evidenceSlots == null ? null : List.copyOf(evidenceSlots);
            evidenceItems = evidenceItems == null ? null : List.copyOf(evidenceItems);
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

    /** 不含 values、校验消息、prefillVersion 与 submittedBy。 */
    public record WorkOrderWorkspaceFormSubmissionSummary(
            UUID submissionId,
            UUID taskId,
            UUID projectId,
            UUID formVersionId,
            String formKey,
            int submissionVersion,
            String contentDigest,
            String validationStatus,
            int errorCount,
            int warningCount,
            Instant submittedAt
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

    /** 不含 Revision、文件引用、采集元数据与操作者。 */
    public record WorkOrderWorkspaceEvidenceItemSummary(
            UUID evidenceItemId,
            UUID taskId,
            UUID projectId,
            UUID evidenceSlotId,
            int itemOrdinal,
            String status,
            int revisionCount,
            Integer latestRevisionNumber,
            String latestRevisionStatus
    ) {
    }

    /**
     * reviews / corrections 为 null 表示对应读权不可用；空列表表示有权但无数据。
     */
    public record WorkOrderWorkspaceReviewsCorrectionsSectionData(
            List<WorkOrderWorkspaceReviewCaseSummary> reviews,
            List<WorkOrderWorkspaceCorrectionCaseSummary> corrections,
            String nextCursor
    ) {
        public WorkOrderWorkspaceReviewsCorrectionsSectionData {
            reviews = reviews == null ? null : List.copyOf(reviews);
            corrections = corrections == null ? null : List.copyOf(corrections);
        }
    }

    /** 不含审核决定 note / approvalRef 等自由文本或审批引用。 */
    public record WorkOrderWorkspaceReviewCaseSummary(
            UUID reviewCaseId,
            UUID taskId,
            UUID projectId,
            UUID evidenceSetSnapshotId,
            String scopeType,
            String origin,
            String policyVersion,
            String status,
            Instant createdAt,
            Instant decidedAt,
            UUID sourceReviewCaseId,
            String externalSubmissionRef,
            String callbackBatchRef,
            String mappingVersionId,
            UUID reopenedFromReviewCaseId,
            String reopenTriggerRef,
            List<WorkOrderWorkspaceReviewDecisionSummary> decisions
    ) {
        public WorkOrderWorkspaceReviewCaseSummary {
            decisions = List.copyOf(decisions);
        }
    }

    public record WorkOrderWorkspaceReviewDecisionSummary(
            UUID reviewDecisionId,
            int decisionOrdinal,
            String decision,
            String decisionSource,
            List<String> reasonCodes,
            Instant decidedAt
    ) {
        public WorkOrderWorkspaceReviewDecisionSummary {
            reasonCodes = List.copyOf(reasonCodes);
        }
    }

    /** 不含 waiveNote / approvalRef；补传只保留不可变轮次摘要。 */
    public record WorkOrderWorkspaceCorrectionCaseSummary(
            UUID correctionCaseId,
            UUID taskId,
            UUID projectId,
            UUID sourceReviewCaseId,
            UUID sourceReviewDecisionId,
            List<String> reasonCodes,
            UUID correctionTaskId,
            String status,
            Instant createdAt,
            UUID latestResubmissionSnapshotId,
            Instant closedAt,
            Instant waivedAt,
            List<WorkOrderWorkspaceCorrectionResubmissionSummary> resubmissions
    ) {
        public WorkOrderWorkspaceCorrectionCaseSummary {
            reasonCodes = List.copyOf(reasonCodes);
            resubmissions = List.copyOf(resubmissions);
        }
    }

    public record WorkOrderWorkspaceCorrectionResubmissionSummary(
            UUID correctionResubmissionId,
            int resubmissionOrdinal,
            UUID evidenceSetSnapshotId,
            Instant submittedAt
    ) {
    }

    /**
     * inboundEnvelopes / outboundDeliveries 为 null 表示对应读权不可用。
     */
    public record WorkOrderWorkspaceIntegrationSectionData(
            List<WorkOrderWorkspaceInboundEnvelopeSummary> inboundEnvelopes,
            List<WorkOrderWorkspaceOutboundDeliverySummary> outboundDeliveries,
            String nextCursor
    ) {
        public WorkOrderWorkspaceIntegrationSectionData {
            inboundEnvelopes = inboundEnvelopes == null ? null : List.copyOf(inboundEnvelopes);
            outboundDeliveries = outboundDeliveries == null ? null : List.copyOf(outboundDeliveries);
        }
    }

    /** 不含原文/Canonical 对象引用、签名值与传输凭据。 */
    public record WorkOrderWorkspaceInboundEnvelopeSummary(
            UUID inboundEnvelopeId,
            UUID projectId,
            String connectorVersionId,
            String messageType,
            String externalMessageId,
            String signatureStatus,
            String processingStatus,
            String mappingVersionId,
            UUID canonicalMessageId,
            String resultCode,
            String resultType,
            String resultId,
            Instant receivedAt,
            Instant completedAt,
            String correlationId
    ) {
    }

    /** 不含操作者、payload、幂等键、重放原因与审批引用。 */
    public record WorkOrderWorkspaceOutboundDeliverySummary(
            UUID deliveryId,
            UUID projectId,
            String connectorVersionId,
            String mappingVersionId,
            String businessMessageType,
            String businessKey,
            UUID sourceReviewCaseId,
            UUID sourceTaskId,
            UUID sourceWorkOrderId,
            UUID sourceSnapshotId,
            String externalOrderCode,
            UUID executionTaskId,
            String status,
            UUID clientReviewCaseId,
            UUID reviewRouteId,
            long aggregateVersion,
            Instant createdAt,
            Instant deliveredAt,
            Instant acknowledgedAt,
            List<WorkOrderWorkspaceDeliveryAttemptSummary> attempts,
            List<WorkOrderWorkspaceExternalAcknowledgementSummary> acknowledgements,
            List<WorkOrderWorkspaceDeliveryReplaySummary> replayRequests
    ) {
        public WorkOrderWorkspaceOutboundDeliverySummary {
            attempts = List.copyOf(attempts);
            acknowledgements = List.copyOf(acknowledgements);
            replayRequests = List.copyOf(replayRequests);
        }
    }

    public record WorkOrderWorkspaceDeliveryAttemptSummary(
            UUID deliveryAttemptId,
            int attemptNo,
            UUID taskExecutionAttemptId,
            LocalDate requestDate,
            String status,
            Integer httpStatus,
            String resultCode,
            Instant startedAt,
            Instant finishedAt
    ) {
    }

    public record WorkOrderWorkspaceExternalAcknowledgementSummary(
            UUID acknowledgementId,
            String acknowledgementType,
            String result,
            String reasonCode,
            String mappingVersionId,
            Instant receivedAt
    ) {
    }

    public record WorkOrderWorkspaceDeliveryReplaySummary(
            UUID replayRequestId,
            UUID executionTaskId,
            String status,
            String resultCode,
            Instant requestedAt,
            Instant startedAt,
            Instant finishedAt
    ) {
    }
}
