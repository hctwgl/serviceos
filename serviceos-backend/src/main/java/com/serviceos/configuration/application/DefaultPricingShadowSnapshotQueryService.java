package com.serviceos.configuration.application;

import com.serviceos.authorization.api.AuthorizationRequest;
import com.serviceos.authorization.api.AuthorizationService;
import com.serviceos.configuration.api.PricingShadowSnapshotQueryService;
import com.serviceos.configuration.api.PricingShadowSnapshotView;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import com.serviceos.workorder.api.WorkOrderScope;
import com.serviceos.workorder.api.WorkOrderScopeQuery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** SHADOW 定价试算快照只读查询；授权基于 pricing.snapshot.read + 工单 Project Scope。 */
@Service
final class DefaultPricingShadowSnapshotQueryService implements PricingShadowSnapshotQueryService {
    private static final String READ = "pricing.snapshot.read";

    private final PricingShadowSnapshotQueryRepository snapshots;
    private final WorkOrderScopeQuery workOrders;
    private final AuthorizationService authorization;

    DefaultPricingShadowSnapshotQueryService(
            PricingShadowSnapshotQueryRepository snapshots,
            WorkOrderScopeQuery workOrders,
            AuthorizationService authorization
    ) {
        this.snapshots = snapshots;
        this.workOrders = workOrders;
        this.authorization = authorization;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PricingShadowSnapshotView> listByWorkOrder(
            CurrentPrincipal principal, String correlationId, UUID workOrderId
    ) {
        Objects.requireNonNull(workOrderId, "workOrderId must not be null");
        WorkOrderScope scope = workOrders.find(principal.tenantId(), workOrderId)
                .orElseThrow(() -> new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND, "工单不存在"));
        authorization.require(principal, AuthorizationRequest.projectCapability(
                READ, principal.tenantId(), "WorkOrder", workOrderId.toString(),
                scope.projectId().toString()), correlationId);
        return snapshots.listByWorkOrder(principal.tenantId(), workOrderId);
    }
}
