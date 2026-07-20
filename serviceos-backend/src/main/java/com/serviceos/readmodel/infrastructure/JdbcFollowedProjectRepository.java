package com.serviceos.readmodel.infrastructure;

import com.serviceos.readmodel.application.FollowedProjectRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
final class JdbcFollowedProjectRepository implements FollowedProjectRepository {
    private final JdbcClient jdbc;

    JdbcFollowedProjectRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<FollowedProjectRecord> listByOwnerOrdered(
            String tenantId, String principalId, String portal, int fetchLimit
    ) {
        return jdbc.sql("""
                SELECT tenant_id, principal_id, portal, project_id, display_ref, followed_at, created_at
                  FROM rdm_followed_project
                 WHERE tenant_id = :tenantId
                   AND principal_id = :principalId
                   AND portal = :portal
                 ORDER BY followed_at DESC
                 LIMIT :limit
                """)
                .param("tenantId", tenantId)
                .param("principalId", principalId)
                .param("portal", portal)
                .param("limit", fetchLimit)
                .query((rs, rowNum) -> new FollowedProjectRecord(
                        rs.getString("tenant_id"),
                        rs.getString("principal_id"),
                        rs.getString("portal"),
                        rs.getObject("project_id", UUID.class),
                        rs.getString("display_ref"),
                        rs.getTimestamp("followed_at").toInstant(),
                        rs.getTimestamp("created_at").toInstant()))
                .list();
    }

    @Override
    public Optional<FollowedProjectRecord> findOwned(
            String tenantId, String principalId, String portal, UUID projectId
    ) {
        return jdbc.sql("""
                SELECT tenant_id, principal_id, portal, project_id, display_ref, followed_at, created_at
                  FROM rdm_followed_project
                 WHERE tenant_id = :tenantId
                   AND principal_id = :principalId
                   AND portal = :portal
                   AND project_id = :projectId
                """)
                .param("tenantId", tenantId)
                .param("principalId", principalId)
                .param("portal", portal)
                .param("projectId", projectId)
                .query((rs, rowNum) -> new FollowedProjectRecord(
                        rs.getString("tenant_id"),
                        rs.getString("principal_id"),
                        rs.getString("portal"),
                        rs.getObject("project_id", UUID.class),
                        rs.getString("display_ref"),
                        rs.getTimestamp("followed_at").toInstant(),
                        rs.getTimestamp("created_at").toInstant()))
                .optional();
    }

    @Override
    public void upsert(FollowedProjectRecord record) {
        jdbc.sql("""
                INSERT INTO rdm_followed_project (
                    tenant_id, principal_id, portal, project_id, display_ref, followed_at, created_at
                ) VALUES (
                    :tenantId, :principalId, :portal, :projectId, :displayRef, :followedAt, :createdAt
                )
                ON CONFLICT (tenant_id, principal_id, portal, project_id) DO UPDATE
                   SET display_ref = EXCLUDED.display_ref,
                       followed_at = EXCLUDED.followed_at
                """)
                .param("tenantId", record.tenantId())
                .param("principalId", record.principalId())
                .param("portal", record.portal())
                .param("projectId", record.projectId())
                .param("displayRef", record.displayRef())
                .param("followedAt", Timestamp.from(record.followedAt()))
                .param("createdAt", Timestamp.from(record.createdAt()))
                .update();
    }

    @Override
    public void deleteOwned(String tenantId, String principalId, String portal, UUID projectId) {
        jdbc.sql("""
                DELETE FROM rdm_followed_project
                 WHERE tenant_id = :tenantId
                   AND principal_id = :principalId
                   AND portal = :portal
                   AND project_id = :projectId
                """)
                .param("tenantId", tenantId)
                .param("principalId", principalId)
                .param("portal", portal)
                .param("projectId", projectId)
                .update();
    }
}
