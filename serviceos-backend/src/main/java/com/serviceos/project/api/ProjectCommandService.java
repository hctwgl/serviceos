package com.serviceos.project.api;

import com.serviceos.shared.CommandContext;

public interface ProjectCommandService {
    ProjectView create(CommandContext context, CreateProjectCommand command);
}
