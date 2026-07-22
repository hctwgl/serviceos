package com.serviceos.project.application;

import com.serviceos.audit.api.AuditAppender;
import com.serviceos.audit.api.AuditEntry;
import com.serviceos.authorization.api.AuthorizationDecision;
import com.serviceos.authorization.api.AuthorizationRequest;
import com.serviceos.authorization.api.AuthorizationService;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.identity.api.PrincipalPersonaQuery;
import com.serviceos.identity.api.SecurityPrincipalPage;
import com.serviceos.identity.api.SecurityPrincipalQueryService;
import com.serviceos.identity.api.SecurityPrincipalView;
import com.serviceos.project.api.AddProjectTeamMemberCommand;
import com.serviceos.project.api.AssignProjectRegionPersonnelCommand;
import com.serviceos.project.api.ProjectPositionCode;
import com.serviceos.project.api.ProjectRegionPersonnelAssignmentView;
import com.serviceos.project.api.ProjectRegionPersonnelMatchResult;
import com.serviceos.project.api.ProjectRegionPersonnelMatchView;
import com.serviceos.project.api.ProjectTeamCandidateView;
import com.serviceos.project.api.ProjectTeamMemberView;
import com.serviceos.project.api.ProjectTeamRegionOption;
import com.serviceos.project.api.ProjectTeamService;
import com.serviceos.project.api.ProjectTeamWorkspaceView;
import com.serviceos.project.domain.Project;
import com.serviceos.reliability.api.IdempotencyDecision;
import com.serviceos.reliability.api.IdempotencyService;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.CommandContext;
import com.serviceos.shared.CommandMetadata;
import com.serviceos.shared.ProblemCode;
import com.serviceos.shared.Sha256;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 项目团队与区域岗位分工用例。
 *
 * <p>成员和分工属于项目日常主数据，不推进项目聚合版本，也不触发履约配置发布。写命令仍以
 * 幂等抢占、领域写入、审计、幂等完成的单事务顺序提交；替换负责人时先锁定并结束旧分工，
 * 再写入新分工，避免并发下出现两个当前负责人。</p>
 */
@Service
final class DefaultProjectTeamService implements ProjectTeamService {
    private static final String READ = "project.read";
    private static final String MANAGE = "project.team.manage";
    private static final String ADD_MEMBER_OPERATION = "project.team.addMember";
    private static final String ASSIGN_OPERATION = "project.team.assignRegionPersonnel";
    private static final ObjectMapper CANONICAL_JSON = JsonMapper.builder()
            .findAndAddModules()
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .build();

    private final ProjectRepository projects;
    private final ProjectTeamRepository teams;
    private final AuthorizationService authorization;
    private final PrincipalPersonaQuery personas;
    private final SecurityPrincipalQueryService principalDirectory;
    private final IdempotencyService idempotency;
    private final AuditAppender audit;
    private final Clock clock;

