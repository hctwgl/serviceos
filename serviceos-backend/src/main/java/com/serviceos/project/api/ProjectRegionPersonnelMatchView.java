package com.serviceos.project.api;

import java.util.UUID;

/** 按工单行政区预览某一项目岗位的实际命中结果。 */
public record ProjectRegionPersonnelMatchView(
        ProjectPositionCode position,
        String positionName,
        UUID assignmentId,
        UUID principalId,
        String displayName,
        String matchedRegionCode,
        String matchedRegionName,
        boolean inherited,
        boolean dataComplete
) {
}
