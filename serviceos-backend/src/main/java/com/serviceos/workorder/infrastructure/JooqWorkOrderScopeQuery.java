package com.serviceos.workorder.infrastructure;

import com.serviceos.jooq.generated.tables.WoWorkOrder;
import com.serviceos.workorder.api.WorkOrderScope;
import com.serviceos.workorder.api.WorkOrderScopeQuery;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

import static com.serviceos.jooq.generated.tables.WoWorkOrder.WO_WORK_ORDER;

/**
 * 工单范围适配器。tenantId 必须参与查询，调用方不能仅凭全局 UUID 推断其他租户的工单范围。
 */
@Repository
final class JooqWorkOrderScopeQuery implements WorkOrderScopeQuery {
    private final DSLContext dsl;

    JooqWorkOrderScopeQuery(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public Optional<WorkOrderScope> find(String tenantId, UUID workOrderId) {
        WoWorkOrder wo = WO_WORK_ORDER;
        return dsl.select(wo.ID, wo.PROJECT_ID)
                .from(wo)
                .where(wo.TENANT_ID.eq(tenantId))
                .and(wo.ID.eq(workOrderId))
                .fetchOptional()
                .map(row -> new WorkOrderScope(
                        row.get(wo.ID),
                        row.get(wo.PROJECT_ID)));
    }
}
