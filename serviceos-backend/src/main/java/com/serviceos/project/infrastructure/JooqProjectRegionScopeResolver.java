package com.serviceos.project.infrastructure;

import com.serviceos.authorization.api.ProjectRegionScopeResolver;
import org.jooq.DSLContext;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import static com.serviceos.jooq.generated.tables.PrjProjectRegion.PRJ_PROJECT_REGION;

/**
 * 项目目录提供的 REGION 权威映射。查询同时约束 tenant、关系生效区间和精确 region_code，
 * 不使用地址前缀推断，不把空结果扩大为租户全量。
 */
@Component
final class JooqProjectRegionScopeResolver implements ProjectRegionScopeResolver {
    private final DSLContext dsl;

    JooqProjectRegionScopeResolver(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public Set<UUID> resolve(String tenantId, Set<String> regionCodes, Instant effectiveAt) {
        if (regionCodes.isEmpty()) {
            return Set.of();
        }
        var r = PRJ_PROJECT_REGION;
        return new LinkedHashSet<>(dsl.selectDistinct(r.PROJECT_ID)
                .from(r)
                .where(r.TENANT_ID.eq(tenantId))
                .and(r.REGION_CODE.in(regionCodes))
                .and(r.VALID_FROM.le(effectiveAt))
                .and(r.VALID_TO.isNull().or(r.VALID_TO.gt(effectiveAt)))
                .orderBy(r.PROJECT_ID)
                .fetch(r.PROJECT_ID));
    }
}
