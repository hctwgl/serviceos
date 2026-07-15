package com.serviceos.integration.api;

import com.serviceos.identity.api.CurrentPrincipal;

import java.util.UUID;

/** 入站 Envelope 与 CanonicalMessage 的授权查询边界。 */
public interface InboundMessageQueryService {
    InboundEnvelopeView getEnvelope(CurrentPrincipal principal, String correlationId, UUID envelopeId);

    CanonicalMessageView getCanonicalMessage(CurrentPrincipal principal, String correlationId, UUID messageId);
}
