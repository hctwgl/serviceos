package com.serviceos.project.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 工单创建前对项目区域人员分工的完整预览结果。 */
public record ProjectRegionPersonnelMatchResult(
        UUID projectId,
        String requestedRegionCode,
        String requestedRegionName,
        List<ProjectRegionPersonnelMatchView> matches,
        List<ProjectPositionCode> missingPositions,
        Instant matchedAt
) {
    public ProjectRegionPersonnelMatchResult {
        matches = matches == null ? List.of() : List.copyOf(matches);
        missingPositions = missingPositions == null ? List.of() : List.copyOf(missingPositions);
    }
}
