package com.serviceos.workorder.infrastructure;

import com.serviceos.jooq.generated.tables.WoWorkOrder;
import com.serviceos.workorder.api.WorkOrderConfigurationBinding;
import com.serviceos.workorder.api.WorkOrderConfigurationBindingQuery;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

import static com.serviceos.jooq.generated.tables.WoWorkOrder.WO_WORK_ORDER;

/** 从 WorkOrder 权威表读取冻结 Bundle 绑定。 */
@Repository
final class JooqWorkOrderConfigurationBindingQuery implements WorkOrderConfigurationBindingQuery {
    private final DSLContext dsl;

    JooqWorkOrderConfigurationBindingQuery(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public Optional<WorkOrderConfigurationBinding> find(String tenantId, UUID workOrderId) {
        WoWorkOrder wo = WO_WORK_ORDER;
        return dsl.select(wo.ID, wo.PROJECT_ID, wo.CONFIGURATION_BUNDLE_ID, wo.CONFIGURATION_BUNDLE_DIGEST)
                .from(wo)
                .where(wo.TENANT_ID.eq(tenantId))
                .and(wo.ID.eq(workOrderId))
                .fetchOptional()
                .map(row -> new WorkOrderConfigurationBinding(
                        row.get(wo.ID),
                        row.get(wo.PROJECT_ID),
                        row.get(wo.CONFIGURATION_BUNDLE_ID),
                        row.get(wo.CONFIGURATION_BUNDLE_DIGEST)));
    }
}
