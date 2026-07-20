package com.serviceos.project.application;

import com.serviceos.authorization.api.AuthorizationRequest;
import com.serviceos.authorization.api.AuthorizationService;
import com.serviceos.authorization.api.AuthorizedProjectScope;
import com.serviceos.authorization.api.ProjectScopeAuthorizationService;
import com.serviceos.configuration.api.ProjectFulfillmentProfileService;
import com.serviceos.configuration.api.ProjectFulfillmentSchemeCount;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.project.api.ProjectClientDirectoryPage;
import com.serviceos.project.api.ProjectClientOption;
import com.serviceos.project.api.ProjectDetail;
import com.serviceos.project.api.ProjectPage;
import com.serviceos.project.api.ProjectQuery;
import com.serviceos.project.api.ProjectQueryService;
import com.serviceos.project.api.ProjectReferenceOptions;
import com.serviceos.project.api.ProjectRegionOption;
import com.serviceos.project.api.ProjectScopeRelationRevisionPage;
import com.serviceos.project.api.ProjectView;
import com.serviceos.project.api.RegionCatalogPage;
import com.serviceos.project.domain.Project;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import com.serviceos.shared.Sha256;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 项目授权目录查询。集合只解析一次实时授权范围并执行一条范围化 SQL；详情先按 tenant 隔离读取，
 * 再按资源所属 project 实时鉴权，避免跨租户存在性泄露。
 */
@Service
final class DefaultProjectQueryService implements ProjectQueryService {
    private static final String READ = "project.read";
    private static final Set<String> STATUSES = Set.of("DRAFT", "ACTIVE", "SUSPENDED", "CLOSED");

    private final ProjectRepository projects;
    private final ProjectQueryRepository queries;
    private final AuthorizationService authorization;
    private final ProjectScopeAuthorizationService projectScopes;
    private final ProjectFulfillmentProfileService fulfillmentProfiles;
    private final ProjectReferenceOptionsRepository referenceOptions;
    private final ProjectCatalogRepository catalogs;
    private final Clock clock;

