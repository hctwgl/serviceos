package com.serviceos.project.api;

import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.shared.CommandMetadata;

public interface ProjectCommandService {
    ProjectView create(CurrentPrincipal principal, CommandMetadata metadata, CreateProjectCommand command);

    /** 登记/更新租户车企主数据；需要 project.create。 */
    ProjectClientDirectoryItem registerClient(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            String clientCode,
            String displayName
    );

    ProjectScopeRelationRevisionView reviseScopeRelations(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            ReviseProjectScopeRelationsCommand command
    );
}
