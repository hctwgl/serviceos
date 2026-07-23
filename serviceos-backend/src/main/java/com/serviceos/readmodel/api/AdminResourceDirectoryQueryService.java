package com.serviceos.readmodel.api;

import com.serviceos.identity.api.CurrentPrincipal;

public interface AdminResourceDirectoryQueryService {
    AdminResourceDirectoryPage load(CurrentPrincipal actor, String correlationId);
}
