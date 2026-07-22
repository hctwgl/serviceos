package com.serviceos.project.application;

import com.serviceos.identity.api.PrincipalPersonaQuery;
import com.serviceos.project.api.ProjectPositionCode;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import com.serviceos.workorder.api.WorkOrderProjectPersonnelResolution;
import com.serviceos.workorder.api.WorkOrderProjectPersonnelResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** 工单创建使用的项目区域人员解析实现。 */
@Service
final class DefaultProjectPersonnelResolver implements WorkOrderProjectPersonnelResolver {
    private final ProjectRepository projects;
    private final ProjectTeamRepository teams;
    private final PrincipalPersonaQuery personas;

    DefaultProjectPersonnelResolver(
            ProjectRepository projects,
            ProjectTeamRepository teams,
            PrincipalPersonaQuery personas
    ) {
        this.projects = projects;
        this.teams = teams;
        this.personas = personas;
    }

    @Override
    @Transactional(readOnly = true)
    public WorkOrderProjectPersonnelResolution resolve(
            String tenantId, UUID projectId, String regionCode, Instant matchedAt
    ) {
        Objects.requireNonNull(matchedAt, "matchedAt must not be null");
        if (tenantId == null || tenantId.isBlank()) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, "租户标识不能为空");
        }
        if (projectId == null) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, "项目标识不能为空");
        }
        if (regionCode == null || !regionCode.matches("^[0-9]{6}$")) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, "工单行政区编码必须为六位数字");
        }
        projects.findById(tenantId, projectId)
                .orElseThrow(() -> new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND, "项目不存在"));

        List<ProjectTeamRepository.MatchRow> matches = teams.match(tenantId, projectId, regionCode);
        Map<UUID, String> names = personas.displayNames(
                tenantId, matches.stream().map(ProjectTeamRepository.MatchRow::principalId).toList());
        Map<ProjectPositionCode, ProjectTeamRepository.MatchRow> byPosition =
                new EnumMap<>(ProjectPositionCode.class);
        matches.forEach(item -> byPosition.put(item.position(), item));

        List<WorkOrderProjectPersonnelResolution.Item> items = java.util.Arrays.stream(ProjectPositionCode.values())
                .map(position -> item(position, byPosition.get(position), names))
                .toList();
        return new WorkOrderProjectPersonnelResolution(projectId, regionCode, items, matchedAt);
    }

    private static WorkOrderProjectPersonnelResolution.Item item(
            ProjectPositionCode position,
            ProjectTeamRepository.MatchRow row,
            Map<UUID, String> names
    ) {
        if (row == null) {
            return new WorkOrderProjectPersonnelResolution.Item(
                    position.name(), null, null, null, null, null, false, "MISSING");
        }
        String displayName = names.get(row.principalId());
        return new WorkOrderProjectPersonnelResolution.Item(
                position.name(), row.assignmentId(), row.principalId(), displayName,
                row.matchedRegionCode(), row.matchedRegionName(), row.depth() > 0,
                displayName == null ? "DATA_INCOMPLETE" : "ASSIGNED");
    }
}
