package com.serviceos.evidence.api;

import java.time.Instant;
import java.util.UUID;

/** 一次机器校验事实；不含原图、OCR 原文或对象存储地址。 */
public record EvidenceValidationView(
        UUID validationId,
        UUID evidenceRevisionId,
        String checkType,
        String severity,
        String result,
        String reasonCode,
        String message,
        String detailsJson,
        String validatorName,
        String validatorVersion,
        Instant createdAt
) {
}
