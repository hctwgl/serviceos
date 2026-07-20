package com.serviceos.readmodel.api;

import com.serviceos.identity.api.CurrentPrincipal;

public interface AdminUserDirectoryQueryService {
    AdminUserDirectoryPage list(
            CurrentPrincipal actor,
            String correlationId,
            String query,
            String status,
            String cursor,
            int limit
    );
}
