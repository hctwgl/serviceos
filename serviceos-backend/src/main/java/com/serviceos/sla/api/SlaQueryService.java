package com.serviceos.sla.api;

import com.serviceos.identity.api.CurrentPrincipal;

import java.util.UUID;

/** 经实时 Capability 与 Project Scope 复核的 SLA HTTP 查询用例。 */
public interface SlaQueryService {
    SlaInstancePage list(CurrentPrincipal principal, String correlationId, SlaInstanceQuery query);

    SlaInstancePage listForWorkOrder(
            CurrentPrincipal principal, String correlationId, UUID workOrderId, String cursor, int limit);

    SlaInstanceDetail get(CurrentPrincipal principal, String correlationId, UUID slaInstanceId);
}
