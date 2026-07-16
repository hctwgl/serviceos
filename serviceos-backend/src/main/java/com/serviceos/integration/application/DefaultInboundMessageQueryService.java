package com.serviceos.integration.application;

import com.serviceos.authorization.api.AuthorizationRequest;
import com.serviceos.authorization.api.AuthorizationService;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.integration.api.CanonicalMessageView;
import com.serviceos.integration.api.InboundEnvelopeView;
import com.serviceos.integration.api.InboundMessageQueryService;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import com.serviceos.workorder.api.WorkOrderDetail;
import com.serviceos.workorder.api.WorkOrderQueryService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/** 入站摘要查询始终按服务端 tenant 和实时 project/tenant scope 授权。 */
@Service
final class DefaultInboundMessageQueryService implements InboundMessageQueryService {
    private static final String READ = "integration.readInbound";

    private final InboundMessageRepository messages;
    private final AuthorizationService authorization;
    private final WorkOrderQueryService workOrders;

    DefaultInboundMessageQueryService(
            InboundMessageRepository messages,
            AuthorizationService authorization,
            WorkOrderQueryService workOrders
    ) {
        this.messages = messages;
        this.authorization = authorization;
        this.workOrders = workOrders;
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

    @Override
    public List<InboundEnvelopeView> listForWorkOrder(
            CurrentPrincipal principal, String correlationId, UUID workOrderId, int limit
    ) {
        validateLimit(limit);
        WorkOrderDetail workOrder = workOrders.get(principal, correlationId, workOrderId);
        UUID projectId = workOrder.workOrder().projectId();
        authorization.require(principal, AuthorizationRequest.projectCapability(
                READ, principal.tenantId(), "WorkOrder", workOrderId.toString(), projectId.toString()),
                correlationId);
        return messages.listEnvelopesByWorkOrder(
                        principal.tenantId(), projectId, workOrderId, limit)
                .stream()
                .map(InboundMessageRepository.InboundEnvelopeRecord::view)
                .toList();
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

    private static void validateLimit(int limit) {
        if (limit < 1 || limit > 100) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, "limit must be between 1 and 100");
        }
    }
}
