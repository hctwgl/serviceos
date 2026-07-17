package com.serviceos.readmodel.web;

import com.serviceos.evidence.api.CorrectionCaseView;
import com.serviceos.evidence.api.CorrectionResubmissionView;
import com.serviceos.identity.api.CurrentPrincipalProvider;
import com.serviceos.readmodel.api.NetworkPortalCapacityItem;
import com.serviceos.readmodel.api.NetworkPortalCorrectionItem;
import com.serviceos.readmodel.api.NetworkPortalExceptionItem;
import com.serviceos.readmodel.api.NetworkPortalPage;
import com.serviceos.readmodel.api.NetworkPortalQualificationItem;
import com.serviceos.readmodel.api.NetworkPortalQueryService;
import com.serviceos.readmodel.api.NetworkPortalTaskItem;
import com.serviceos.readmodel.api.NetworkPortalTechnicianItem;
import com.serviceos.readmodel.api.NetworkPortalWorkbenchView;
import com.serviceos.readmodel.api.NetworkPortalWorkOrderItem;
import com.serviceos.shared.CorrelationIds;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Network Portal 只读 HTTP 适配器。networkId 仅来自可信头 X-Network-Context，
 * 不接受查询参数任意指定。
 */
@RestController
@RequestMapping("/api/v1/network-portal")
final class NetworkPortalController {
    private final NetworkPortalQueryService queries;
    private final CurrentPrincipalProvider principals;

    NetworkPortalController(NetworkPortalQueryService queries, CurrentPrincipalProvider principals) {
        this.queries = queries;
        this.principals = principals;
    }

    @GetMapping("/work-orders")
    ResponseEntity<NetworkPortalPage<NetworkPortalWorkOrderItem>> workOrders(
            @RequestHeader(value = "X-Network-Context", required = false) String networkContext,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        return response(queries.listWorkOrders(principals.current(), correlationId, networkContext), correlationId);
    }

    @GetMapping("/tasks")
    ResponseEntity<NetworkPortalPage<NetworkPortalTaskItem>> tasks(
            @RequestHeader(value = "X-Network-Context", required = false) String networkContext,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        return response(queries.listTasks(principals.current(), correlationId, networkContext), correlationId);
    }

    @GetMapping("/technicians")
    ResponseEntity<NetworkPortalPage<NetworkPortalTechnicianItem>> technicians(
            @RequestHeader(value = "X-Network-Context", required = false) String networkContext,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        return response(queries.listTechnicians(principals.current(), correlationId, networkContext), correlationId);
    }

    @GetMapping("/capacity")
    ResponseEntity<NetworkPortalPage<NetworkPortalCapacityItem>> capacity(
            @RequestHeader(value = "X-Network-Context", required = false) String networkContext,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        return response(queries.listCapacity(principals.current(), correlationId, networkContext), correlationId);
    }

    @GetMapping("/workbench")
    ResponseEntity<NetworkPortalWorkbenchView> workbench(
            @RequestHeader(value = "X-Network-Context", required = false) String networkContext,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        NetworkPortalWorkbenchView body = queries.workbench(principals.current(), correlationId, networkContext);
        return ResponseEntity.ok()
                .header(CorrelationIds.HEADER_NAME, correlationId)
                .body(body);
    }