    DefaultProjectTeamService(
            ProjectRepository projects,
            ProjectTeamRepository teams,
            AuthorizationService authorization,
            PrincipalPersonaQuery personas,
            SecurityPrincipalQueryService principalDirectory,
            IdempotencyService idempotency,
            AuditAppender audit,
            Clock clock
    ) {
        this.projects = projects;
        this.teams = teams;
        this.authorization = authorization;
        this.personas = personas;
        this.principalDirectory = principalDirectory;
        this.idempotency = idempotency;
        this.audit = audit;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectTeamWorkspaceView workspace(
            CurrentPrincipal principal, String correlationId, UUID projectId
    ) {
        Project project = requireProject(principal, correlationId, projectId, READ);
        List<ProjectTeamRepository.MemberRow> memberRows = teams.listMembers(principal.tenantId(), projectId);
        List<ProjectTeamRepository.AssignmentRow> assignmentRows = teams.listAssignments(
                principal.tenantId(), projectId);

        Set<UUID> principalIds = memberRows.stream()
                .map(ProjectTeamRepository.MemberRow::principalId)
                .collect(Collectors.toSet());
        principalIds.addAll(assignmentRows.stream()
                .map(ProjectTeamRepository.AssignmentRow::principalId)
                .toList());
        Map<UUID, String> displayNames = personas.displayNames(principal.tenantId(), principalIds);

        AuthorizationDecision identityRead = authorization.authorize(
                principal,
                AuthorizationRequest.tenantCapability(
                        "identity.read", principal.tenantId(), "SecurityPrincipal", "directory"),
                correlationId);
        List<SecurityPrincipalView> directory = identityRead.effect() == AuthorizationDecision.Effect.ALLOW
                ? activeDirectory(principal, correlationId)
                : List.of();
        Map<UUID, SecurityPrincipalView> directoryById = directory.stream()
                .collect(Collectors.toMap(SecurityPrincipalView::id, Function.identity()));

        List<ProjectTeamMemberView> members = memberRows.stream()
                .map(row -> memberView(row, displayNames, directoryById))
                .toList();
        List<ProjectRegionPersonnelAssignmentView> assignments = assignmentRows.stream()
                .map(row -> assignmentView(row, displayNames))
                .toList();
        Set<UUID> memberIds = memberRows.stream()
                .map(ProjectTeamRepository.MemberRow::principalId)
                .collect(Collectors.toSet());
        List<ProjectTeamCandidateView> candidates = directory.stream()
                .map(item -> new ProjectTeamCandidateView(
                        item.id(), item.displayName(), item.employeeNumber(), memberIds.contains(item.id())))
                .toList();
        List<ProjectTeamRegionOption> regions = teams.listEligibleRegions(principal.tenantId(), projectId).stream()
                .map(item -> new ProjectTeamRegionOption(
                        item.code(), item.name(), item.level(), item.parentCode()))
                .toList();
        AuthorizationDecision manage = authorization.authorize(
                principal,
                AuthorizationRequest.projectCapability(
                        MANAGE, principal.tenantId(), "ProjectTeam", projectId.toString(), projectId.toString()),
                correlationId);
        List<String> allowedActions = manage.effect() == AuthorizationDecision.Effect.ALLOW
                ? List.of("ADD_MEMBER", "ASSIGN_REGION_PERSONNEL")
                : List.of();

        return new ProjectTeamWorkspaceView(
                project.id(), project.code(), project.name(), project.status().name(),
                members, assignments, candidates, regions, allowedActions, clock.instant());
    }

    @Override
    @Transactional
    public ProjectTeamMemberView addMember(
            CurrentPrincipal principal, CommandMetadata metadata, AddProjectTeamMemberCommand command
    ) {
        CommandContext context = context(principal, metadata);
        AuthorizationDecision decision = requireManage(principal, context, command.projectId(), "ProjectMember");
        requireProjectExists(principal.tenantId(), command.projectId());
        if (!personas.isActive(principal.tenantId(), command.principalId())) {
            throw new BusinessProblem(ProblemCode.VERSION_CONFLICT, "所选人员已停用，不能加入项目");
        }
        String requestDigest = Sha256.digest(canonicalJson(command));
        IdempotencyDecision idempotencyDecision = idempotency.begin(
                context, ADD_MEMBER_OPERATION, requestDigest);
        if (idempotencyDecision.kind() == IdempotencyDecision.Kind.REPLAY) {
            UUID memberId = UUID.fromString(idempotencyDecision.resourceId().orElseThrow());
            return teams.findMemberById(principal.tenantId(), command.projectId(), memberId)
                    .map(row -> memberView(
                            row,
                            personas.displayNames(principal.tenantId(), List.of(row.principalId())),
                            Map.of()))
                    .orElseThrow(() -> new IllegalStateException("幂等结果引用的项目成员不存在"));
        }
        if (teams.findActiveMember(principal.tenantId(), command.projectId(), command.principalId()).isPresent()) {
            throw new BusinessProblem(ProblemCode.VERSION_CONFLICT, "该人员已经是当前项目成员");
        }

        Instant now = clock.instant();
        ProjectTeamRepository.MemberRow row = new ProjectTeamRepository.MemberRow(
                UUID.randomUUID(), command.principalId(), "ACTIVE", now, 1);
        try {
            teams.insertMember(principal.tenantId(), command.projectId(), row, context.actorId(), now);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessProblem(ProblemCode.VERSION_CONFLICT, "项目成员关系已被其他操作修改");
        }
        audit.append(new AuditEntry(
                UUID.randomUUID(), principal.tenantId(), context.actorId(), "PROJECT_TEAM_MEMBER_ADDED",
                ADD_MEMBER_OPERATION, "ProjectMember", row.memberId().toString(), "ALLOW",
                decision.matchedGrantIds(), decision.policyVersion(), "SUCCEEDED", null,
                requestDigest, context.correlationId(), now));
        ProjectTeamMemberView result = memberView(
                row,
                personas.displayNames(principal.tenantId(), List.of(row.principalId())),
                Map.of());
        idempotency.complete(
                context, ADD_MEMBER_OPERATION, row.memberId().toString(), Sha256.digest(canonicalJson(result)));
        return result;
    }

    @Override
    @Transactional
    public ProjectRegionPersonnelAssignmentView assign(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            AssignProjectRegionPersonnelCommand command
    ) {
        CommandContext context = context(principal, metadata);
        AuthorizationDecision decision = requireManage(principal, context, command.projectId(), "ProjectRegionTeam");
        requireProjectExists(principal.tenantId(), command.projectId());
        if (!teams.isEligibleRegion(principal.tenantId(), command.projectId(), command.regionCode())) {
            throw new BusinessProblem(ProblemCode.VERSION_CONFLICT, "所选行政区不在当前项目服务范围内");
        }
        ProjectTeamRepository.MemberRow member = teams.findActiveMember(
                        principal.tenantId(), command.projectId(), command.principalId())
                .orElseThrow(() -> new BusinessProblem(
                        ProblemCode.VERSION_CONFLICT, "所选人员不是当前项目有效成员"));
        if (!personas.isActive(principal.tenantId(), command.principalId())) {
            throw new BusinessProblem(ProblemCode.VERSION_CONFLICT, "所选人员已停用，不能承担项目岗位");
        }
        String requestDigest = Sha256.digest(canonicalJson(command));
        IdempotencyDecision idempotencyDecision = idempotency.begin(context, ASSIGN_OPERATION, requestDigest);
        if (idempotencyDecision.kind() == IdempotencyDecision.Kind.REPLAY) {
            UUID assignmentId = UUID.fromString(idempotencyDecision.resourceId().orElseThrow());
            return teams.findAssignmentById(principal.tenantId(), command.projectId(), assignmentId)
                    .map(row -> assignmentView(
                            row,
                            personas.displayNames(principal.tenantId(), List.of(row.principalId()))))
                    .orElseThrow(() -> new IllegalStateException("幂等结果引用的区域岗位分工不存在"));
        }

        var current = teams.findActiveAssignmentForUpdate(
                principal.tenantId(), command.projectId(), command.regionCode(), command.position());
        if (current.isPresent()) {
            if (command.expectedCurrentAssignmentId() == null
                    || !current.get().assignmentId().equals(command.expectedCurrentAssignmentId())) {
                throw new BusinessProblem(ProblemCode.VERSION_CONFLICT, "区域岗位负责人已变化，请刷新后重试");
            }
        } else if (command.expectedCurrentAssignmentId() != null) {
            throw new BusinessProblem(ProblemCode.VERSION_CONFLICT, "原区域岗位分工已失效，请刷新后重试");
        }

        Instant now = clock.instant();
        current.ifPresent(item -> teams.endAssignment(
                principal.tenantId(), command.projectId(), item.assignmentId(), context.actorId(), now));
        ProjectTeamRepository.RegionRow region = teams.listEligibleRegions(
                        principal.tenantId(), command.projectId()).stream()
                .filter(item -> item.code().equals(command.regionCode()))
                .findFirst()
                .orElseThrow(() -> new BusinessProblem(ProblemCode.VERSION_CONFLICT, "行政区目录已变化，请刷新后重试"));
        ProjectTeamRepository.AssignmentRow row = new ProjectTeamRepository.AssignmentRow(
                UUID.randomUUID(), region.code(), region.name(), region.level(), command.position(),
                member.memberId(), command.principalId(), command.allowInheritance(), now, 1, command.reason());
        try {
            teams.insertAssignment(principal.tenantId(), command.projectId(), row, context.actorId(), now);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessProblem(ProblemCode.VERSION_CONFLICT, "区域岗位负责人已被其他操作修改");
        }
        audit.append(new AuditEntry(
                UUID.randomUUID(), principal.tenantId(), context.actorId(),
                current.isPresent() ? "PROJECT_REGION_PERSONNEL_REPLACED" : "PROJECT_REGION_PERSONNEL_ASSIGNED",
                ASSIGN_OPERATION, "ProjectRegionPersonnel", row.assignmentId().toString(), "ALLOW",
                decision.matchedGrantIds(), decision.policyVersion(), "SUCCEEDED", null,
                requestDigest, context.correlationId(), now));
        ProjectRegionPersonnelAssignmentView result = assignmentView(
                row, personas.displayNames(principal.tenantId(), List.of(row.principalId())));
        idempotency.complete(
                context, ASSIGN_OPERATION, row.assignmentId().toString(), Sha256.digest(canonicalJson(result)));
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectRegionPersonnelMatchResult match(
            CurrentPrincipal principal, String correlationId, UUID projectId, String regionCode
    ) {
        requireProject(principal, correlationId, projectId, READ);
        String normalized = requireRegionCode(regionCode);
        if (!teams.isEligibleRegion(principal.tenantId(), projectId, normalized)) {
            throw new BusinessProblem(ProblemCode.VERSION_CONFLICT, "行政区不在当前项目服务范围内");
        }
        List<ProjectTeamRepository.MatchRow> rows = teams.match(principal.tenantId(), projectId, normalized);
        Map<UUID, String> names = personas.displayNames(
                principal.tenantId(), rows.stream().map(ProjectTeamRepository.MatchRow::principalId).toList());
        List<ProjectRegionPersonnelMatchView> matches = rows.stream()
                .map(row -> new ProjectRegionPersonnelMatchView(
                        row.position(), row.position().label(), row.assignmentId(), row.principalId(),
                        names.get(row.principalId()), row.matchedRegionCode(), row.matchedRegionName(),
                        row.depth() > 0, names.containsKey(row.principalId())))
                .toList();
        EnumSet<ProjectPositionCode> missing = EnumSet.allOf(ProjectPositionCode.class);
        matches.forEach(item -> missing.remove(item.position()));
        String requestedName = teams.listEligibleRegions(principal.tenantId(), projectId).stream()
                .filter(item -> item.code().equals(normalized))
                .map(ProjectTeamRepository.RegionRow::name)
                .findFirst()
                .orElseThrow(() -> new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND, "行政区不存在"));
        return new ProjectRegionPersonnelMatchResult(
                projectId, normalized, requestedName, matches, List.copyOf(missing), clock.instant());
    }

    private Project requireProject(
            CurrentPrincipal principal, String correlationId, UUID projectId, String capability
    ) {
        Project project = requireProjectExists(principal.tenantId(), projectId);
        authorization.require(
                principal,
                AuthorizationRequest.projectCapability(
                        capability, principal.tenantId(), "Project", projectId.toString(), projectId.toString()),
                correlationId);
        return project;
    }

    private Project requireProjectExists(String tenantId, UUID projectId) {
        return projects.findById(tenantId, projectId)
                .orElseThrow(() -> new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND, "项目不存在"));
    }

