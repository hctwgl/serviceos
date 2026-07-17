package com.serviceos.readmodel.infrastructure;

import com.serviceos.readmodel.application.UiPreferenceRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

@Repository
class JdbcUiPreferenceRepository implements UiPreferenceRepository {
    private final JdbcClient jdbc;

    JdbcUiPreferenceRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<UiPreferenceRecord> listByOwner(String tenantId, String principalId, String portal) {
        return jdbc.sql("""
                        SELECT tenant_id, principal_id, portal, preference_key,
                               value_json::text AS value_json, schema_version, aggregate_version,
                               created_at, updated_at
                          FROM rdm_ui_preference
                         WHERE tenant_id = :tenantId
                           AND principal_id = :principalId
                           AND portal = :portal
                         ORDER BY preference_key ASC
                        """)
                .param("tenantId", tenantId)
                .param("principalId", principalId)
                .param("portal", portal)
                .query((rs, rowNum) -> mapRecord(rs))
                .list();
    }

    @Override
    public Optional<UiPreferenceRecord> findOwned(
            String tenantId, String principalId, String portal, String preferenceKey
    ) {
        return jdbc.sql("""
                        SELECT tenant_id, principal_id, portal, preference_key,
                               value_json::text AS value_json, schema_version, aggregate_version,
                               created_at, updated_at
                          FROM rdm_ui_preference
                         WHERE tenant_id = :tenantId
                           AND principal_id = :principalId
                           AND portal = :portal
                           AND preference_key = :preferenceKey
                        """)
                .param("tenantId", tenantId)
                .param("principalId", principalId)
                .param("portal", portal)
                .param("preferenceKey", preferenceKey)
                .query((rs, rowNum) -> mapRecord(rs))
                .optional();
    }

    @Override
    public void insert(UiPreferenceRecord record) {
        jdbc.sql("""
                INSERT INTO rdm_ui_preference (
                    tenant_id, principal_id, portal, preference_key, value_json,
                    schema_version, aggregate_version, created_at, updated_at
                ) VALUES (
                    :tenantId, :principalId, :portal, :preferenceKey, CAST(:valueJson AS jsonb),
                    :schemaVersion, :aggregateVersion, :createdAt, :updatedAt
                )
                """)
                .param("tenantId", record.tenantId())
                .param("principalId", record.principalId())
                .param("portal", record.portal())
                .param("preferenceKey", record.preferenceKey())
                .param("valueJson", record.valueJson())
                .param("schemaVersion", record.schemaVersion())
                .param("aggregateVersion", record.aggregateVersion())
                .param("createdAt", Timestamp.from(record.createdAt()))
                .param("updatedAt", Timestamp.from(record.updatedAt()))
                .update();
    }

    @Override
    public boolean update(UiPreferenceRecord record, long expectedVersion) {
        int updated = jdbc.sql("""
                UPDATE rdm_ui_preference
                   SET value_json = CAST(:valueJson AS jsonb),
                       schema_version = :schemaVersion,
                       aggregate_version = :aggregateVersion,
                       updated_at = :updatedAt
                 WHERE tenant_id = :tenantId
                   AND principal_id = :principalId
                   AND portal = :portal
                   AND preference_key = :preferenceKey
                   AND aggregate_version = :expectedVersion
                """)
                .param("valueJson", record.valueJson())
                .param("schemaVersion", record.schemaVersion())
                .param("aggregateVersion", record.aggregateVersion())
                .param("updatedAt", Timestamp.from(record.updatedAt()))
                .param("tenantId", record.tenantId())
                .param("principalId", record.principalId())
                .param("portal", record.portal())
                .param("preferenceKey", record.preferenceKey())
                .param("expectedVersion", expectedVersion)
                .update();
        return updated == 1;
    }

    @Override
    public boolean deleteOwned(String tenantId, String principalId, String portal, String preferenceKey) {
        int deleted = jdbc.sql("""
                DELETE FROM rdm_ui_preference
                 WHERE tenant_id = :tenantId
                   AND principal_id = :principalId
                   AND portal = :portal
                   AND preference_key = :preferenceKey
                """)
                .param("tenantId", tenantId)
                .param("principalId", principalId)
                .param("portal", portal)
                .param("preferenceKey", preferenceKey)
                .update();
        return deleted == 1;
    }

    private static UiPreferenceRecord mapRecord(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new UiPreferenceRecord(
                rs.getString("tenant_id"),
                rs.getString("principal_id"),
                rs.getString("portal"),
                rs.getString("preference_key"),
                rs.getString("value_json"),
                rs.getInt("schema_version"),
                rs.getLong("aggregate_version"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant()
        );
    }
}
