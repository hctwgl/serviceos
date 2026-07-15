package com.serviceos.evidence.api;

import java.util.List;
import java.util.UUID;

/** 适配层记录车企外部审核回执。 */
public record RecordExternalReviewReceiptCommand(
        UUID reviewCaseId,
        String inboundEnvelopeId,
        String canonicalMessageId,
        String externalKey,
        String callbackBatchRef,
        String mappingVersionId,
        String result,
        List<String> reasonCodes,
        List<ExternalReviewAffectedTarget> affectedTargets,
        String payloadRef
) {
}