    @GetMapping("/correction-cases")
    ResponseEntity<NetworkPortalPage<NetworkPortalCorrectionItem>> correctionCases(
            @RequestHeader(value = "X-Network-Context", required = false) String networkContext,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "taskId", required = false) UUID taskId,
            @RequestParam(value = "limit", required = false) Integer limit
    ) {
        return response(
                queries.listCorrections(
                        principals.current(), correlationId, networkContext, status, taskId, limit),
                correlationId);
    }

    @GetMapping("/correction-cases/{correctionCaseId}")
    ResponseEntity<CorrectionCaseResponse> correctionCase(
            @PathVariable UUID correctionCaseId,
            @RequestHeader(value = "X-Network-Context", required = false) String networkContext,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        CorrectionCaseView view = queries.getCorrection(
                principals.current(), correlationId, networkContext, correctionCaseId);
        return ResponseEntity.ok()
                .header(CorrelationIds.HEADER_NAME, correlationId)
                .body(toCorrectionResponse(view));
    }

    @GetMapping("/operational-exceptions")
    ResponseEntity<NetworkPortalPage<NetworkPortalExceptionItem>> operationalExceptions(
            @RequestHeader(value = "X-Network-Context", required = false) String networkContext,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "taskId", required = false) UUID taskId,
            @RequestParam(value = "severity", required = false) String severity,
            @RequestParam(value = "limit", required = false) Integer limit
    ) {
        return response(
                queries.listExceptions(
                        principals.current(), correlationId, networkContext,
                        status, taskId, severity, limit),
                correlationId);
    }

    @GetMapping("/operational-exceptions/{exceptionId}")
    ResponseEntity<NetworkPortalExceptionItem> operationalException(
            @PathVariable UUID exceptionId,
            @RequestHeader(value = "X-Network-Context", required = false) String networkContext,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        NetworkPortalExceptionItem body = queries.getException(
                principals.current(), correlationId, networkContext, exceptionId);
        return ResponseEntity.ok()
                .header(CorrelationIds.HEADER_NAME, correlationId)
                .body(body);
    }

    @GetMapping("/technician-qualifications")
    ResponseEntity<NetworkPortalPage<NetworkPortalQualificationItem>> technicianQualifications(
            @RequestHeader(value = "X-Network-Context", required = false) String networkContext,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "technicianProfileId", required = false) UUID technicianProfileId,
            @RequestParam(value = "limit", required = false) Integer limit
    ) {
        return response(
                queries.listQualifications(
                        principals.current(), correlationId, networkContext,
                        status, technicianProfileId, limit),
                correlationId);
    }

    @GetMapping("/technician-qualifications/{qualificationId}")
    ResponseEntity<NetworkPortalQualificationItem> technicianQualification(
            @PathVariable UUID qualificationId,
            @RequestHeader(value = "X-Network-Context", required = false) String networkContext,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        NetworkPortalQualificationItem body = queries.getQualification(
                principals.current(), correlationId, networkContext, qualificationId);
        return ResponseEntity.ok()
                .header(CorrelationIds.HEADER_NAME, correlationId)
                .body(body);
    }

    private static <T> ResponseEntity<NetworkPortalPage<T>> response(
            NetworkPortalPage<T> body, String correlationId
    ) {
        return ResponseEntity.ok()
                .header(CorrelationIds.HEADER_NAME, correlationId)
                .body(body);
    }

    /** 与 CorrectionCaseController 响应形对齐，复用 OpenAPI CorrectionCase。 */
    private static CorrectionCaseResponse toCorrectionResponse(CorrectionCaseView correction) {
        return new CorrectionCaseResponse(
                correction.correctionCaseId(), correction.projectId(), correction.taskId(),
                correction.sourceReviewCaseId(), correction.sourceReviewDecisionId(),
                correction.sourceEvidenceSetSnapshotId(), correction.sourceSnapshotContentDigest(),
                correction.reasonCodes(), correction.correctionTaskId(), correction.status(),
                correction.createdBy(),
                correction.createdAt(), correction.latestResubmissionSnapshotId(),
                correction.closedBy(), correction.closedAt(),
                correction.waivedBy(), correction.waivedAt(),
                correction.waiveApprovalRef(), correction.waiveNote(),
                correction.resubmissions() == null
                        ? List.of()
                        : correction.resubmissions().stream().map(NetworkPortalController::toRound).toList());
    }

    private static CorrectionResubmissionResponse toRound(CorrectionResubmissionView round) {
        return new CorrectionResubmissionResponse(
                round.correctionResubmissionId(), round.correctionCaseId(), round.resubmissionOrdinal(),
                round.evidenceSetSnapshotId(), round.snapshotContentDigest(),
                round.submittedBy(), round.submittedAt());
    }

    record CorrectionCaseResponse(
            UUID correctionCaseId,
            UUID projectId,
            UUID taskId,
            UUID sourceReviewCaseId,
            UUID sourceReviewDecisionId,
            UUID sourceEvidenceSetSnapshotId,
            String sourceSnapshotContentDigest,
            List<String> reasonCodes,
            UUID correctionTaskId,
            String status,
            String createdBy,
            Instant createdAt,
            UUID latestResubmissionSnapshotId,
            String closedBy,
            Instant closedAt,
            String waivedBy,
            Instant waivedAt,
            String waiveApprovalRef,
            String waiveNote,
            List<CorrectionResubmissionResponse> resubmissions
    ) {
    }

    record CorrectionResubmissionResponse(
            UUID correctionResubmissionId,
            UUID correctionCaseId,
            int resubmissionOrdinal,
            UUID evidenceSetSnapshotId,
            String snapshotContentDigest,
            String submittedBy,
            Instant submittedAt
    ) {
    }
}
