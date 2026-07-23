package com.serviceos.project.infrastructure;

import com.serviceos.project.api.ProjectPositionCode;
import com.serviceos.project.application.ProjectTeamRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** PostgreSQL 项目团队与区域分工适配器。 */
@Repository
final class JdbcProjectTeamRepository implements ProjectTeamRepository {
    private final JdbcClient jdbc;

    JdbcProjectTeamRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<MemberRow> listMembers(String tenantId, UUID projectId) {
        return jdbc.sql("""
                        SELECT project_member_id, principal_id, member_status, valid_from, aggregate_version
                          FROM prj_project_member
                         WHERE tenant_id = :tenantId AND project_id = :projectId
                           AND member_status = 'ACTIVE'
                         ORDER BY created_at, project_member_id
                        """)
                .param("tenantId", tenantId)
                .param("projectId", projectId)
                .query((rs, rowNum) -> memberRow(rs))
                .list();
    }

    @Override
    public Optional<MemberRow> findActiveMember(String tenantId, UUID projectId, UUID principalId) {
        return jdbc.sql("""
                        SELECT project_member_id, principal_id, member_status, valid_from, aggregate_version
                          FROM prj_project_member
                         WHERE tenant_id = :tenantId AND project_id = :projectId
                           AND principal_id = :principalId AND member_status = 'ACTIVE'
                        """)
                .param("tenantId", tenantId)
                .param("projectId", projectId)
                .param("principalId", principalId)
                .query((rs, rowNum) -> memberRow(rs))
                .optional();
    }

    @Override
    public Optional<MemberRow> findMemberById(String tenantId, UUID projectId, UUID memberId) {
        return jdbc.sql("""
                        SELECT project_member_id, principal_id, member_status, valid_from, aggregate_version
                          FROM prj_project_member
                         WHERE tenant_id = :tenantId AND project_id = :projectId
                           AND project_member_id = :memberId
                        """)
                .param("tenantId", tenantId)
                .param("projectId", projectId)
                .param("memberId", memberId)
                .query((rs, rowNum) -> memberRow(rs))
                .optional();
    }

    @Override
    public void insertMember(
            String tenantId, UUID projectId, MemberRow member, String actorId, Instant createdAt
    ) {
        jdbc.sql("""
                        INSERT INTO prj_project_member (
                            project_member_id, tenant_id, project_id, principal_id,
                            member_status, valid_from, aggregate_version, created_by, created_at
                        ) VALUES (
                            :memberId, :tenantId, :projectId, :principalId,
                            'ACTIVE', :validFrom, 1, :actorId, :createdAt
                        )
                        """)
                .param("memberId", member.memberId())
                .param("tenantId", tenantId)
                .param("projectId", projectId)
                .param("principalId", member.principalId())
                .param("validFrom", timestamp(member.validFrom()))
                .param("actorId", actorId)
                .param("createdAt", timestamp(createdAt))
                .update();
    }

    @Override
    public List<AssignmentRow> listAssignments(String tenantId, UUID projectId) {
        return jdbc.sql("""
                        SELECT assignment_id, a.region_code, r.region_name, r.region_level,
                               position_code, project_member_id, principal_id, allow_inheritance,
                               valid_from, aggregate_version, change_reason
                          FROM prj_project_region_personnel_assignment a
                          JOIN prj_region_catalog r ON r.region_code = a.region_code
                         WHERE a.tenant_id = :tenantId AND a.project_id = :projectId
                           AND a.assignment_status = 'ACTIVE'
                         ORDER BY r.sort_order, a.region_code, a.position_code
                        """)
                .param("tenantId", tenantId)
                .param("projectId", projectId)
                .query((rs, rowNum) -> assignmentRow(rs))
                .list();
    }

    @Override
    public Optional<AssignmentRow> findActiveAssignmentForUpdate(
            String tenantId, UUID projectId, String regionCode, ProjectPositionCode position
    ) {
        return jdbc.sql("""
                        SELECT assignment_id, a.region_code, r.region_name, r.region_level,
                               position_code, project_member_id, principal_id, allow_inheritance,
                               valid_from, aggregate_version, change_reason
                          FROM prj_project_region_personnel_assignment a
                          JOIN prj_region_catalog r ON r.region_code = a.region_code
                         WHERE a.tenant_id = :tenantId AND a.project_id = :projectId
                           AND a.region_code = :regionCode AND a.position_code = :positionCode
                           AND a.assignment_status = 'ACTIVE'
                         FOR UPDATE OF a
                        """)
                .param("tenantId", tenantId)
                .param("projectId", projectId)
                .param("regionCode", regionCode)
                .param("positionCode", position.name())
                .query((rs, rowNum) -> assignmentRow(rs))
                .optional();
    }

