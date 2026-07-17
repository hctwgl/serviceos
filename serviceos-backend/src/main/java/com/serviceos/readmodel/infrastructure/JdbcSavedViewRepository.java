package com.serviceos.readmodel.infrastructure;

import com.serviceos.readmodel.api.SavedView;
import com.serviceos.readmodel.api.SavedViewVisibility;
import com.serviceos.readmodel.application.SavedViewJson;
import com.serviceos.readmodel.application.SavedViewRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class JdbcSavedViewRepository implements SavedViewRepository {
    private final JdbcClient jdbc;

    JdbcSavedViewRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
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
        String sql = """
                SELECT saved_view_id, tenant_id, principal_id, portal, page_id, name,
                       visibility, shared_scope_ref, schema_version,
                       filter_json::text AS filter_json, sort_json::text AS sort_json,
                       column_json::text AS column_json, is_default, aggregate_version,
                       created_at, updated_at
                  FROM rdm_saved_view
                 WHERE tenant_id = :tenantId
                   AND portal = :portal
                   AND page_id = :pageId
                   AND (
                        principal_id = :principalId
                     OR visibility = 'TENANT'
                     OR (visibility = 'ROLE' AND shared_scope_ref IN (:roleIds))
                   )
                 ORDER BY CASE WHEN principal_id = :principalId THEN 0 ELSE 1 END,
                          is_default DESC, updated_at DESC, name ASC
                """;
        if (roleIds.isEmpty()) {
            sql = """
                    SELECT saved_view_id, tenant_id, principal_id, portal, page_id, name,
                           visibility, shared_scope_ref, schema_version,
                           filter_json::text AS filter_json, sort_json::text AS sort_json,
                           column_json::text AS column_json, is_default, aggregate_version,
                           created_at, updated_at
                      FROM rdm_saved_view
                     WHERE tenant_id = :tenantId
                       AND portal = :portal
                       AND page_id = :pageId
                       AND (
                            principal_id = :principalId
                         OR visibility = 'TENANT'
                       )
                     ORDER BY CASE WHEN principal_id = :principalId THEN 0 ELSE 1 END,
                              is_default DESC, updated_at DESC, name ASC
                    """;
            return jdbc.sql(sql)
                    .param("tenantId", tenantId)
                    .param("principalId", principalId)
                    .param("portal", portal)
                    .param("pageId", pageId)
                    .query((rs, rowNum) -> SavedViewJson.toView(mapRecord(rs)))
                    .list();
        }
        return jdbc.sql(sql)
                .param("tenantId", tenantId)
                .param("principalId", principalId)
                .param("portal", portal)
                .param("pageId", pageId)
                .param("roleIds", roleIds)
                .query((rs, rowNum) -> SavedViewJson.toView(mapRecord(rs)))
                .list();
    }

    @Override
    public Optional<SavedViewRecord> findOwned(String tenantId, String principalId, UUID savedViewId) {
        return jdbc.sql("""
                        SELECT saved_view_id, tenant_id, principal_id, portal, page_id, name,
                               visibility, shared_scope_ref, schema_version,
                               filter_json::text AS filter_json, sort_json::text AS sort_json,
                               column_json::text AS column_json, is_default, aggregate_version,
                               created_at, updated_at
                          FROM rdm_saved_view
                         WHERE saved_view_id = :id
                           AND tenant_id = :tenantId
                           AND principal_id = :principalId
                        """)
                .param("id", savedViewId)
                .param("tenantId", tenantId)
                .param("principalId", principalId)
                .query((rs, rowNum) -> mapRecord(rs))
                .optional();
    }

    @Override
    public void insert(SavedViewRecord record) {
        jdbc.sql("""
                INSERT INTO rdm_saved_view (
                    saved_view_id, tenant_id, principal_id, portal, page_id, name,
                    visibility, shared_scope_ref, schema_version,
                    filter_json, sort_json, column_json, is_default, aggregate_version, created_at, updated_at
                ) VALUES (
                    :id, :tenantId, :principalId, :portal, :pageId, :name,
                    :visibility, :sharedScopeRef, :schemaVersion,
                    CAST(:filterJson AS jsonb), CAST(:sortJson AS jsonb), CAST(:columnJson AS jsonb),
                    :isDefault, :aggregateVersion, :createdAt, :updatedAt
                )
                """)
                .param("id", record.id())
                .param("tenantId", record.tenantId())
                .param("principalId", record.principalId())
                .param("portal", record.portal())
                .param("pageId", record.pageId())
                .param("name", record.name())
                .param("visibility", record.visibility().name())
                .param("sharedScopeRef", record.sharedScopeRef())
                .param("schemaVersion", record.schemaVersion())
                .param("filterJson", record.filterJson())
                .param("sortJson", record.sortJson())
                .param("columnJson", record.columnJson())
                .param("isDefault", record.isDefault())
                .param("aggregateVersion", record.aggregateVersion())
                .param("createdAt", Timestamp.from(record.createdAt()))
                .param("updatedAt", Timestamp.from(record.updatedAt()))
                .update();
    }

    @Override
    public boolean update(SavedViewRecord record, long expectedVersion) {
        int updated = jdbc.sql("""
                UPDATE rdm_saved_view
                   SET name = :name,
                       schema_version = :schemaVersion,
                       filter_json = CAST(:filterJson AS jsonb),
                       sort_json = CAST(:sortJson AS jsonb),
                       column_json = CAST(:columnJson AS jsonb),
                       is_default = :isDefault,
                       aggregate_version = :aggregateVersion,
                       updated_at = :updatedAt
                 WHERE saved_view_id = :id
                   AND tenant_id = :tenantId
                   AND principal_id = :principalId
                   AND aggregate_version = :expectedVersion
                """)
                .param("name", record.name())
                .param("schemaVersion", record.schemaVersion())
                .param("filterJson", record.filterJson())
                .param("sortJson", record.sortJson())
                .param("columnJson", record.columnJson())
                .param("isDefault", record.isDefault())
                .param("aggregateVersion", record.aggregateVersion())
                .param("updatedAt", Timestamp.from(record.updatedAt()))
                .param("id", record.id())
                .param("tenantId", record.tenantId())
                .param("principalId", record.principalId())
                .param("expectedVersion", expectedVersion)
                .update();
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
            java.time.Instant updatedAt
    ) {
        int updated = jdbc.sql("""
                UPDATE rdm_saved_view
                   SET visibility = :visibility,
                       shared_scope_ref = :sharedScopeRef,
                       aggregate_version = :aggregateVersion,
                       updated_at = :updatedAt
                 WHERE saved_view_id = :id
                   AND tenant_id = :tenantId
                   AND principal_id = :principalId
                   AND aggregate_version = :expectedVersion
                """)
                .param("visibility", visibility.name())
                .param("sharedScopeRef", sharedScopeRef)
                .param("aggregateVersion", nextVersion)
                .param("updatedAt", Timestamp.from(updatedAt))
                .param("id", savedViewId)
                .param("tenantId", tenantId)
                .param("principalId", principalId)
                .param("expectedVersion", expectedVersion)
                .update();
        return updated == 1;
    }

    @Override
    public boolean deleteOwned(String tenantId, String principalId, UUID savedViewId) {
        int deleted = jdbc.sql("""
                DELETE FROM rdm_saved_view
                 WHERE saved_view_id = :id
                   AND tenant_id = :tenantId
                   AND principal_id = :principalId
                """)
                .param("id", savedViewId)
                .param("tenantId", tenantId)
                .param("principalId", principalId)
                .update();
        return deleted == 1;
    }

    @Override
    public void clearDefault(String tenantId, String principalId, String portal, String pageId) {
        jdbc.sql("""
                UPDATE rdm_saved_view
                   SET is_default = FALSE,
                       updated_at = now()
                 WHERE tenant_id = :tenantId
                   AND principal_id = :principalId
                   AND portal = :portal
                   AND page_id = :pageId
                   AND is_default
                """)
                .param("tenantId", tenantId)
                .param("principalId", principalId)
                .param("portal", portal)
                .param("pageId", pageId)
                .update();
    }

    private static SavedViewRecord mapRecord(java.sql.ResultSet rs) throws java.sql.SQLException {
        String visibility = rs.getString("visibility");
        return new SavedViewRecord(
                rs.getObject("saved_view_id", UUID.class),
                rs.getString("tenant_id"),
                rs.getString("principal_id"),
                rs.getString("portal"),
                rs.getString("page_id"),
                rs.getString("name"),
                visibility == null ? SavedViewVisibility.PRIVATE : SavedViewVisibility.valueOf(visibility),
                rs.getString("shared_scope_ref"),
                rs.getInt("schema_version"),
                rs.getString("filter_json"),
                rs.getString("sort_json"),
                rs.getString("column_json"),
                rs.getBoolean("is_default"),
                rs.getLong("aggregate_version"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant()
        );
    }
}
