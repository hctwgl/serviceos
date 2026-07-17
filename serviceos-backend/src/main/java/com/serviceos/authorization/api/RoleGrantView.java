package com.serviceos.authorization.api;

import java.time.Instant;
import java.util.UUID;

/** RoleGrant 治理视图；历史字段只追加，撤销不删除原区间。 */
public record RoleGrantView(
        UUID grantId,
        String principalId,
        UUID roleId,
        String roleCode,
        String scopeType,
        String scopeRef,
        String grantStatus,
        String grantEffect,
        Instant validFrom,
        Instant validTo,
        String sourceCode,
        String requestedBy,
        String requestReason,
        String approvedBy,
        Instant approvedAt,
        String rejectedBy,
        Instant rejectedAt,
        String rejectReason,
        Instant revokedAt,
        String revokedBy,
        String revokeReason,
        long version,
        Instant createdAt,
        Instant updatedAt
) {
}