    @Override
    public Optional<AssignmentRow> findAssignmentById(String tenantId, UUID projectId, UUID assignmentId) {
        return jdbc.sql("""
                        SELECT assignment_id, a.region_code, r.region_name, r.region_level,
                               position_code, project_member_id, principal_id, allow_inheritance,
                               valid_from, aggregate_version, change_reason
                          FROM prj_project_region_personnel_assignment a
                          JOIN prj_region_catalog r ON r.region_code = a.region_code
                         WHERE a.tenant_id = :tenantId AND a.project_id = :projectId
                           AND a.assignment_id = :assignmentId
                        """)
                .param("tenantId", tenantId)
                .param("projectId", projectId)
                .param("assignmentId", assignmentId)
                .query((rs, rowNum) -> assignmentRow(rs))
                .optional();
    }

    @Override
    public void endAssignment(
            String tenantId, UUID projectId, UUID assignmentId, String actorId, Instant endedAt
    ) {
        int updated = jdbc.sql("""
                        UPDATE prj_project_region_personnel_assignment
                           SET assignment_status = 'ENDED', valid_to = :endedAt,
                               ended_by = :actorId, ended_at = :endedAt,
                               aggregate_version = aggregate_version + 1
                         WHERE tenant_id = :tenantId AND project_id = :projectId
                           AND assignment_id = :assignmentId AND assignment_status = 'ACTIVE'
                        """)
                .param("endedAt", timestamp(endedAt))
                .param("actorId", actorId)
                .param("tenantId", tenantId)
                .param("projectId", projectId)
                .param("assignmentId", assignmentId)
                .update();
        if (updated != 1) {
            throw new IllegalStateException("待替换的项目区域岗位分工已发生变化");
        }
    }

    @Override
    public void insertAssignment(
            String tenantId, UUID projectId, AssignmentRow assignment, String actorId, Instant createdAt
    ) {
        jdbc.sql("""
                        INSERT INTO prj_project_region_personnel_assignment (
                            assignment_id, tenant_id, project_id, region_code, position_code,
                            project_member_id, principal_id, allow_inheritance, assignment_status,
                            valid_from, aggregate_version, created_by, created_at, change_reason
                        ) VALUES (
                            :assignmentId, :tenantId, :projectId, :regionCode, :positionCode,
                            :memberId, :principalId, :allowInheritance, 'ACTIVE',
                            :validFrom, 1, :actorId, :createdAt, :changeReason
                        )
                        """)
                .param("assignmentId", assignment.assignmentId())
                .param("tenantId", tenantId)
                .param("projectId", projectId)
                .param("regionCode", assignment.regionCode())
                .param("positionCode", assignment.position().name())
                .param("memberId", assignment.memberId())
                .param("principalId", assignment.principalId())
                .param("allowInheritance", assignment.allowInheritance())
                .param("validFrom", timestamp(assignment.validFrom()))
                .param("actorId", actorId)
                .param("createdAt", timestamp(createdAt))
                .param("changeReason", assignment.changeReason())
                .update();
    }

    @Override
    public List<RegionRow> listEligibleRegions(String tenantId, UUID projectId) {
        return jdbc.sql("""
                        WITH RECURSIVE eligible AS (
                            SELECT c.region_code, c.region_name, c.region_level, c.parent_code, c.sort_order
                              FROM prj_project_region p
                              JOIN prj_region_catalog c ON c.region_code = p.region_code
                             WHERE p.tenant_id = :tenantId AND p.project_id = :projectId
                               AND p.valid_to IS NULL AND c.region_status = 'ACTIVE'
                            UNION
                            SELECT c.region_code, c.region_name, c.region_level, c.parent_code, c.sort_order
                              FROM prj_region_catalog c
                              JOIN eligible parent ON c.parent_code = parent.region_code
                             WHERE c.region_status = 'ACTIVE'
                        )
                        SELECT region_code, region_name, region_level, parent_code
                          FROM eligible
                         ORDER BY sort_order, region_code
                        """)
                .param("tenantId", tenantId)
                .param("projectId", projectId)
                .query((rs, rowNum) -> new RegionRow(
                        rs.getString("region_code"), rs.getString("region_name"),
                        rs.getString("region_level"), rs.getString("parent_code")))
                .list();
    }

