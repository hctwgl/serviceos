package com.serviceos.workorder.api;

import com.serviceos.identity.api.CurrentPrincipal;

import java.util.UUID;

/** WorkOrder 授权只读用例。 */
public interface WorkOrderQueryService {
    WorkOrderPage list(CurrentPrincipal principal, String correlationId, WorkOrderQuery query);
    WorkOrderDetail get(CurrentPrincipal principal, String correlationId, UUID workOrderId);

    /**
     * M351：返回服务端脱敏后的客户联系摘要；完整原文永不离开 workorder 模块边界。
     */
    WorkOrderMaskedContactView getMaskedContact(
            CurrentPrincipal principal, String correlationId, UUID workOrderId);

    /**
     * M424：网点协作脱敏客户联系摘要。
     *
     * <p>强制 NETWORK {@code networkTask.read}；完整原文永不离开 workorder 模块边界。
     * 调用方（Network Portal 工作区）必须已证明该工单属于本网点 ACTIVE NETWORK 责任，
     * 本端口不再查询派工以免 workorder → dispatch 依赖。</p>
     */
    WorkOrderMaskedContactView getMaskedContactForNetwork(
            CurrentPrincipal principal, String correlationId, UUID networkId, UUID workOrderId);
}
