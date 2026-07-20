package com.serviceos.workorder.infrastructure;

import com.serviceos.jooq.generated.tables.WoWorkOrder;
import com.serviceos.workorder.api.WorkOrderView;
import com.serviceos.workorder.application.WorkOrderQueryRepository;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.SelectField;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.serviceos.jooq.generated.tables.WoWorkOrder.WO_WORK_ORDER;

/**
 * 工单授权查询的 jOOQ 实现。列表范围（tenant/project scope）与稳定游标必须在 SQL 中收敛，
 * 与原 MyBatis 动态查询逐条等价：空项目集合退化为 AND FALSE，游标为 (received_at,id) 行值比较。
 */
@Repository
final class JooqWorkOrderQueryRepository implements WorkOrderQueryRepository {
    private static final WoWorkOrder WO = WO_WORK_ORDER;
    private static final List<SelectField<?>> VIEW_FIELDS = List.of(
            WO.ID, WO.TENANT_ID, WO.PROJECT_ID, WO.CLIENT_CODE, WO.BRAND_CODE,
            WO.SERVICE_PRODUCT_CODE, WO.EXTERNAL_ORDER_CODE, WO.STATUS,
            WO.CONFIGURATION_BUNDLE_ID, WO.CONFIGURATION_BUNDLE_CODE,
            WO.CONFIGURATION_BUNDLE_VERSION, WO.CONFIGURATION_BUNDLE_DIGEST,
            WO.PROVINCE_CODE, WO.CITY_CODE, WO.DISTRICT_CODE,
            WO.EXTERNAL_DISPATCHED_AT, WO.RECEIVED_AT, WO.ACTIVATED_AT, WO.FULFILLED_AT, WO.VERSION);

    private final DSLContext dsl;

    JooqWorkOrderQueryRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public List<WorkOrderView> findPage(String tenantId, boolean tenantWide,
            List<UUID> projectIds, String clientCode, UUID projectId, String status,
            String externalOrderCode, Instant cursorReceivedAt, UUID cursorId, int fetchSize) {
        Condition condition = WO.TENANT_ID.eq(tenantId);
        if (!tenantWide) {
            // 非全租户视角且无任何授权项目时结果必须为空（AND FALSE），不得退化为全量。
            condition = condition.and(projectIds.isEmpty()
                    ? DSL.falseCondition()
                    : WO.PROJECT_ID.in(projectIds));
        }
        if (clientCode != null) {
            condition = condition.and(WO.CLIENT_CODE.eq(clientCode));
        }
        if (projectId != null) {
            condition = condition.and(WO.PROJECT_ID.eq(projectId));
        }
        if (status != null) {
            condition = condition.and(WO.STATUS.eq(status));
        }
        if (externalOrderCode != null) {
            condition = condition.and(WO.EXTERNAL_ORDER_CODE.eq(externalOrderCode));
        }
        if (cursorReceivedAt != null) {
            condition = condition.and(DSL.row(WO.RECEIVED_AT, WO.ID).lt(cursorReceivedAt, cursorId));
        }
        return dsl.select(VIEW_FIELDS)
                .from(WO)
                .where(condition)
                .orderBy(WO.RECEIVED_AT.desc(), WO.ID.desc())
                .limit(fetchSize)
                .fetch(JooqWorkOrderQueryRepository::view);
    }

    @Override
    public Optional<WorkOrderView> findById(String tenantId, UUID workOrderId) {
        return dsl.select(VIEW_FIELDS)
                .from(WO)
                .where(WO.TENANT_ID.eq(tenantId))
                .and(WO.ID.eq(workOrderId))
                .fetchOptional()
                .map(JooqWorkOrderQueryRepository::view);
    }

    @Override
    public Optional<RawCustomerContact> findRawCustomerContact(String tenantId, UUID workOrderId) {
        return dsl.select(WO.ID, WO.CUSTOMER_NAME, WO.CUSTOMER_MOBILE, WO.SERVICE_ADDRESS)
                .from(WO)
                .where(WO.TENANT_ID.eq(tenantId))
                .and(WO.ID.eq(workOrderId))
                .fetchOptional()
                .map(row -> new RawCustomerContact(
                        row.get(WO.ID),
                        row.get(WO.CUSTOMER_NAME),
                        row.get(WO.CUSTOMER_MOBILE),
                        row.get(WO.SERVICE_ADDRESS)));
    }

    private static WorkOrderView view(Record row) {
        return new WorkOrderView(
                row.get(WO.ID),
                row.get(WO.TENANT_ID),
                row.get(WO.PROJECT_ID),
                row.get(WO.CLIENT_CODE),
                row.get(WO.BRAND_CODE),
                row.get(WO.SERVICE_PRODUCT_CODE),
                row.get(WO.EXTERNAL_ORDER_CODE),
                row.get(WO.STATUS),
                row.get(WO.CONFIGURATION_BUNDLE_ID),
                row.get(WO.CONFIGURATION_BUNDLE_CODE),
                row.get(WO.CONFIGURATION_BUNDLE_VERSION),
                row.get(WO.CONFIGURATION_BUNDLE_DIGEST),
                row.get(WO.PROVINCE_CODE),
                row.get(WO.CITY_CODE),
                row.get(WO.DISTRICT_CODE),
                // external_dispatched_at 为不带时区 timestamp（车企本地时间约定按 UTC 解释），
                // 与原 MyBatis Map 适配器 LocalDateTime -> Instant(UTC) 的语义一致。
                row.get(WO.EXTERNAL_DISPATCHED_AT).toInstant(ZoneOffset.UTC),
                row.get(WO.RECEIVED_AT),
                row.get(WO.ACTIVATED_AT),
                row.get(WO.FULFILLED_AT),
                row.get(WO.VERSION));
    }
}
