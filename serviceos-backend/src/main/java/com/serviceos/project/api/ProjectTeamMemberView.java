package com.serviceos.project.api;

import java.time.Instant;
import java.util.UUID;

/** 项目成员的产品化只读摘要，不暴露 OIDC subject 等身份信息。 */
public record ProjectTeamMemberView(
        UUID memberId,
        UUID principalId,
        String displayName,
        String employeeNumber,
        String status,
        Instant validFrom,
        long version,
        boolean dataComplete
) {
}
