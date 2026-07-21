package com.serviceos.readmodel.infrastructure;

import com.serviceos.jooq.generated.tables.RdmSavedView;
import com.serviceos.readmodel.api.SavedView;
import com.serviceos.readmodel.api.SavedViewVisibility;
import com.serviceos.readmodel.application.SavedViewJson;
import com.serviceos.readmodel.application.SavedViewRepository;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.SelectField;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.serviceos.jooq.generated.tables.RdmSavedView.RDM_SAVED_VIEW;

@Repository
final class JooqSavedViewRepository implements SavedViewRepository {
    private static final RdmSavedView S = RDM_SAVED_VIEW;
    private static final List<SelectField<?>> RECORD_FIELDS = List.of(
            S.SAVED_VIEW_ID, S.TENANT_ID, S.PRINCIPAL_ID, S.PORTAL, S.PAGE_ID, S.NAME,
            S.VISIBILITY, S.SHARED_SCOPE_REF, S.SCHEMA_VERSION,
            S.FILTER_JSON, S.SORT_JSON, S.COLUMN_JSON, S.IS_DEFAULT, S.AGGREGATE_VERSION,
            S.CREATED_AT, S.UPDATED_AT);

    private final DSLContext dsl;

    JooqSavedViewRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public List<SavedView> listVisible(
            String tenantId,
            String principalId,
            String portal,
            String pageId,
            Collection<String> activeRoleIds
    ) {
        // 本人视图始终可见；TENANT 共享对同租户开放；ROLE 共享仅对持有该 roleId 的主体开放。
        List<String> roleIds = activeRoleIds == null ? List.of() : List.copyOf(activeRoleIds);
        Condition visible = S.PRINCIPAL_ID.eq(principalId).or(S.VISIBILITY.eq("TENANT"));
        if (!roleIds.isEmpty()) {
            visible = visible.or(S.VISIBILITY.eq("ROLE").and(S.SHARED_SCOPE_REF.in(roleIds)));
        }
        return dsl.select(RECORD_FIELDS)
                .from(S)
                .where(S.TENANT_ID.eq(tenantId))
                .and(S.PORTAL.eq(portal))
                .and(S.PAGE_ID.eq(pageId))
                .and(visible)
                .orderBy(
                        DSL.when(S.PRINCIPAL_ID.eq(principalId), 0).otherwise(1),
                        S.IS_DEFAULT.desc(), S.UPDATED_AT.desc(), S.NAME.asc())
                .fetch(row -> SavedViewJson.toView(mapRecord(row)));
    }

    @Override
    public Optional<SavedViewRecord> findOwned(String tenantId, String principalId, UUID savedViewId) {
        return dsl.select(RECORD_FIELDS)
                .from(S)
                .where(S.SAVED_VIEW_ID.eq(savedViewId))
                .and(S.TENANT_ID.eq(tenantId))
                .and(S.PRINCIPAL_ID.eq(principalId))
                .fetchOptional(JooqSavedViewRepository::mapRecord);
    }

    @Override
    public void insert(SavedViewRecord record) {
        // jsonb 列由全局 JsonbStringConverter 绑定（String -> JSONB），无需手写 CAST。
        dsl.insertInto(S)
                .set(S.SAVED_VIEW_ID, record.id())
                .set(S.TENANT_ID, record.tenantId())
                .set(S.PRINCIPAL_ID, record.principalId())
                .set(S.PORTAL, record.portal())
                .set(S.PAGE_ID, record.pageId())
                .set(S.NAME, record.name())
                .set(S.VISIBILITY, record.visibility().name())
                .set(S.SHARED_SCOPE_REF, record.sharedScopeRef())
                .set(S.SCHEMA_VERSION, record.schemaVersion())
                .set(S.FILTER_JSON, record.filterJson())
                .set(S.SORT_JSON, record.sortJson())
                .set(S.COLUMN_JSON, record.columnJson())
                .set(S.IS_DEFAULT, record.isDefault())
                .set(S.AGGREGATE_VERSION, record.aggregateVersion())
                .set(S.CREATED_AT, record.createdAt())
                .set(S.UPDATED_AT, record.updatedAt())
                .execute();
    }

