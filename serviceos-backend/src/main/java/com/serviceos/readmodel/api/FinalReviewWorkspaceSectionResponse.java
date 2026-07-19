package com.serviceos.readmodel.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * M351 终审工作区响应：统一 {@code data + meta}，不推进业务状态。
 */
public record FinalReviewWorkspaceSectionResponse(
        FinalReviewWorkspaceData data,
        FinalReviewWorkspaceMeta meta
) {
    public record FinalReviewWorkspaceMeta(
            Instant asOf,
            String projectionCheckpoint,
            String freshnessStatus,
            long scopeVersion,
            String queryId
    ) {
    }

    public record FinalReviewWorkspaceData(
            FinalReviewWorkOrderSummary workOrder,
            FinalReviewTaskSummary reviewTask,
            FinalReviewCaseSummary reviewCase,
            FinalReviewSlaSummary sla,
            List<FinalReviewGateCheck> gateChecks,
            List<FinalReviewTargetGroup> targetGroups,
            List<FinalReviewRejectionReason> rejectionReasons,
            List<FinalReviewAllowedAction> allowedActions,
            FinalReviewTargetRef defaultTargetRef
    ) {
        public FinalReviewWorkspaceData {
            gateChecks = List.copyOf(gateChecks);
            targetGroups = List.copyOf(targetGroups);
            rejectionReasons = List.copyOf(rejectionReasons);
            allowedActions = List.copyOf(allowedActions);
        }
    }

    public record FinalReviewWorkOrderSummary(
            UUID workOrderId,
            String displayNo,
            UUID projectId,
            String projectName,
            String statusCode,
            String statusLabel,
            String serviceProductCode,
            String serviceProductName,
            String maskedCustomerName,
            String maskedCustomerPhone,
            String maskedServiceAddress,
            String networkName,
            String technicianName,
            String deviceModel,
            String nextActionLabel
    ) {
    }

    public record FinalReviewTaskSummary(
            UUID taskId,
            String status,
            String statusLabel,
            String assigneeDisplayName,
            long resourceVersion,
            boolean executionGuarded
    ) {
    }

    public record FinalReviewCaseSummary(
            UUID reviewCaseId,
            String origin,
            String status,
            long aggregateVersion,
            UUID snapshotId,
            String snapshotDigest,
            String policyVersionId,
            int targetCount
    ) {
    }

    public record FinalReviewSlaSummary(
            String status,
            Instant startedAt,
            Instant dueAt,
            String displayText
    ) {
    }

    public record FinalReviewGateCheck(
            String code,
            String label,
            String status,
            boolean blocking,
            String detail
    ) {
    }

    public record FinalReviewTargetGroup(
            String groupCode,
            String groupLabel,
            int displayOrder,
            List<FinalReviewTarget> targets
    ) {
        public FinalReviewTargetGroup {
            targets = List.copyOf(targets);
        }
    }

    public record FinalReviewTarget(
            String targetType,
            UUID targetId,
            int targetVersion,
            String requirementCode,
            String requirementLabel,
            String requirementDescription,
            String groupCode,
            String groupLabel,
            int displayOrder,
            boolean required,
            UUID slotId,
            UUID evidenceItemId,
            UUID revisionId,
            int revisionNo,
            String mimeType,
            String lifecycleStatus,
            Instant capturedAt,
            String captureSource,
            String uploaderDisplayName,
            Boolean offline,
            String locationVerdict,
            String validationReadiness,
            String validationResult,
            List<String> validationCodes,
            List<String> validationMessages,
            Map<String, Object> structuredValues
    ) {
        public FinalReviewTarget {
            validationCodes = List.copyOf(validationCodes);
            validationMessages = List.copyOf(validationMessages);
            structuredValues = structuredValues == null ? Map.of() : Map.copyOf(structuredValues);
        }
    }

    public record FinalReviewRejectionReason(
            String code,
            String label,
            boolean requiresNote
    ) {
    }

    public record FinalReviewAllowedAction(
            String action,
            boolean enabled,
            String reason
    ) {
    }

    public record FinalReviewTargetRef(
            String targetType,
            UUID targetId
    ) {
    }
}
