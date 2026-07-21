package com.serviceos.organization.api;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 主体任职产品摘要：任职事实 + 组织/单元显示名（禁止前端拼 UUID）。
 */
public record OrgMembershipSummaryView(
        UUID id,
        UUID organizationId,
        String organizationCode,
        String organizationName,
        String organizationAuthorityMode,
        UUID orgUnitId,
        String unitCode,
        String unitName,
        UUID principalId,
        String membershipType,
        String status,
        Instant validFrom,
        Instant validTo,
        long version,
        Instant createdAt
) {
    public OrgMembershipSummaryView {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(organizationId, "organizationId");
        Objects.requireNonNull(organizationCode, "organizationCode");
        Objects.requireNonNull(organizationName, "organizationName");
        Objects.requireNonNull(orgUnitId, "orgUnitId");
        Objects.requireNonNull(unitCode, "unitCode");
        Objects.requireNonNull(unitName, "unitName");
        Objects.requireNonNull(principalId, "principalId");
        Objects.requireNonNull(membershipType, "membershipType");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(validFrom, "validFrom");
        Objects.requireNonNull(createdAt, "createdAt");
    }
}
