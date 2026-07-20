package com.serviceos.project.api;

import com.serviceos.identity.api.CurrentPrincipal;

import java.util.UUID;

/** Project 授权只读用例。 */
public interface ProjectQueryService {
    ProjectPage list(CurrentPrincipal principal, String correlationId, ProjectQuery query);

    ProjectDetail get(CurrentPrincipal principal, String correlationId, UUID projectId);

    ProjectScopeRelationRevisionPage listScopeRevisions(
            CurrentPrincipal principal,
            String correlationId,
            UUID projectId,
            String cursor,
            int limit
    );

    /**
     * 项目创建与范围编辑用的车企/区域选择选项（按实时授权范围聚合）。
     */
    ProjectReferenceOptions referenceOptions(CurrentPrincipal principal, String correlationId);
}
