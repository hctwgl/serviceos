package com.serviceos.workorder.infrastructure;

import com.serviceos.jooq.generated.tables.WoWorkOrder;
import com.serviceos.workorder.api.WorkOrderDirectoryHeader;
import com.serviceos.workorder.api.WorkOrderDirectoryHeaderQuery;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

import static com.serviceos.jooq.generated.tables.WoWorkOrder.WO_WORK_ORDER;

/**
 * 从 {@code wo_work_order} 读取目录用非 PII 头字段。
 * 查询必须同时携带 tenantId 与 workOrderId。
 */
@Repository
final class JooqWorkOrderDirectoryHeaderQuery implements WorkOrderDirectoryHeaderQuery {
    private final DSLContext dsl;

    JooqWorkOrderDirectoryHeaderQuery(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public Optional<WorkOrderDirectoryHeader> find(String tenantId, UUID workOrderId) {
        WoWorkOrder wo = WO_WORK_ORDER;
        return dsl.select(wo.ID, wo.BRAND_CODE, wo.SERVICE_PRODUCT_CODE,
                        wo.PROVINCE_CODE, wo.CITY_CODE, wo.DISTRICT_CODE, wo.RECEIVED_AT)
                .from(wo)
                .where(wo.TENANT_ID.eq(tenantId))
                .and(wo.ID.eq(workOrderId))
                .fetchOptional()
                .map(row -> new WorkOrderDirectoryHeader(
                        row.get(wo.ID),
                        row.get(wo.BRAND_CODE),
                        row.get(wo.SERVICE_PRODUCT_CODE),
                        row.get(wo.PROVINCE_CODE),
                        row.get(wo.CITY_CODE),
                        row.get(wo.DISTRICT_CODE),
                        row.get(wo.RECEIVED_AT)));
    }
}
