package com.serviceos.network.domain;

import com.serviceos.network.api.TechnicianQualificationView;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** 师傅资质记录，只追加提交与总部裁决。 */
public record TechnicianQualification(
        UUID id,
        String tenantId,
        UUID technicianProfileId,
        String qualificationCode,
        Status status,
        Instant validFrom,
        Instant validTo,
        String submittedBy,
        Instant submittedAt,
        String decidedBy,
        Instant decidedAt,
        String decisionReason,
        long version
) {
    public enum Status { PENDING, APPROVED, REJECTED, EXPIRED }

    public TechnicianQualification {
        Objects.requireNonNull(id, "id must not be null");
        tenantId = requireText(tenantId, "tenantId", 64);
        Objects.requireNonNull(technicianProfileId, "technicianProfileId must not be null");
        qualificationCode = requireText(qualificationCode, "qualificationCode", 64);
        status = Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(validFrom, "validFrom must not be null");
        submittedBy = requireText(submittedBy, "submittedBy", 128);
        Objects.requireNonNull(submittedAt, "submittedAt must not be null");
        if (version < 1) throw new IllegalArgumentException("version must be positive");
    }

    public TechnicianQualificationView toView() {
        return new TechnicianQualificationView(id, technicianProfileId, qualificationCode, status.name(),
                validFrom, validTo, submittedBy, submittedAt, decidedBy, decidedAt, decisionReason, version);
    }

    private static String requireText(String value, String field, int max) {
        if (value == null || value.isBlank() || !value.equals(value.trim()) || value.length() > max) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        return value;
    }
}
