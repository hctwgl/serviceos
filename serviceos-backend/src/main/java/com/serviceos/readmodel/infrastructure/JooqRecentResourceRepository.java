package com.serviceos.readmodel.infrastructure;

import com.serviceos.jooq.generated.tables.RdmRecentResource;
import com.serviceos.readmodel.application.RecentResourceRepository;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.SelectField;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import static com.serviceos.jooq.generated.tables.RdmRecentResource.RDM_RECENT_RESOURCE;
import static org.jooq.impl.DSL.excluded;

@Repository
final class JooqRecentResourceRepository implements RecentResourceRepository {
    private static final RdmRecentResource R = RDM_RECENT_RESOURCE;
    private static final List<SelectField<?>> RECORD_FIELDS = List.of(
            R.TENANT_ID, R.PRINCIPAL_ID, R.PORTAL, R.RESOURCE_TYPE, R.RESOURCE_ID,
            R.PAGE_ID, R.DISPLAY_REF, R.LAST_VISITED_AT, R.CREATED_AT);

    private final DSLContext dsl;

    JooqRecentResourceRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public List<RecentResourceRecord> listByOwnerOrdered(
            String tenantId, String principalId, String portal, int fetchLimit
    ) {
        return dsl.select(RECORD_FIELDS)
                .from(R)
                .where(R.TENANT_ID.eq(tenantId))
                .and(R.PRINCIPAL_ID.eq(principalId))
                .and(R.PORTAL.eq(portal))
                .orderBy(R.LAST_VISITED_AT.desc(), R.RESOURCE_TYPE.asc(), R.RESOURCE_ID.asc())
                .limit(fetchLimit)
                .fetch(JooqRecentResourceRepository::mapRecord);
    }

    @Override
    public Optional<RecentResourceRecord> findOwned(
            String tenantId,
            String principalId,
            String portal,
            String resourceType,
            String resourceId
    ) {
        return dsl.select(RECORD_FIELDS)
                .from(R)
                .where(R.TENANT_ID.eq(tenantId))
                .and(R.PRINCIPAL_ID.eq(principalId))
                .and(R.PORTAL.eq(portal))
                .and(R.RESOURCE_TYPE.eq(resourceType))
                .and(R.RESOURCE_ID.eq(resourceId))
                .fetchOptional(JooqRecentResourceRepository::mapRecord);
    }

    @Override
    public void upsert(RecentResourceRecord record) {
        // 冲突即同一资源再次访问：只刷新落点与最近访问时间，created_at 保留首次记录。
        dsl.insertInto(R)
                .set(R.TENANT_ID, record.tenantId())
                .set(R.PRINCIPAL_ID, record.principalId())
                .set(R.PORTAL, record.portal())
                .set(R.RESOURCE_TYPE, record.resourceType())
                .set(R.RESOURCE_ID, record.resourceId())
                .set(R.PAGE_ID, record.pageId())
                .set(R.DISPLAY_REF, record.displayRef())
                .set(R.LAST_VISITED_AT, record.lastVisitedAt())
                .set(R.CREATED_AT, record.createdAt())
                .onConflict(R.TENANT_ID, R.PRINCIPAL_ID, R.PORTAL, R.RESOURCE_TYPE, R.RESOURCE_ID)
                .doUpdate()
                .set(R.PAGE_ID, excluded(R.PAGE_ID))
                .set(R.DISPLAY_REF, excluded(R.DISPLAY_REF))
                .set(R.LAST_VISITED_AT, excluded(R.LAST_VISITED_AT))
                .execute();
    }

    @Override
    public boolean deleteOwned(
            String tenantId,
            String principalId,
            String portal,
            String resourceType,
            String resourceId
    ) {
        int deleted = dsl.deleteFrom(R)
                .where(R.TENANT_ID.eq(tenantId))
                .and(R.PRINCIPAL_ID.eq(principalId))
                .and(R.PORTAL.eq(portal))
                .and(R.RESOURCE_TYPE.eq(resourceType))
                .and(R.RESOURCE_ID.eq(resourceId))
                .execute();
        return deleted == 1;
    }

    @Override
    public int trimExcess(String tenantId, String principalId, String portal, int keepLimit) {
        // 保留最近 keepLimit 条；其余按 last_visited_at 升序删除。
        // 删除目标与子查询同表，必须用不同别名（r 删除目标 / x 待删集合）。
        RdmRecentResource r = RDM_RECENT_RESOURCE.as("r");
        RdmRecentResource x = RDM_RECENT_RESOURCE.as("x");
        return dsl.deleteFrom(r)
                .where(r.TENANT_ID.eq(tenantId))
                .and(r.PRINCIPAL_ID.eq(principalId))
                .and(r.PORTAL.eq(portal))
                .and(DSL.row(r.RESOURCE_TYPE, r.RESOURCE_ID).in(
                        dsl.select(x.RESOURCE_TYPE, x.RESOURCE_ID)
                                .from(x)
                                .where(x.TENANT_ID.eq(tenantId))
                                .and(x.PRINCIPAL_ID.eq(principalId))
                                .and(x.PORTAL.eq(portal))
                                .orderBy(x.LAST_VISITED_AT.asc(), x.RESOURCE_TYPE.desc(), x.RESOURCE_ID.desc())
                                .offset(keepLimit)))
                .execute();
    }

    private static RecentResourceRecord mapRecord(Record row) {
        return new RecentResourceRecord(
                row.get(R.TENANT_ID),
                row.get(R.PRINCIPAL_ID),
                row.get(R.PORTAL),
                row.get(R.RESOURCE_TYPE),
                row.get(R.RESOURCE_ID),
                row.get(R.PAGE_ID),
                row.get(R.DISPLAY_REF),
                row.get(R.LAST_VISITED_AT),
                row.get(R.CREATED_AT));
    }
}
