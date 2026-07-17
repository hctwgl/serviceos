package com.serviceos.integration.api;

import com.serviceos.identity.api.CurrentPrincipal;

import java.util.List;
import java.util.UUID;

/** 入站 Envelope 与 CanonicalMessage 的授权查询边界。 */
public interface InboundMessageQueryService {
    InboundEnvelopeView getEnvelope(CurrentPrincipal principal, String correlationId, UUID envelopeId);

    CanonicalMessageView getCanonicalMessage(CurrentPrincipal principal, String correlationId, UUID messageId);

    /**
     * API-06 §6.1 授权入站队列：实时项目范围 + 受控筛选 + 稳定游标；仅返回已绑定 projectId
     * 的安全摘要。
     */
    InboundEnvelopeQueuePage list(
            CurrentPrincipal principal, String correlationId, InboundEnvelopeQueueQuery query);

    /**
     * 按工单列出已成功映射到该 WorkOrder 的入站 Envelope；同时要求 workOrder.read 与
     * integration.readInbound 的实时 Project Scope。
     */
    List<InboundEnvelopeView> listForWorkOrder(
            CurrentPrincipal principal, String correlationId, UUID workOrderId, int limit);
}
