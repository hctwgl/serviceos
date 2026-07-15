package com.serviceos.project.api;

import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.shared.CommandMetadata;

public interface ProjectCommandService {
    ProjectView create(CurrentPrincipal principal, CommandMetadata metadata, CreateProjectCommand command);

    ProjectScopeRelationRevisionView reviseScopeRelations(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            ReviseProjectScopeRelationsCommand command
    );
}
