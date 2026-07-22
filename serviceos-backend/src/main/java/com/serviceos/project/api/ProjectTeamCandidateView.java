package com.serviceos.project.api;

import java.util.UUID;

/** 可加入项目的有效人员选择项。 */
public record ProjectTeamCandidateView(
        UUID principalId,
        String displayName,
        String employeeNumber,
        boolean alreadyMember
) {
}
