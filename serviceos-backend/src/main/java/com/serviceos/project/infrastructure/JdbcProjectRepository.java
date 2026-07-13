package com.serviceos.project.infrastructure;

import com.serviceos.project.application.ProjectRepository;
import com.serviceos.project.domain.Project;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

import static com.serviceos.shared.infrastructure.PostgresJdbcParameters.timestamptz;

@Repository
final class JdbcProjectRepository implements ProjectRepository {
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
    public Optional<Project> findById(String tenantId, UUID projectId) {
        return jdbc.sql("""
                        SELECT project_id, tenant_id, project_code, client_id, project_name,
                               starts_on, ends_on, project_status, aggregate_version, created_at
                          FROM prj_project
                         WHERE tenant_id = :tenantId AND project_id = :projectId
                        """)
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
                        Project.Status.valueOf(rs.getString("project_status")),
                        rs.getLong("aggregate_version"),
                        rs.getTimestamp("created_at").toInstant()))
                .optional();
    }
}
