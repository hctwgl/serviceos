package com.serviceos.evidence.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 不可变外部审核回执投影。 */
public record ExternalReviewReceiptView(
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
