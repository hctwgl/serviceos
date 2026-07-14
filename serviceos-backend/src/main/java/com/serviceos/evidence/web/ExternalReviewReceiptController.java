package com.serviceos.evidence.web;

import com.serviceos.evidence.api.ExternalReviewReceiptService;
import com.serviceos.evidence.api.ExternalReviewReceiptView;
import com.serviceos.evidence.api.RecordExternalReviewReceiptCommand;
import com.serviceos.identity.api.CurrentPrincipalProvider;
import com.serviceos.shared.CommandMetadata;
import com.serviceos.shared.CorrelationIds;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 外部审核回执 HTTP 边界；仅适配层服务主体调用。 */
@RestController
@RequestMapping("/api/v1")
final class ExternalReviewReceiptController {
    private final ExternalReviewReceiptService receipts;
    private final CurrentPrincipalProvider principals;

    ExternalReviewReceiptController(
            ExternalReviewReceiptService receipts, CurrentPrincipalProvider principals
    ) {
        this.receipts = receipts;
        this.principals = principals;
    }

    @PostMapping("/internal/external-review-receipts")
    ResponseEntity<ExternalReviewReceiptResponse> record(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId,
            @RequestBody RecordExternalReviewReceiptRequest request
    ) {
        ExternalReviewReceiptView receipt = receipts.record(
                principals.current(),
                new CommandMetadata(correlationId, idempotencyKey),
                new RecordExternalReviewReceiptCommand(
                        request.reviewCaseId(), request.inboundEnvelopeId(),
                        request.canonicalMessageId(), request.externalKey(),
                        request.callbackBatchRef(), request.mappingVersionId(),
                        request.result(), request.reasonCodes(), request.affectedTargets(),
                        request.payloadRef()));
        return ResponseEntity
                .ok()
                .header(CorrelationIds.HEADER_NAME, correlationId)
                .body(response(receipt));
    }

    @GetMapping("/internal/external-review-receipts/{receiptId}")
    ExternalReviewReceiptResponse get(
            @PathVariable UUID receiptId,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        return response(receipts.get(principals.current(), correlationId, receiptId));
    }

    private ExternalReviewReceiptResponse response(ExternalReviewReceiptView receipt) {
        return new ExternalReviewReceiptResponse(
                receipt.receiptId(), receipt.projectId(), receipt.reviewCaseId(),
                receipt.reviewDecisionId(), receipt.inboundEnvelopeId(),
                receipt.canonicalMessageId(), receipt.externalKey(), receipt.callbackBatchRef(),
                receipt.mappingVersionId(), receipt.result(), receipt.reasonCodes(),
                receipt.affectedTargets(), receipt.payloadRef(), receipt.coordinationTaskId(),
                receipt.receivedBy(), receipt.receivedAt());
    }

    record RecordExternalReviewReceiptRequest(
            UUID reviewCaseId,
            String inboundEnvelopeId,
            String canonicalMessageId,
            String externalKey,
            String callbackBatchRef,
            String mappingVersionId,
            String result,
            List<String> reasonCodes,
            List<Map<String, Object>> affectedTargets,
            String payloadRef
    ) {
    }

    record ExternalReviewReceiptResponse(
            UUID receiptId,
            UUID projectId,
            UUID reviewCaseId,
            UUID reviewDecisionId,
            String inboundEnvelopeId,
            String canonicalMessageId,
            String externalKey,
            String callbackBatchRef,
            String mappingVersionId,
            String result,
            List<String> reasonCodes,
            List<Map<String, Object>> affectedTargets,
            String payloadRef,
            UUID coordinationTaskId,
            String receivedBy,
            Instant receivedAt
    ) {
    }
}