    private AuthorizationDecision requireManage(
            CurrentPrincipal principal, CommandContext context, UUID projectId, String resourceType
    ) {
        return authorization.require(
                principal,
                AuthorizationRequest.projectCapability(
                        MANAGE, principal.tenantId(), resourceType, projectId.toString(), projectId.toString()),
                context.correlationId());
    }

    private List<SecurityPrincipalView> activeDirectory(CurrentPrincipal principal, String correlationId) {
        SecurityPrincipalPage page = principalDirectory.list(
                principal, correlationId, null, "ACTIVE", null, 100);
        return page.items().stream()
                .filter(item -> "USER".equals(item.type()) && "ACTIVE".equals(item.status()))
                .toList();
    }

    private static ProjectTeamMemberView memberView(
            ProjectTeamRepository.MemberRow row,
            Map<UUID, String> displayNames,
            Map<UUID, SecurityPrincipalView> directory
    ) {
        SecurityPrincipalView principal = directory.get(row.principalId());
        String name = displayNames.get(row.principalId());
        return new ProjectTeamMemberView(
                row.memberId(), row.principalId(), name,
                principal == null ? null : principal.employeeNumber(), row.status(), row.validFrom(), row.version(),
                name != null && !name.isBlank());
    }

    private static ProjectRegionPersonnelAssignmentView assignmentView(
            ProjectTeamRepository.AssignmentRow row, Map<UUID, String> displayNames
    ) {
        String name = displayNames.get(row.principalId());
        return new ProjectRegionPersonnelAssignmentView(
                row.assignmentId(), row.regionCode(), row.regionName(), row.regionLevel(),
                row.position(), row.position().label(), row.memberId(), row.principalId(), name,
                row.allowInheritance(), row.validFrom(), row.version(), row.changeReason(),
                name != null && !name.isBlank());
    }

    private static CommandContext context(CurrentPrincipal principal, CommandMetadata metadata) {
        return new CommandContext(
                principal.tenantId(), principal.principalId(),
                metadata.correlationId(), metadata.idempotencyKey());
    }

    private static String requireRegionCode(String regionCode) {
        if (regionCode == null || regionCode.isBlank() || !regionCode.equals(regionCode.trim())
                || regionCode.length() > 32) {
            throw new IllegalArgumentException("行政区编码无效");
        }
        return regionCode;
    }

    private static String canonicalJson(Object value) {
        try {
            return CANONICAL_JSON.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("无法生成项目团队命令摘要", exception);
        }
    }
}
