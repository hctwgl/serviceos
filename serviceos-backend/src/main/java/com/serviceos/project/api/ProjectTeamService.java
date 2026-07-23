package com.serviceos.project.api;

import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.shared.CommandMetadata;

import java.util.UUID;

/** 项目团队成员、区域分工与人员匹配的权威用例入口。 */
public interface ProjectTeamService {
    ProjectTeamWorkspaceView workspace(CurrentPrincipal principal, String correlationId, UUID projectId);

    ProjectTeamMemberView addMember(
            CurrentPrincipal principal, CommandMetadata metadata, AddProjectTeamMemberCommand command);

    ProjectRegionPersonnelAssignmentView assign(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            AssignProjectRegionPersonnelCommand command);

    ProjectRegionPersonnelMatchResult match(
            CurrentPrincipal principal, String correlationId, UUID projectId, String regionCode);
}
