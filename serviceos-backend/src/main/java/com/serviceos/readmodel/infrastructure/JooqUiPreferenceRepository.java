package com.serviceos.readmodel.infrastructure;

import com.serviceos.jooq.generated.tables.RdmUiPreference;
import com.serviceos.readmodel.application.UiPreferenceRepository;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.SelectField;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import static com.serviceos.jooq.generated.tables.RdmUiPreference.RDM_UI_PREFERENCE;

@Repository
final class JooqUiPreferenceRepository implements UiPreferenceRepository {
    private static final RdmUiPreference P = RDM_UI_PREFERENCE;
    private static final List<SelectField<?>> RECORD_FIELDS = List.of(
            P.TENANT_ID, P.PRINCIPAL_ID, P.PORTAL, P.PREFERENCE_KEY,
            P.VALUE_JSON, P.SCHEMA_VERSION, P.AGGREGATE_VERSION,
            P.CREATED_AT, P.UPDATED_AT);

    private final DSLContext dsl;

    JooqUiPreferenceRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public List<UiPreferenceRecord> listByOwner(String tenantId, String principalId, String portal) {
        return dsl.select(RECORD_FIELDS)
                .from(P)
                .where(P.TENANT_ID.eq(tenantId))
                .and(P.PRINCIPAL_ID.eq(principalId))
                .and(P.PORTAL.eq(portal))
                .orderBy(P.PREFERENCE_KEY.asc())
                .fetch(JooqUiPreferenceRepository::mapRecord);
    }

    @Override
    public Optional<UiPreferenceRecord> findOwned(
            String tenantId, String principalId, String portal, String preferenceKey
    ) {
        return dsl.select(RECORD_FIELDS)
                .from(P)
                .where(P.TENANT_ID.eq(tenantId))
                .and(P.PRINCIPAL_ID.eq(principalId))
                .and(P.PORTAL.eq(portal))
                .and(P.PREFERENCE_KEY.eq(preferenceKey))
                .fetchOptional(JooqUiPreferenceRepository::mapRecord);
    }

    @Override
    public void insert(UiPreferenceRecord record) {
        // value_json 由全局 JsonbStringConverter 绑定（String -> JSONB），无需手写 CAST。
        dsl.insertInto(P)
                .set(P.TENANT_ID, record.tenantId())
                .set(P.PRINCIPAL_ID, record.principalId())
                .set(P.PORTAL, record.portal())
                .set(P.PREFERENCE_KEY, record.preferenceKey())
                .set(P.VALUE_JSON, record.valueJson())
                .set(P.SCHEMA_VERSION, record.schemaVersion())
                .set(P.AGGREGATE_VERSION, record.aggregateVersion())
                .set(P.CREATED_AT, record.createdAt())
                .set(P.UPDATED_AT, record.updatedAt())
                .execute();
    }

    @Override
    public boolean update(UiPreferenceRecord record, long expectedVersion) {
        // 乐观并发：aggregate_version 不匹配即更新失败，由调用方判定冲突。
        int updated = dsl.update(P)
                .set(P.VALUE_JSON, record.valueJson())
                .set(P.SCHEMA_VERSION, record.schemaVersion())
                .set(P.AGGREGATE_VERSION, record.aggregateVersion())
                .set(P.UPDATED_AT, record.updatedAt())
                .where(P.TENANT_ID.eq(record.tenantId()))
                .and(P.PRINCIPAL_ID.eq(record.principalId()))
                .and(P.PORTAL.eq(record.portal()))
                .and(P.PREFERENCE_KEY.eq(record.preferenceKey()))
                .and(P.AGGREGATE_VERSION.eq(expectedVersion))
                .execute();
        return updated == 1;
    }

    @Override
    public boolean deleteOwned(String tenantId, String principalId, String portal, String preferenceKey) {
        int deleted = dsl.deleteFrom(P)
                .where(P.TENANT_ID.eq(tenantId))
                .and(P.PRINCIPAL_ID.eq(principalId))
                .and(P.PORTAL.eq(portal))
                .and(P.PREFERENCE_KEY.eq(preferenceKey))
                .execute();
        return deleted == 1;
    }

    private static UiPreferenceRecord mapRecord(Record row) {
        return new UiPreferenceRecord(
                row.get(P.TENANT_ID),
                row.get(P.PRINCIPAL_ID),
                row.get(P.PORTAL),
                row.get(P.PREFERENCE_KEY),
                row.get(P.VALUE_JSON),
                row.get(P.SCHEMA_VERSION),
                row.get(P.AGGREGATE_VERSION),
                row.get(P.CREATED_AT),
                row.get(P.UPDATED_AT));
    }
}