    @Override
    public boolean update(SavedViewRecord record, long expectedVersion) {
        // 乐观并发：只有 aggregate_version 匹配才允许覆盖，影响行数即是否成功。
        int updated = dsl.update(S)
                .set(S.NAME, record.name())
                .set(S.SCHEMA_VERSION, record.schemaVersion())
                .set(S.FILTER_JSON, record.filterJson())
                .set(S.SORT_JSON, record.sortJson())
                .set(S.COLUMN_JSON, record.columnJson())
                .set(S.IS_DEFAULT, record.isDefault())
                .set(S.AGGREGATE_VERSION, record.aggregateVersion())
                .set(S.UPDATED_AT, record.updatedAt())
                .where(S.SAVED_VIEW_ID.eq(record.id()))
                .and(S.TENANT_ID.eq(record.tenantId()))
                .and(S.PRINCIPAL_ID.eq(record.principalId()))
                .and(S.AGGREGATE_VERSION.eq(expectedVersion))
                .execute();
        return updated == 1;
    }

    @Override
    public boolean updateVisibility(
            String tenantId,
            String principalId,
            UUID savedViewId,
            long expectedVersion,
            SavedViewVisibility visibility,
            String sharedScopeRef,
            long nextVersion,
            Instant updatedAt
    ) {
        int updated = dsl.update(S)
                .set(S.VISIBILITY, visibility.name())
                .set(S.SHARED_SCOPE_REF, sharedScopeRef)
                .set(S.AGGREGATE_VERSION, nextVersion)
                .set(S.UPDATED_AT, updatedAt)
                .where(S.SAVED_VIEW_ID.eq(savedViewId))
                .and(S.TENANT_ID.eq(tenantId))
                .and(S.PRINCIPAL_ID.eq(principalId))
                .and(S.AGGREGATE_VERSION.eq(expectedVersion))
                .execute();
        return updated == 1;
    }

    @Override
    public boolean deleteOwned(String tenantId, String principalId, UUID savedViewId) {
        int deleted = dsl.deleteFrom(S)
                .where(S.SAVED_VIEW_ID.eq(savedViewId))
                .and(S.TENANT_ID.eq(tenantId))
                .and(S.PRINCIPAL_ID.eq(principalId))
                .execute();
        return deleted == 1;
    }

    @Override
    public void clearDefault(String tenantId, String principalId, String portal, String pageId) {
        // updated_at 沿用数据库时钟 now()，与原 SQL 语义一致。
        dsl.update(S)
                .set(S.IS_DEFAULT, false)
                .set(S.UPDATED_AT, DSL.field("now()", Instant.class))
                .where(S.TENANT_ID.eq(tenantId))
                .and(S.PRINCIPAL_ID.eq(principalId))
                .and(S.PORTAL.eq(portal))
                .and(S.PAGE_ID.eq(pageId))
                .and(S.IS_DEFAULT.isTrue())
                .execute();
    }

    private static SavedViewRecord mapRecord(Record row) {
        String visibility = row.get(S.VISIBILITY);
        return new SavedViewRecord(
                row.get(S.SAVED_VIEW_ID),
                row.get(S.TENANT_ID),
                row.get(S.PRINCIPAL_ID),
                row.get(S.PORTAL),
                row.get(S.PAGE_ID),
                row.get(S.NAME),
                visibility == null ? SavedViewVisibility.PRIVATE : SavedViewVisibility.valueOf(visibility),
                row.get(S.SHARED_SCOPE_REF),
                row.get(S.SCHEMA_VERSION),
                row.get(S.FILTER_JSON),
                row.get(S.SORT_JSON),
                row.get(S.COLUMN_JSON),
                row.get(S.IS_DEFAULT),
                row.get(S.AGGREGATE_VERSION),
                row.get(S.CREATED_AT),
                row.get(S.UPDATED_AT));
    }
}
