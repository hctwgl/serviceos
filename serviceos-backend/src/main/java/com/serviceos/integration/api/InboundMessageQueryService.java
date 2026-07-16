package com.serviceos.integration.api;

import com.serviceos.identity.api.CurrentPrincipal;

import java.util.List;
import java.util.UUID;

/** 入站 Envelope 与 CanonicalMessage 的授权查询边界。 */
public interface InboundMessageQueryService {
    InboundEnvelopeView getEnvelope(CurrentPrincipal principal, String correlationId, UUID envelopeId);

    CanonicalMessageView getCanonicalMessage(CurrentPrincipal principal, String correlationId, UUID messageId);

    /**
     * 按工单列出已成功映射到该 WorkOrder 的入站 Envelope；同时要求 workOrder.read 与
     * integration.readInbound 的实时 Project Scope。
     */
    List<InboundEnvelopeView> listForWorkOrder(
            CurrentPrincipal principal, String correlationId, UUID workOrderId, int limit);
}
