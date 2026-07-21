package com.serviceos.project.infrastructure;

import com.serviceos.jooq.generated.tables.PrjProject;
import com.serviceos.project.application.ProjectQueryRepository;
import com.serviceos.project.application.ProjectScopeRevision;
import com.serviceos.project.domain.Project;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static com.serviceos.jooq.generated.tables.PrjProject.PRJ_PROJECT;
import static com.serviceos.jooq.generated.tables.PrjProjectNetwork.PRJ_PROJECT_NETWORK;
import static com.serviceos.jooq.generated.tables.PrjProjectRegion.PRJ_PROJECT_REGION;
import static com.serviceos.jooq.generated.tables.PrjProjectScopeRevision.PRJ_PROJECT_SCOPE_REVISION;

/**
 * 项目目录分页查询。实时授权范围在 SQL 条件中收敛（tenantWide=false 且项目集合为空时
 * 恒假条件直接无结果），有效绑定用相关子查询聚合，避免先读全量再内存过滤。
 */
@Repository
final class JooqProjectQueryRepository implements ProjectQueryRepository {
    private final DSLContext dsl;

    JooqProjectQueryRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public List<Project> findPage(
            String tenantId, boolean tenantWide, List<UUID> projectIds, String clientId, String status,
            LocalDate activeOn, String cursorCode, UUID cursorId, int fetchSize
    ) {
        PrjProject p = PRJ_PROJECT;
        var r = PRJ_PROJECT_REGION;
        var n = PRJ_PROJECT_NETWORK;
        // 原 XML 用 jsonb_agg 聚合有效绑定；array_agg 同序等价，Java 侧直接得到列表，空集为 NULL。
        Field<String[]> regionCodes = DSL.field(dsl
                        .select(DSL.arrayAgg(r.REGION_CODE).orderBy(r.REGION_CODE))
                        .from(r)
                        .where(r.TENANT_ID.eq(p.TENANT_ID))
                        .and(r.PROJECT_ID.eq(p.PROJECT_ID))
                        .and(r.VALID_TO.isNull()))
                .as("region_codes");
        Field<String[]> networkIds = DSL.field(dsl
                        .select(DSL.arrayAgg(n.NETWORK_ID).orderBy(n.NETWORK_ID))
                        .from(n)
                        .where(n.TENANT_ID.eq(p.TENANT_ID))
                        .and(n.PROJECT_ID.eq(p.PROJECT_ID))
                        .and(n.VALID_TO.isNull()))
                .as("network_ids");
        Condition condition = p.TENANT_ID.eq(tenantId);
        if (!tenantWide) {
            // 非全租户视角且项目集合为空时与原 XML 的 AND FALSE 一致，直接无结果。
            condition = condition.and(projectIds.isEmpty()
                    ? DSL.falseCondition()
                    : p.PROJECT_ID.in(projectIds));
        }
        if (clientId != null) {
            condition = condition.and(p.CLIENT_ID.eq(clientId));
        }
        if (status != null) {
            condition = condition.and(p.PROJECT_STATUS.eq(status));
        }
        if (activeOn != null) {
            condition = condition.and(p.STARTS_ON.le(activeOn))
                    .and(p.ENDS_ON.isNull().or(p.ENDS_ON.ge(activeOn)));
        }
        if (cursorCode != null) {
            condition = condition.and(DSL.row(p.PROJECT_CODE, p.PROJECT_ID).gt(cursorCode, cursorId));
        }
        return dsl.select(
                        p.PROJECT_ID, p.TENANT_ID, p.PROJECT_CODE, p.CLIENT_ID, p.PROJECT_NAME,
                        p.STARTS_ON, p.ENDS_ON, p.PROJECT_STATUS, p.AGGREGATE_VERSION, p.CREATED_AT,
                        regionCodes, networkIds)
                .from(p)
                .where(condition)
                .orderBy(p.PROJECT_CODE, p.PROJECT_ID)
                .limit(fetchSize)
                .fetch(record -> project(record, regionCodes, networkIds));
    }

    @Override
    public List<ProjectScopeRevision> findScopeRevisionPage(
            String tenantId, UUID projectId, Long cursorVersion, int fetchSize
    ) {
        var rev = PRJ_PROJECT_SCOPE_REVISION;
        Condition condition = rev.TENANT_ID.eq(tenantId).and(rev.PROJECT_ID.eq(projectId));
        if (cursorVersion != null) {
            condition = condition.and(rev.AGGREGATE_VERSION.lt(cursorVersion));
        }
        return dsl.select(
                        rev.REVISION_ID, rev.TENANT_ID, rev.PROJECT_ID, rev.EXPECTED_VERSION,
                        rev.AGGREGATE_VERSION, rev.REGION_CODES, rev.NETWORK_IDS,
                        rev.ADDED_REGION_CODES, rev.REMOVED_REGION_CODES,
                        rev.ADDED_NETWORK_IDS, rev.REMOVED_NETWORK_IDS,
                        rev.REASON, rev.REVISED_BY, rev.REVISED_AT)
                .from(rev)
                .where(condition)
                .orderBy(rev.AGGREGATE_VERSION.desc())
                .limit(fetchSize)
                .fetch(JooqProjectRepository::revision);
    }

    private static Project project(Record record, Field<String[]> regionCodes, Field<String[]> networkIds) {
        PrjProject p = PRJ_PROJECT;
        String[] regions = record.get(regionCodes);
        String[] networks = record.get(networkIds);
        return new Project(
                record.get(p.PROJECT_ID), record.get(p.TENANT_ID), record.get(p.PROJECT_CODE),
                record.get(p.CLIENT_ID), record.get(p.PROJECT_NAME),
                record.get(p.STARTS_ON), record.get(p.ENDS_ON),
                regions == null ? List.of() : List.of(regions),
                networks == null ? List.of() : List.of(networks),
                Project.Status.valueOf(record.get(p.PROJECT_STATUS)),
                record.get(p.AGGREGATE_VERSION), record.get(p.CREATED_AT));
    }
}
