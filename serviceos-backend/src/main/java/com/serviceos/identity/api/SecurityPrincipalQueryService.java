package com.serviceos.identity.api;

import java.util.List;
import java.util.UUID;

public interface SecurityPrincipalQueryService {
    SecurityPrincipalPage list(
            CurrentPrincipal actor, String correlationId, String query, String status, String cursor, int limit);

    SecurityPrincipalDetail get(CurrentPrincipal actor, String correlationId, UUID principalId);

    List<IdentityLinkView> identities(CurrentPrincipal actor, String correlationId, UUID principalId);
}
