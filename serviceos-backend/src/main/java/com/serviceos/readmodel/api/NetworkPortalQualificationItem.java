package com.serviceos.readmodel.api;

import java.time.Instant;
import java.util.UUID;

/**
 * Network Portal 本网点师傅资质安全摘要。
 * <p>
 * 字段对齐 {@code TechnicianQualificationView}；submittedBy/decidedBy 为非 PII 主体标识，可暴露。
 */
public record NetworkPortalQualificationItem(
        UUID id,
        UUID technicianProfileId,
        String qualificationCode,
        String status,
        Instant validFrom,
        Instant validTo,
        String submittedBy,
        Instant submittedAt,
        String decidedBy,
        Instant decidedAt,
        String decisionReason,
        long version
) {}
