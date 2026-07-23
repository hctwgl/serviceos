package com.serviceos.project.infrastructure;

import com.serviceos.project.application.ProjectRepository;
import com.serviceos.project.domain.Project;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

import static com.serviceos.shared.infrastructure.PostgresJdbcParameters.timestamptz;

@Repository
final class JdbcProjectRepository implements ProjectRepository {
    private static final ObjectMapper JSON = JsonMapper.builder().build();

    private final JdbcClient jdbc;

    JdbcProjectRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(Project project) {
        jdbc.sql("""
                        INSERT INTO prj_project (
                            project_id, tenant_id, project_code, client_id, project_name,
                            starts_on, ends_on, project_status, aggregate_version, created_at
                        ) VALUES (
                            :id, :tenantId, :code, :clientId, :name,
                            :startsOn, :endsOn, :status, :version, :createdAt
                        )
                        """)
                .param("id", project.id())
                .param("tenantId", project.tenantId())
                .param("code", project.code())
                .param("clientId", project.clientId())
                .param("name", project.name())
                .param("startsOn", project.startsOn())
                .param("endsOn", project.endsOn(), java.sql.Types.DATE)
                .param("status", project.status().name())
                .param("version", project.version())
                .param("createdAt", timestamptz(project.createdAt()))
                .update();
    }

    @Override
    public void insertRegionBindings(Project project, String createdBy) {
        for (String regionCode : project.regionCodes()) {
            jdbc.sql("""
                            INSERT INTO prj_project_region (
                                project_region_id, tenant_id, project_id, region_code,
                                valid_from, created_by, created_at
                            ) VALUES (
                                :bindingId, :tenantId, :projectId, :regionCode,
                                :validFrom, :createdBy, :createdAt
                            )
                            """)
                    .param("bindingId", UUID.randomUUID())
                    .param("tenantId", project.tenantId())
                    .param("projectId", project.id())
                    .param("regionCode", regionCode)
                    .param("validFrom", timestamptz(project.createdAt()))
                    .param("createdBy", createdBy)
                    .param("createdAt", timestamptz(project.createdAt()))
                    .update();
        }
    }

    @Override
    public void insertNetworkBindings(Project project, String createdBy) {
        for (String networkId : project.networkIds()) {
            jdbc.sql("""
                            INSERT INTO prj_project_network (
                                project_network_id, tenant_id, project_id, network_id,
                                valid_from, created_by, created_at
                            ) VALUES (
                                :bindingId, :tenantId, :projectId, :networkId,
                                :validFrom, :createdBy, :createdAt
                            )
                            """)
                    .param("bindingId", UUID.randomUUID())
                    .param("tenantId", project.tenantId())
                    .param("projectId", project.id())
                    .param("networkId", networkId)
                    .param("validFrom", timestamptz(project.createdAt()))
                    .param("createdBy", createdBy)
                    .param("createdAt", timestamptz(project.createdAt()))
                    .update();
        }
    }

    @Override
    public Optional<Project> findById(String tenantId, UUID projectId) {
        return find(tenantId, projectId, false);
    }

    @Override
    public Optional<Project> findByIdForUpdate(String tenantId, UUID projectId) {
        return find(tenantId, projectId, true);
    }

    private Optional<Project> find(String tenantId, UUID projectId, boolean forUpdate) {
        String sql = """
                        SELECT project_id, tenant_id, project_code, client_id, project_name,
                               starts_on, ends_on, project_status, aggregate_version, created_at
                          FROM prj_project
                         WHERE tenant_id = :tenantId AND project_id = :projectId
                        """ + (forUpdate ? " FOR UPDATE" : "");
        return jdbc.sql(sql)
                .param("tenantId", tenantId)
                .param("projectId", projectId)
                .query((rs, rowNum) -> new Project(
                        rs.getObject("project_id", UUID.class),
                        rs.getString("tenant_id"),
                        rs.getString("project_code"),
                        rs.getString("client_id"),
                        rs.getString("project_name"),
                        rs.getObject("starts_on", java.time.LocalDate.class),
                        rs.getObject("ends_on", java.time.LocalDate.class),
                        findActiveRegions(tenantId, projectId),
                        findOpenNetworkBindings(tenantId, projectId),
                        Project.Status.valueOf(rs.getString("project_status")),
                        rs.getLong("aggregate_version"),
                        rs.getTimestamp("created_at").toInstant()))
                .optional();
    }

