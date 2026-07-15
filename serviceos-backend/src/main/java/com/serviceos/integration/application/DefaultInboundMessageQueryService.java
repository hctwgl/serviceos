package com.serviceos.integration.application;

import com.serviceos.authorization.api.AuthorizationRequest;
import com.serviceos.authorization.api.AuthorizationService;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.integration.api.CanonicalMessageView;
import com.serviceos.integration.api.InboundEnvelopeView;
import com.serviceos.integration.api.InboundMessageQueryService;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import org.springframework.stereotype.Service;

import java.util.UUID;

/** 入站摘要查询始终按服务端 tenant 和实时 project/tenant scope 授权。 */
@Service
final class DefaultInboundMessageQueryService implements InboundMessageQueryService {
    private static final String READ = "integration.readInbound";

    private final InboundMessageRepository messages;
    private final AuthorizationService authorization;

    DefaultInboundMessageQueryService(
            InboundMessageRepository messages,
            AuthorizationService authorization
    ) {
        this.messages = messages;
        this.authorization = authorization;
    }

    @Override
    public InboundEnvelopeView getEnvelope(
            CurrentPrincipal principal,
            String correlationId,
            UUID envelopeId
    ) {
        InboundEnvelopeView view = messages.findEnvelope(principal.tenantId(), envelopeId)
                .map(InboundMessageRepository.InboundEnvelopeRecord::view)
                .orElseThrow(() -> new BusinessProblem(
                        ProblemCode.RESOURCE_NOT_FOUND, "InboundEnvelope does not exist"));
        requireRead(principal, correlationId, "InboundEnvelope", envelopeId.toString(), view.projectId());
        return view;
    }

    @Override
    public CanonicalMessageView getCanonicalMessage(
            CurrentPrincipal principal,
            String correlationId,
            UUID messageId
    ) {
        CanonicalMessageView view = messages.findCanonical(principal.tenantId(), messageId)
                .map(InboundMessageRepository.CanonicalMessageRecord::view)
                .orElseThrow(() -> new BusinessProblem(
                        ProblemCode.RESOURCE_NOT_FOUND, "CanonicalMessage does not exist"));
        requireRead(principal, correlationId, "CanonicalMessage", messageId.toString(), view.projectId());
        return view;
    }

    private void requireRead(
            CurrentPrincipal principal,
            String correlationId,
            String resourceType,
            String resourceId,
            UUID projectId
    ) {
        if (projectId == null) {
            authorization.require(principal, AuthorizationRequest.tenantCapability(
                    READ, principal.tenantId(), resourceType, resourceId), correlationId);
            return;
        }
        authorization.require(principal, AuthorizationRequest.projectCapability(
                READ, principal.tenantId(), resourceType, resourceId, projectId.toString()), correlationId);
    }
}
