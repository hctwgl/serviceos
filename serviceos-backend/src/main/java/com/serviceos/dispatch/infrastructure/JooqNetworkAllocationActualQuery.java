package com.serviceos.dispatch.infrastructure;

import com.serviceos.dispatch.api.NetworkAllocationActualQuery;
import com.serviceos.jooq.generated.tables.DspServiceAssignment;
import com.serviceos.jooq.generated.tables.WoWorkOrder;
import org.jooq.DSLContext;
import org.jooq.Record2;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static com.serviceos.jooq.generated.tables.DspServiceAssignment.DSP_SERVICE_ASSIGNMENT;
import static com.serviceos.jooq.generated.tables.WoWorkOrder.WO_WORK_ORDER;

/**
 * 月度 NETWORK 派单实际量（ORDER_COUNT）。
 *
 * <p>口径：当月 UTC 自然月内创建的 NETWORK ServiceAssignment（ACTIVE/ENDED），
 * 并与工单 brand/project 对齐。AMOUNT/WEIGHTED_VOLUME 不在此实现。</p>
 */
@Component
final class JooqNetworkAllocationActualQuery implements NetworkAllocationActualQuery {
    private final DSLContext dsl;

    JooqNetworkAllocationActualQuery(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public Map<String, Long> countMonthlyNetworkAssignments(
            String tenantId,
            UUID projectId,
            String brandCode,
            String businessType,
            Instant asOf
    ) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(brandCode, "brandCode");
        Objects.requireNonNull(businessType, "businessType");
        Instant evaluatedAt = Objects.requireNonNull(asOf, "asOf");
        OffsetDateTime monthStart = evaluatedAt.atOffset(ZoneOffset.UTC)
                .truncatedTo(ChronoUnit.DAYS)
                .withDayOfMonth(1);
        Instant periodStart = monthStart.toInstant();
        Instant periodEnd = monthStart.plusMonths(1).toInstant();
        DspServiceAssignment a = DSP_SERVICE_ASSIGNMENT.as("a");
        WoWorkOrder w = WO_WORK_ORDER.as("w");
        Map<String, Long> result = new LinkedHashMap<>();
        dsl.select(a.ASSIGNEE_ID, DSL.count())
                .from(a)
                .join(w)
                .on(w.TENANT_ID.eq(a.TENANT_ID))
                .and(w.ID.eq(a.WORK_ORDER_ID))
                .where(a.TENANT_ID.eq(tenantId.trim()))
                .and(w.PROJECT_ID.eq(projectId))
                .and(w.BRAND_CODE.eq(brandCode.trim()))
                .and(a.BUSINESS_TYPE.eq(businessType.trim()))
                .and(a.RESPONSIBILITY_LEVEL.eq("NETWORK"))
                .and(a.STATUS.in("ACTIVE", "ENDED"))
                .and(a.CREATED_AT.ge(periodStart))
                .and(a.CREATED_AT.lt(periodEnd))
                .groupBy(a.ASSIGNEE_ID)
                .orderBy(a.ASSIGNEE_ID)
                .fetch((Record2<String, Integer> row) ->
                        result.put(row.value1(), row.value2().longValue()));
        return Map.copyOf(result);
    }
}
