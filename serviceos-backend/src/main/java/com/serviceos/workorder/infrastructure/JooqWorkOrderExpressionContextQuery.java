package com.serviceos.workorder.infrastructure;

import com.serviceos.jooq.generated.tables.WoWorkOrder;
import com.serviceos.workorder.api.WorkOrderExpressionContext;
import com.serviceos.workorder.api.WorkOrderExpressionContextQuery;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

import static com.serviceos.jooq.generated.tables.WoWorkOrder.WO_WORK_ORDER;

/**
 * 从 WorkOrder 权威表读取表达式所需的最小事实集合。
 *
 * <p>查询必须同时携带 tenantId 与 workOrderId，避免仅凭全局 UUID 读取其他租户事实。
 * 持久化细节留在 infrastructure，调用方只能依赖 workorder::api 暴露的只读端口。</p>
 */
@Repository
final class JooqWorkOrderExpressionContextQuery implements WorkOrderExpressionContextQuery {
    private final DSLContext dsl;

    JooqWorkOrderExpressionContextQuery(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public Optional<WorkOrderExpressionContext> find(String tenantId, UUID workOrderId) {
        WoWorkOrder wo = WO_WORK_ORDER;
        return dsl.select(wo.ID, wo.CLIENT_CODE, wo.BRAND_CODE, wo.SERVICE_PRODUCT_CODE,
                        wo.PROVINCE_CODE, wo.CITY_CODE, wo.DISTRICT_CODE)
                .from(wo)
                .where(wo.TENANT_ID.eq(tenantId))
                .and(wo.ID.eq(workOrderId))
                .fetchOptional()
                .map(row -> new WorkOrderExpressionContext(
                        row.get(wo.ID),
                        row.get(wo.CLIENT_CODE),
                        row.get(wo.BRAND_CODE),
                        row.get(wo.SERVICE_PRODUCT_CODE),
                        row.get(wo.PROVINCE_CODE),
                        row.get(wo.CITY_CODE),
                        row.get(wo.DISTRICT_CODE)));
    }
}
