package com.serviceos.operations.api;

import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.shared.CommandMetadata;

import java.util.UUID;

public interface OperationalExceptionWorkbenchService {
    OperationalExceptionPage list(CurrentPrincipal principal, String correlationId, OperationalExceptionQuery query);

    OperationalExceptionItem get(CurrentPrincipal principal, String correlationId, UUID exceptionId);

    OperationalExceptionAcknowledgement acknowledge(
            CurrentPrincipal principal, CommandMetadata metadata, AcknowledgeOperationalExceptionCommand command);
}
