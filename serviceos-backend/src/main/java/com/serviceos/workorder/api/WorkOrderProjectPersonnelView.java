package com.serviceos.workorder.api;

import java.time.Instant;
import java.util.UUID;

/** 工单创建时固化的项目岗位人员快照。 */
public record WorkOrderProjectPersonnelView(
        String positionCode,
        String positionName,
        UUID principalId,
        String displayName,
        String requestedRegionCode,
        String matchedRegionCode,
        String matchedRegionName,
        String matchStatus,
        boolean inherited,
        Instant matchedAt,
        String adjustmentReason
) {
}
