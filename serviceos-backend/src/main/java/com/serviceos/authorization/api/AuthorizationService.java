package com.serviceos.authorization.api;

import com.serviceos.identity.api.CurrentPrincipal;

public interface AuthorizationService {
    AuthorizationDecision authorize(CurrentPrincipal principal, AuthorizationRequest request, String correlationId);

    AuthorizationDecision require(CurrentPrincipal principal, AuthorizationRequest request, String correlationId);
}
