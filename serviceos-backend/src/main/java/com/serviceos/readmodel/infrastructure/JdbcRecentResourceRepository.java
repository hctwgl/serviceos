package com.serviceos.readmodel.infrastructure;

import com.serviceos.readmodel.application.RecentResourceRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

@Repository
class JdbcRecentResourceRepository implements RecentResourceRepository {
    private final JdbcClient jdbc;

    JdbcRecentResourceRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<RecentResourceRecord> listByOwnerOrdered(
            String tenantId, String principalId, String portal, int fetchLimit
    ) {
        return jdbc.sql("""
                        SELECT tenant_id, principal_id, portal, resource_type, resource_id,
                               page_id, display_ref, last_visited_at, created_at
                          FROM rdm_recent_resource
                         WHERE tenant_id = :tenantId
                           AND principal_id = :principalId
                           AND portal = :portal
                         ORDER BY last_visited_at DESC, resource_type ASC, resource_id ASC
                         LIMIT :fetchLimit
                        """)
                .param("tenantId", tenantId)
                .param("principalId", principalId)
                .param("portal", portal)
                .param("fetchLimit", fetchLimit)
                .query((rs, rowNum) -> mapRecord(rs))
                .list();
    }

    @Override
    public Optional<RecentResourceRecord> findOwned(
            String tenantId,
            String principalId,
            String portal,
            String resourceType,
            String resourceId
    ) {
        return jdbc.sql("""
                        SELECT tenant_id, principal_id, portal, resource_type, resource_id,
                               page_id, display_ref, last_visited_at, created_at
                          FROM rdm_recent_resource
                         WHERE tenant_id = :tenantId
                           AND principal_id = :principalId
                           AND portal = :portal
                           AND resource_type = :resourceType
                           AND resource_id = :resourceId
                        """)
                .param("tenantId", tenantId)
                .param("principalId", principalId)
                .param("portal", portal)
                .param("resourceType", resourceType)
                .param("resourceId", resourceId)
                .query((rs, rowNum) -> mapRecord(rs))
                .optional();
    }

    @Override
    public void upsert(RecentResourceRecord record) {
        jdbc.sql("""
                INSERT INTO rdm_recent_resource (
                    tenant_id, principal_id, portal, resource_type, resource_id,
                    page_id, display_ref, last_visited_at, created_at
                ) VALUES (
                    :tenantId, :principalId, :portal, :resourceType, :resourceId,
                    :pageId, :displayRef, :lastVisitedAt, :createdAt
                )
                ON CONFLICT (tenant_id, principal_id, portal, resource_type, resource_id)
                DO UPDATE SET
                    page_id = EXCLUDED.page_id,
                    display_ref = EXCLUDED.display_ref,
                    last_visited_at = EXCLUDED.last_visited_at
                """)
                .param("tenantId", record.tenantId())
                .param("principalId", record.principalId())
                .param("portal", record.portal())
                .param("resourceType", record.resourceType())
                .param("resourceId", record.resourceId())
                .param("pageId", record.pageId())
                .param("displayRef", record.displayRef())
                .param("lastVisitedAt", Timestamp.from(record.lastVisitedAt()))
                .param("createdAt", Timestamp.from(record.createdAt()))
                .update();
    }

    @Override
    public boolean deleteOwned(
            String tenantId,
            String principalId,
            String portal,
            String resourceType,
            String resourceId
    ) {
        int deleted = jdbc.sql("""
                DELETE FROM rdm_recent_resource
                 WHERE tenant_id = :tenantId
                   AND principal_id = :principalId
                   AND portal = :portal
                   AND resource_type = :resourceType
                   AND resource_id = :resourceId
                """)
                .param("tenantId", tenantId)
                .param("principalId", principalId)
                .param("portal", portal)
                .param("resourceType", resourceType)
                .param("resourceId", resourceId)
                .update();
        return deleted == 1;
    }

    @Override
    public int trimExcess(String tenantId, String principalId, String portal, int keepLimit) {
        // 保留最近 keepLimit 条；其余按 last_visited_at 升序删除。
        return jdbc.sql("""
                DELETE FROM rdm_recent_resource AS r
                 WHERE r.tenant_id = :tenantId
                   AND r.principal_id = :principalId
                   AND r.portal = :portal
                   AND (r.resource_type, r.resource_id) IN (
                        SELECT x.resource_type, x.resource_id
                          FROM rdm_recent_resource x
                         WHERE x.tenant_id = :tenantId
                           AND x.principal_id = :principalId
                           AND x.portal = :portal
                         ORDER BY x.last_visited_at ASC, x.resource_type DESC, x.resource_id DESC
                         OFFSET :keepLimit
                   )
                """)
                .param("tenantId", tenantId)
                .param("principalId", principalId)
                .param("portal", portal)
                .param("keepLimit", keepLimit)
                .update();
    }

    private static RecentResourceRecord mapRecord(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new RecentResourceRecord(
                rs.getString("tenant_id"),
                rs.getString("principal_id"),
                rs.getString("portal"),
                rs.getString("resource_type"),
                rs.getString("resource_id"),
                rs.getString("page_id"),
                rs.getString("display_ref"),
                rs.getTimestamp("last_visited_at").toInstant(),
                rs.getTimestamp("created_at").toInstant()
        );
    }
}
