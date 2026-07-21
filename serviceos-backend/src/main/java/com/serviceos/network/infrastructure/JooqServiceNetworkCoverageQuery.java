package com.serviceos.network.infrastructure;

import com.serviceos.jooq.generated.tables.NetServiceNetworkCoverage;
import com.serviceos.network.api.ServiceNetworkCoverageQuery;
import com.serviceos.network.api.ServiceNetworkCoverageView;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import static com.serviceos.jooq.generated.tables.NetServiceNetworkCoverage.NET_SERVICE_NETWORK_COVERAGE;

/**
 * 网点 ServiceCoverage 查询（PostgreSQL）。
 */
@Component
public class JooqServiceNetworkCoverageQuery implements ServiceNetworkCoverageQuery {
    private final DSLContext dsl;

    public JooqServiceNetworkCoverageQuery(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public List<ServiceNetworkCoverageView> listActiveCoverage(
            String tenantId,
            Collection<String> serviceNetworkIds,
            String brandCode,
            String businessType,
            Instant asOf
    ) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(serviceNetworkIds, "serviceNetworkIds");
        Objects.requireNonNull(brandCode, "brandCode");
        Objects.requireNonNull(businessType, "businessType");
        Instant evaluatedAt = Objects.requireNonNull(asOf, "asOf");
        if (serviceNetworkIds.isEmpty()) {
            return List.of();
        }
        // 与 ServiceNetworkDirectoryQuery 一致：按文本匹配项目侧 network_id。
        List<String> networkIds = serviceNetworkIds.stream()
                .map(String::trim)
                .filter(id -> !id.isEmpty())
                .distinct()
                .toList();
        if (networkIds.isEmpty()) {
            return List.of();
        }
        NetServiceNetworkCoverage c = NET_SERVICE_NETWORK_COVERAGE;
        Field<String> networkIdText = c.SERVICE_NETWORK_ID.cast(String.class);
        return dsl.select(c.COVERAGE_ID, c.SERVICE_NETWORK_ID, c.BRAND_CODE, c.BUSINESS_TYPE, c.REGION_CODE)
                .from(c)
                .where(c.TENANT_ID.eq(tenantId.trim()))
                .and(networkIdText.in(networkIds))
                .and(c.BRAND_CODE.eq(brandCode.trim()))
                .and(c.BUSINESS_TYPE.eq(businessType.trim()))
                .and(c.COVERAGE_STATUS.eq("ACTIVE"))
                .and(c.VALID_FROM.le(evaluatedAt))
                .and(c.VALID_TO.isNull().or(c.VALID_TO.gt(evaluatedAt)))
                .orderBy(c.SERVICE_NETWORK_ID, c.REGION_CODE)
                .fetch(record -> new ServiceNetworkCoverageView(
                        record.get(c.COVERAGE_ID),
                        record.get(c.SERVICE_NETWORK_ID),
                        record.get(c.BRAND_CODE),
                        record.get(c.BUSINESS_TYPE),
                        record.get(c.REGION_CODE)));
    }
}
