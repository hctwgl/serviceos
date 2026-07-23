package com.serviceos.project.api;

import java.util.UUID;

/** 将租户内有效人员加入项目成员目录。 */
public record AddProjectTeamMemberCommand(UUID projectId, UUID principalId) {
    public AddProjectTeamMemberCommand {
        if (projectId == null || principalId == null) {
            throw new IllegalArgumentException("项目和人员不能为空");
        }
    }
}
