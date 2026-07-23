package com.serviceos.project.application;

import com.serviceos.project.api.ProjectPositionCode;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 项目团队与区域分工持久化端口。 */
public interface ProjectTeamRepository {
    List<MemberRow> listMembers(String tenantId, UUID projectId);

    Optional<MemberRow> findActiveMember(String tenantId, UUID projectId, UUID principalId);

    Optional<MemberRow> findMemberById(String tenantId, UUID projectId, UUID memberId);

    void insertMember(String tenantId, UUID projectId, MemberRow member, String actorId, Instant createdAt);

    List<AssignmentRow> listAssignments(String tenantId, UUID projectId);

    Optional<AssignmentRow> findActiveAssignmentForUpdate(
            String tenantId, UUID projectId, String regionCode, ProjectPositionCode position);

    Optional<AssignmentRow> findAssignmentById(String tenantId, UUID projectId, UUID assignmentId);

    void endAssignment(
            String tenantId, UUID projectId, UUID assignmentId, String actorId, Instant endedAt);

    void insertAssignment(
            String tenantId, UUID projectId, AssignmentRow assignment, String actorId, Instant createdAt);

    List<RegionRow> listEligibleRegions(String tenantId, UUID projectId);

    boolean isEligibleRegion(String tenantId, UUID projectId, String regionCode);

    List<MatchRow> match(String tenantId, UUID projectId, String regionCode);

    record MemberRow(
            UUID memberId,
            UUID principalId,
            String status,
            Instant validFrom,
            long version
    ) {
    }

    record AssignmentRow(
            UUID assignmentId,
            String regionCode,
            String regionName,
            String regionLevel,
            ProjectPositionCode position,
            UUID memberId,
            UUID principalId,
            boolean allowInheritance,
            Instant validFrom,
            long version,
            String changeReason
    ) {
    }

    record RegionRow(String code, String name, String level, String parentCode) {
    }

    record MatchRow(
            ProjectPositionCode position,
            UUID assignmentId,
            UUID principalId,
            String matchedRegionCode,
            String matchedRegionName,
            int depth
    ) {
    }
}
