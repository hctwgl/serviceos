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
}