    DefaultProjectQueryService(
            ProjectRepository projects,
            ProjectQueryRepository queries,
            AuthorizationService authorization,
            ProjectScopeAuthorizationService projectScopes,
            ProjectFulfillmentProfileService fulfillmentProfiles,
            ProjectReferenceOptionsRepository referenceOptions,
            ProjectCatalogRepository catalogs,
            Clock clock
    ) {
        this.projects = projects;
        this.queries = queries;
        this.authorization = authorization;
        this.projectScopes = projectScopes;
        this.fulfillmentProfiles = fulfillmentProfiles;
        this.referenceOptions = referenceOptions;
        this.catalogs = catalogs;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectPage list(CurrentPrincipal principal, String correlationId, ProjectQuery query) {
        Objects.requireNonNull(query, "query must not be null");
        validateLimit(query.limit());
        String clientId = normalizeClientId(query.clientId());
        String status = normalizeStatus(query.status());
        AuthorizedProjectScope scope = projectScopes.require(principal, READ, "Project", correlationId);
        String filterDigest = filterDigest(clientId, status, query.activeOn());
        Cursor cursor = decodeListCursor(query.cursor(), scope.scopeDigest(), filterDigest);
        List<UUID> projectIds = scope.projectIds().stream()
                .sorted(Comparator.comparing(UUID::toString)).toList();
        List<Project> fetched = queries.findPage(
                principal.tenantId(), scope.tenantWide(), projectIds, clientId, status, query.activeOn(),
                cursor == null ? null : cursor.code(), cursor == null ? null : cursor.id(), query.limit() + 1);
        boolean more = fetched.size() > query.limit();
        List<Project> selected = more ? fetched.subList(0, query.limit()) : fetched;
        Project last = more ? selected.getLast() : null;
        List<ProjectFulfillmentSchemeCount> schemeCounts = fulfillmentProfiles.summarizeSchemeCounts(
                principal,
                correlationId,
                selected.stream().map(Project::id).toList());
        // DENY → 空列表 → 字段 null；ALLOW → 每个项目都有计数（可为 0）。
        Map<UUID, ProjectFulfillmentSchemeCount> byProject = schemeCounts.stream()
                .collect(Collectors.toMap(ProjectFulfillmentSchemeCount::projectId, Function.identity()));
        boolean enrich = !schemeCounts.isEmpty() || selected.isEmpty();
        List<ProjectView> views = selected.stream()
                .map(project -> {
                    if (!enrich) {
                        return project.toView();
                    }
                    ProjectFulfillmentSchemeCount count = byProject.get(project.id());
                    int published = count == null ? 0 : count.publishedSchemeCount();
                    int draft = count == null ? 0 : count.draftSchemeCount();
                    return project.toView(published, draft);
                })
                .toList();
        return new ProjectPage(views,
                last == null ? null : encodeListCursor(
                        scope.scopeDigest(), filterDigest, last.code(), last.id()), clock.instant());
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectDetail get(CurrentPrincipal principal, String correlationId, UUID projectId) {
        Project project = requireProject(principal, correlationId, projectId);
        return new ProjectDetail(project.toView(), clock.instant());
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectReferenceOptions referenceOptions(CurrentPrincipal principal, String correlationId) {
        AuthorizedProjectScope scope = projectScopes.require(principal, READ, "Project", correlationId);
        List<ProjectClientOption> clients = referenceOptions.listClients(
                principal.tenantId(), scope.tenantWide(), scope.projectIds());
        List<ProjectRegionOption> regions = referenceOptions.listRegions(
                principal.tenantId(), scope.tenantWide(), scope.projectIds());
        Map<String, String> clientNames = catalogs.findClientDisplayNames(
                principal.tenantId(),
                clients.stream().map(ProjectClientOption::clientId).toList());
        Map<String, String> regionNames = catalogs.findRegionNames(
                regions.stream().map(ProjectRegionOption::regionCode).toList());
        return new ProjectReferenceOptions(
                clients.stream()
                        .map(item -> new ProjectClientOption(
                                item.clientId(),
                                clientNames.getOrDefault(item.clientId(), item.displayName()),
                                item.projectCount()))
                        .toList(),
                regions.stream()
                        .map(item -> new ProjectRegionOption(
                                item.regionCode(),
                                regionNames.getOrDefault(item.regionCode(), item.regionName()),
                                item.projectCount()))
                        .toList(),
                clock.instant());
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectClientDirectoryPage listClientDirectory(CurrentPrincipal principal, String correlationId) {
        projectScopes.require(principal, READ, "Project", correlationId);
        return new ProjectClientDirectoryPage(
                catalogs.listClients(principal.tenantId(), true), clock.instant());
    }

    @Override
    @Transactional(readOnly = true)
    public RegionCatalogPage listRegionCatalog(
            CurrentPrincipal principal,
            String correlationId,
            String parentCode,
            String query,
            String level,
            Integer limit
    ) {
        projectScopes.require(principal, READ, "Project", correlationId);
        int effective = limit == null ? 100 : limit;
        if (effective < 1 || effective > 200) {
            throw new IllegalArgumentException("limit must be between 1 and 200");
        }
        if (level != null && !level.isBlank()
                && !"PROVINCE".equals(level) && !"CITY".equals(level) && !"DISTRICT".equals(level)) {
            throw new IllegalArgumentException("level is invalid");
        }
        return new RegionCatalogPage(
                catalogs.listRegions(parentCode, query, level, effective), clock.instant());
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectScopeRelationRevisionPage listScopeRevisions(
            CurrentPrincipal principal, String correlationId, UUID projectId, String cursorValue, int limit
    ) {
        validateLimit(limit);
        requireProject(principal, correlationId, projectId);
        Long cursor = decodeRevisionCursor(cursorValue, projectId);
        List<ProjectScopeRevision> fetched = queries.findScopeRevisionPage(
                principal.tenantId(), projectId, cursor, limit + 1);
        boolean more = fetched.size() > limit;
        List<ProjectScopeRevision> selected = more ? fetched.subList(0, limit) : fetched;
        ProjectScopeRevision last = more ? selected.getLast() : null;
        return new ProjectScopeRelationRevisionPage(
                selected.stream().map(ProjectScopeRevision::toView).toList(),
                last == null ? null : encodeRevisionCursor(projectId, last.aggregateVersion()),
                clock.instant());
    }

    private Project requireProject(CurrentPrincipal principal, String correlationId, UUID projectId) {
        Objects.requireNonNull(projectId, "projectId must not be null");
        Project project = projects.findById(principal.tenantId(), projectId)
                .orElseThrow(() -> new BusinessProblem(
                        ProblemCode.RESOURCE_NOT_FOUND, "项目不存在"));
        AuthorizedProjectScope scope = projectScopes.require(principal, READ, "Project", correlationId);
        if (!scope.tenantWide() && !scope.projectIds().contains(projectId)) {
            // 范围解析拒绝时，再执行资源级鉴权以写入包含具体 projectId 的拒绝审计；该调用必须失败关闭。
            authorization.require(principal, AuthorizationRequest.projectCapability(
                    READ, principal.tenantId(), "Project", projectId.toString(), projectId.toString()), correlationId);
            throw new IllegalStateException("项目范围拒绝未能失败关闭");
        }
        return project;
    }

    private static String normalizeClientId(String value) {
        if (value == null) return null;
        if (value.isBlank() || !value.equals(value.trim()) || value.length() > 128) {
            throw new IllegalArgumentException("clientId is invalid");
        }
        return value;
    }

    private static String normalizeStatus(String value) {
        if (value == null) return null;
        if (value.isBlank() || !value.equals(value.trim()) || !STATUSES.contains(value)) {
            throw new IllegalArgumentException("status is invalid");
        }
        return value;
    }

    private static void validateLimit(int limit) {
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("limit must be between 1 and 100");
        }
    }

    private static String filterDigest(String clientId, String status, LocalDate activeOn) {
        return Sha256.digest("clientId=" + nullable(clientId)
                + "|status=" + nullable(status) + "|activeOn=" + nullable(activeOn));
    }

    private static String encodeListCursor(String scopeDigest, String filterDigest, String code, UUID id) {
        return encode(scopeDigest + "|" + filterDigest + "|" + code + "|" + id);
    }

    private static Cursor decodeListCursor(String value, String scopeDigest, String filterDigest) {
        if (value == null) return null;
        if (value.isBlank()) throw new IllegalArgumentException("cursor is invalid for the requested project scope");
        try {
            String[] parts = decode(value).split("\\|", -1);
            if (parts.length != 4 || !scopeDigest.equals(parts[0]) || !filterDigest.equals(parts[1])) {
                throw new IllegalArgumentException();
            }
            return new Cursor(parts[2], UUID.fromString(parts[3]));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("cursor is invalid for the requested project scope", exception);
        }
    }

    private static String encodeRevisionCursor(UUID projectId, long version) {
        return encode(projectId + "|" + version);
    }

    private static Long decodeRevisionCursor(String value, UUID projectId) {
        if (value == null) return null;
        if (value.isBlank()) throw new IllegalArgumentException("cursor is invalid for the project revision history");
        try {
            String[] parts = decode(value).split("\\|", -1);
            if (parts.length != 2 || !projectId.toString().equals(parts[0])) {
                throw new IllegalArgumentException();
            }
            long version = Long.parseLong(parts[1]);
            if (version < 2) throw new IllegalArgumentException();
            return version;
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("cursor is invalid for the project revision history", exception);
        }
    }

    private static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }

    private static String nullable(Object value) {
        return value == null ? "-" : value.toString();
    }

    private record Cursor(String code, UUID id) {
    }
}
