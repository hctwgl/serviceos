package com.serviceos.dispatch.infrastructure;

import com.serviceos.dispatch.api.NetworkAllocationTargetQuery;
import com.serviceos.jooq.generated.tables.DspNetworkAllocationTarget;
import org.jooq.DSLContext;
import org.jooq.Record2;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static com.serviceos.jooq.generated.tables.DspNetworkAllocationTarget.DSP_NETWORK_ALLOCATION_TARGET;

/** 网点签约比例目标查询（PostgreSQL）。 */
@Component
final class JooqNetworkAllocationTargetQuery implements NetworkAllocationTargetQuery {
    private final DSLContext dsl;

    JooqNetworkAllocationTargetQuery(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public Map<String, Double> listCommittedShares(
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
        DspNetworkAllocationTarget target = DSP_NETWORK_ALLOCATION_TARGET;
        // DISTINCT ON (network_id) ... ORDER BY network_id, valid_from DESC：
        // 每个网点取 asOf 时刻最新的生效目标行。
        Map<String, Double> result = new LinkedHashMap<>();
        dsl.select(target.NETWORK_ID, target.COMMITTED_SHARE)
                .distinctOn(target.NETWORK_ID)
                .from(target)
                .where(target.TENANT_ID.eq(tenantId.trim()))
                .and(target.PROJECT_ID.eq(projectId))
                .and(target.BRAND_CODE.eq(brandCode.trim()))
                .and(target.BUSINESS_TYPE.eq(businessType.trim()))
                .and(target.VALID_FROM.le(evaluatedAt))
                .and(target.VALID_TO.isNull().or(target.VALID_TO.gt(evaluatedAt)))
                .orderBy(target.NETWORK_ID, target.VALID_FROM.desc())
                .fetch((Record2<String, BigDecimal> row) ->
                        result.put(row.value1(), row.value2().doubleValue()));
        return Map.copyOf(result);
    }
}