    @Override
    public boolean advanceVersion(String tenantId, UUID projectId, long expectedVersion) {
        return jdbc.sql("""
                        UPDATE prj_project
                           SET aggregate_version = aggregate_version + 1
                         WHERE tenant_id = :tenantId AND project_id = :projectId
                           AND aggregate_version = :expectedVersion
                        """)
                .param("tenantId", tenantId)
                .param("projectId", projectId)
                .param("expectedVersion", expectedVersion)
                .update() == 1;
    }

    @Override
    public boolean activate(String tenantId, UUID projectId, long expectedVersion) {
        return jdbc.sql("""
                        UPDATE prj_project
                           SET project_status = 'ACTIVE',
                               aggregate_version = aggregate_version + 1
                         WHERE tenant_id = :tenantId
                           AND project_id = :projectId
                           AND project_status = 'DRAFT'
                           AND aggregate_version = :expectedVersion
                        """)
                .param("tenantId", tenantId)
                .param("projectId", projectId)
                .param("expectedVersion", expectedVersion)
                .update() == 1;
    }

    @Override
    public void reviseRegionBindings(
            String tenantId, UUID projectId, List<String> removed, List<String> added,
            String actorId, Instant revisedAt
    ) {
        for (String regionCode : removed) {
            int updated = jdbc.sql("""
                            UPDATE prj_project_region
                               SET valid_to = :revisedAt, ended_by = :actorId, ended_at = :revisedAt
                             WHERE tenant_id = :tenantId AND project_id = :projectId
                               AND region_code = :regionCode AND valid_to IS NULL
                            """)
                    .param("revisedAt", timestamptz(revisedAt))
                    .param("actorId", actorId)
                    .param("tenantId", tenantId)
                    .param("projectId", projectId)
                    .param("regionCode", regionCode)
                    .update();
            if (updated != 1) {
                throw new IllegalStateException("待终止的项目 REGION 关系不存在或不唯一");
            }
        }
        for (String regionCode : added) {
            insertRegionBinding(tenantId, projectId, regionCode, actorId, revisedAt);
        }
    }

    @Override
    public void reviseNetworkBindings(
            String tenantId, UUID projectId, List<String> removed, List<String> added,
            String actorId, Instant revisedAt
    ) {
        for (String networkId : removed) {
            int updated = jdbc.sql("""
                            UPDATE prj_project_network
                               SET valid_to = :revisedAt, ended_by = :actorId, ended_at = :revisedAt
                             WHERE tenant_id = :tenantId AND project_id = :projectId
                               AND network_id = :networkId AND valid_to IS NULL
                            """)
                    .param("revisedAt", timestamptz(revisedAt))
                    .param("actorId", actorId)
                    .param("tenantId", tenantId)
                    .param("projectId", projectId)
                    .param("networkId", networkId)
                    .update();
            if (updated != 1) {
                throw new IllegalStateException("待终止的项目 NETWORK 关系不存在或不唯一");
            }
        }
        for (String networkId : added) {
            insertNetworkBinding(tenantId, projectId, networkId, actorId, revisedAt);
        }
    }

    private void insertRegionBinding(
            String tenantId, UUID projectId, String regionCode, String actorId, Instant revisedAt
    ) {
        jdbc.sql("""
                        INSERT INTO prj_project_region (
                            project_region_id, tenant_id, project_id, region_code,
                            valid_from, created_by, created_at
                        ) VALUES (
                            :bindingId, :tenantId, :projectId, :regionCode,
                            :revisedAt, :actorId, :revisedAt
                        )
                        """)
                .param("bindingId", UUID.randomUUID())
                .param("tenantId", tenantId)
                .param("projectId", projectId)
                .param("regionCode", regionCode)
                .param("revisedAt", timestamptz(revisedAt))
                .param("actorId", actorId)
                .update();
    }

    private void insertNetworkBinding(
            String tenantId, UUID projectId, String networkId, String actorId, Instant revisedAt
    ) {
        jdbc.sql("""
                        INSERT INTO prj_project_network (
                            project_network_id, tenant_id, project_id, network_id,
                            valid_from, created_by, created_at
                        ) VALUES (
                            :bindingId, :tenantId, :projectId, :networkId,
                            :revisedAt, :actorId, :revisedAt
                        )
                        """)
                .param("bindingId", UUID.randomUUID())
                .param("tenantId", tenantId)
                .param("projectId", projectId)
                .param("networkId", networkId)
                .param("revisedAt", timestamptz(revisedAt))
                .param("actorId", actorId)
                .update();
    }