    @Override
    public boolean isEligibleRegion(String tenantId, UUID projectId, String regionCode) {
        return jdbc.sql("""
                        WITH RECURSIVE ancestors AS (
                            SELECT region_code, parent_code
                              FROM prj_region_catalog
                             WHERE region_code = :regionCode AND region_status = 'ACTIVE'
                            UNION ALL
                            SELECT parent.region_code, parent.parent_code
                              FROM prj_region_catalog parent
                              JOIN ancestors child ON child.parent_code = parent.region_code
                             WHERE parent.region_status = 'ACTIVE'
                        )
                        SELECT EXISTS (
                            SELECT 1
                              FROM ancestors a
                              JOIN prj_project_region p ON p.region_code = a.region_code
                             WHERE p.tenant_id = :tenantId AND p.project_id = :projectId
                               AND p.valid_to IS NULL
                        )
                        """)
                .param("regionCode", regionCode)
                .param("tenantId", tenantId)
                .param("projectId", projectId)
                .query(Boolean.class)
                .single();
    }

    @Override
    public List<MatchRow> match(String tenantId, UUID projectId, String regionCode) {
        return jdbc.sql("""
                        WITH RECURSIVE ancestors AS (
                            SELECT region_code, parent_code, 0 AS depth
                              FROM prj_region_catalog
                             WHERE region_code = :regionCode AND region_status = 'ACTIVE'
                            UNION ALL
                            SELECT parent.region_code, parent.parent_code, child.depth + 1
                              FROM prj_region_catalog parent
                              JOIN ancestors child ON child.parent_code = parent.region_code
                             WHERE parent.region_status = 'ACTIVE'
                        ), ranked AS (
                            SELECT a.position_code, a.assignment_id, a.principal_id,
                                   a.region_code, r.region_name, ancestors.depth,
                                   row_number() OVER (
                                       PARTITION BY a.position_code
                                       ORDER BY ancestors.depth, a.created_at DESC, a.assignment_id
                                   ) AS position_rank
                              FROM ancestors
                              JOIN prj_project_region_personnel_assignment a
                                ON a.region_code = ancestors.region_code
                               AND a.tenant_id = :tenantId AND a.project_id = :projectId
                               AND a.assignment_status = 'ACTIVE'
                               AND (ancestors.depth = 0 OR a.allow_inheritance)
                              JOIN prj_region_catalog r ON r.region_code = a.region_code
                        )
                        SELECT position_code, assignment_id, principal_id,
                               region_code, region_name, depth
                          FROM ranked
                         WHERE position_rank = 1
                         ORDER BY position_code
                        """)
                .param("regionCode", regionCode)
                .param("tenantId", tenantId)
                .param("projectId", projectId)
                .query((rs, rowNum) -> new MatchRow(
                        ProjectPositionCode.valueOf(rs.getString("position_code")),
                        rs.getObject("assignment_id", UUID.class),
                        rs.getObject("principal_id", UUID.class),
                        rs.getString("region_code"), rs.getString("region_name"), rs.getInt("depth")))
                .list();
    }

    private static MemberRow memberRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new MemberRow(
                rs.getObject("project_member_id", UUID.class),
                rs.getObject("principal_id", UUID.class),
                rs.getString("member_status"),
                rs.getTimestamp("valid_from").toInstant(),
                rs.getLong("aggregate_version"));
    }

    private static AssignmentRow assignmentRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new AssignmentRow(
                rs.getObject("assignment_id", UUID.class), rs.getString("region_code"),
                rs.getString("region_name"), rs.getString("region_level"),
                ProjectPositionCode.valueOf(rs.getString("position_code")),
                rs.getObject("project_member_id", UUID.class),
                rs.getObject("principal_id", UUID.class), rs.getBoolean("allow_inheritance"),
                rs.getTimestamp("valid_from").toInstant(), rs.getLong("aggregate_version"),
                rs.getString("change_reason"));
    }

    private static Timestamp timestamp(Instant instant) {
        return Timestamp.from(instant);
    }

}
