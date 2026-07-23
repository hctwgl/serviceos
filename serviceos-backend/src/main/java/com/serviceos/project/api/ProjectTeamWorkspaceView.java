package com.serviceos.project.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** “项目团队与区域分工”页面级读模型。 */
public record ProjectTeamWorkspaceView(
        UUID projectId,
        String projectCode,
        String projectName,
        String projectStatus,
        List<ProjectTeamMemberView> members,
        List<ProjectRegionPersonnelAssignmentView> assignments,
        List<ProjectTeamCandidateView> candidates,
        List<ProjectTeamRegionOption> regions,
        List<String> allowedActions,
        Instant asOf
) {
    public ProjectTeamWorkspaceView {
        members = members == null ? List.of() : List.copyOf(members);
        assignments = assignments == null ? List.of() : List.copyOf(assignments);
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
        regions = regions == null ? List.of() : List.copyOf(regions);
        allowedActions = allowedActions == null ? List.of() : List.copyOf(allowedActions);
    }
}