    @Override
    public void insertScopeRevision(com.serviceos.project.application.ProjectScopeRevision revision) {
        jdbc.sql("""
                        INSERT INTO prj_project_scope_revision (
                            revision_id, tenant_id, project_id, expected_version, aggregate_version,
                            region_codes, network_ids, added_region_codes, removed_region_codes,
                            added_network_ids, removed_network_ids, reason, revised_by, revised_at
                        ) VALUES (
                            :revisionId, :tenantId, :projectId, :expectedVersion, :aggregateVersion,
                            CAST(:regionCodes AS jsonb), CAST(:networkIds AS jsonb),
                            CAST(:addedRegionCodes AS jsonb), CAST(:removedRegionCodes AS jsonb),
                            CAST(:addedNetworkIds AS jsonb), CAST(:removedNetworkIds AS jsonb),
                            :reason, :revisedBy, :revisedAt
                        )
                        """)
                .param("revisionId", revision.revisionId())
                .param("tenantId", revision.tenantId())
                .param("projectId", revision.projectId())
                .param("expectedVersion", revision.expectedVersion())
                .param("aggregateVersion", revision.aggregateVersion())
                .param("regionCodes", json(revision.regionCodes()))
                .param("networkIds", json(revision.networkIds()))
                .param("addedRegionCodes", json(revision.addedRegionCodes()))
                .param("removedRegionCodes", json(revision.removedRegionCodes()))
                .param("addedNetworkIds", json(revision.addedNetworkIds()))
                .param("removedNetworkIds", json(revision.removedNetworkIds()))
                .param("reason", revision.reason())
                .param("revisedBy", revision.revisedBy())
                .param("revisedAt", timestamptz(revision.revisedAt()))
                .update();
    }

    @Override
    public Optional<com.serviceos.project.application.ProjectScopeRevision> findScopeRevision(
            String tenantId, UUID revisionId
    ) {
        return jdbc.sql("""
                        SELECT revision_id, tenant_id, project_id, expected_version, aggregate_version,
                               region_codes::text, network_ids::text,
                               added_region_codes::text, removed_region_codes::text,
                               added_network_ids::text, removed_network_ids::text,
                               reason, revised_by, revised_at
                          FROM prj_project_scope_revision
                         WHERE tenant_id = :tenantId AND revision_id = :revisionId
                        """)
                .param("tenantId", tenantId)
                .param("revisionId", revisionId)
                .query((rs, rowNum) -> new com.serviceos.project.application.ProjectScopeRevision(
                        rs.getObject("revision_id", UUID.class), rs.getString("tenant_id"),
                        rs.getObject("project_id", UUID.class), rs.getLong("expected_version"),
                        rs.getLong("aggregate_version"), jsonList(rs.getString("region_codes")),
                        jsonList(rs.getString("network_ids")), jsonList(rs.getString("added_region_codes")),
                        jsonList(rs.getString("removed_region_codes")),
                        jsonList(rs.getString("added_network_ids")),
                        jsonList(rs.getString("removed_network_ids")), rs.getString("reason"),
                        rs.getString("revised_by"), rs.getTimestamp("revised_at").toInstant()))
                .optional();
    }

    private static String json(List<String> values) {
        try {
            return JSON.writeValueAsString(values);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("项目范围关系不能序列化", exception);
        }
    }

    private static List<String> jsonList(String value) {
        try {
            return List.of(JSON.readValue(value, String[].class));
        } catch (JacksonException exception) {
            throw new IllegalStateException("项目范围修订包含非法 JSON", exception);
        }
    }

    private List<String> findActiveRegions(String tenantId, UUID projectId) {
        return jdbc.sql("""
                        SELECT region_code FROM prj_project_region
                         WHERE tenant_id = :tenantId AND project_id = :projectId
                           AND valid_to IS NULL
                         ORDER BY region_code
                        """)
                .param("tenantId", tenantId)
                .param("projectId", projectId)
                .query(String.class)
                .list();
    }

    private List<String> findOpenNetworkBindings(String tenantId, UUID projectId) {
        return jdbc.sql("""
                        SELECT network_id FROM prj_project_network
                         WHERE tenant_id = :tenantId AND project_id = :projectId
                           AND valid_to IS NULL
                         ORDER BY network_id
                        """)
                .param("tenantId", tenantId)
                .param("projectId", projectId)
                .query(String.class)
                .list();
    }
}
