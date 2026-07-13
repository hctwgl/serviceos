package com.serviceos.authorization.api;

import com.serviceos.identity.api.CurrentPrincipal;

public interface FieldAuthorizationService {
    FieldAuthorizationDecision evaluate(
            CurrentPrincipal principal,
            FieldAuthorizationRequest request,
            String correlationId
    );
}
