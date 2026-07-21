package com.serviceos.sla.api;

import com.serviceos.identity.api.CurrentPrincipal;

import java.util.UUID;

/** 经实时 Capability 与 Project/Network Scope 复核的 SLA HTTP 查询用例。 */
public interface SlaQueryService {
    SlaInstancePage list(CurrentPrincipal principal, String correlationId, SlaInstanceQuery query);

    /**
     * M452：与 {@link #list} 同授权与筛选口径的精确 COUNT（无 cursor）。
     * {@code query.limit}/{@code query.cursor} 忽略。
     */
    int count(CurrentPrincipal principal, String correlationId, SlaInstanceQuery query);

    SlaInstancePage listForWorkOrder(
            CurrentPrincipal principal, String correlationId, UUID workOrderId, String cursor, int limit);

    /**
     * M221 Network Portal：以 NETWORK {@code sla.read} 鉴权后按工单列出实例。
     * {@code projectId} 仅用于数据范围收敛（调用方已从 ACTIVE 任务解析）。
     */
    SlaInstancePage listForWorkOrderOnNetwork(
            CurrentPrincipal principal,
            String correlationId,
            UUID workOrderId,
            UUID projectId,
            UUID networkId,
            String cursor,
            int limit);

    SlaInstanceDetail get(CurrentPrincipal principal, String correlationId, UUID slaInstanceId);
}
