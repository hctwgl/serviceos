package com.serviceos.project.api;

import java.time.Instant;
import java.util.UUID;

/** 项目行政区某一固定岗位的当前负责人。 */
public record ProjectRegionPersonnelAssignmentView(
        UUID assignmentId,
        String regionCode,
        String regionName,
        String regionLevel,
        ProjectPositionCode position,
        String positionName,
        UUID memberId,
        UUID principalId,
        String displayName,
        boolean allowInheritance,
        Instant validFrom,
        long version,
        String changeReason,
        boolean dataComplete
) {
}
