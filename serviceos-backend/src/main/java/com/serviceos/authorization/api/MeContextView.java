package com.serviceos.authorization.api;

import java.util.List;

public record MeContextView(
        String contextId,
        String portal,
        String personaType,
        String scopeType,
        String scopeRef,
        MeContextScopeSummary scopeSummary,
        String version
) {
    public record MeContextScopeSummary(
            List<String> organizationIds,
            List<String> networkIds,
            List<String> projectIds
    ) {
        public MeContextScopeSummary {
            organizationIds = organizationIds == null ? List.of() : List.copyOf(organizationIds);
            networkIds = networkIds == null ? List.of() : List.copyOf(networkIds);
            projectIds = projectIds == null ? List.of() : List.copyOf(projectIds);
        }
    }
}
