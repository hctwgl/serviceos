package com.serviceos.project.web;

import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.identity.api.CurrentPrincipalProvider;
import com.serviceos.project.api.CreateProjectCommand;
import com.serviceos.project.api.ProjectCommandService;
import com.serviceos.project.api.ProjectDetail;
import com.serviceos.project.api.ProjectPage;
import com.serviceos.project.api.ProjectQuery;
import com.serviceos.project.api.ProjectQueryService;
import com.serviceos.project.api.ProjectReferenceOptions;
import com.serviceos.project.api.ProjectScopeRelationRevisionPage;
import com.serviceos.project.api.ProjectScopeRelationRevisionView;
import com.serviceos.project.api.ProjectView;
import com.serviceos.project.api.ReviseProjectScopeRelationsCommand;
import com.serviceos.shared.CommandMetadata;
import com.serviceos.shared.CorrelationIds;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 项目写 API。tenant/actor 只从已验证 JWT 映射的 CurrentPrincipal 获取，
 * 不接受公网客户端通过请求正文或自定义头覆盖主体身份。
 */
@RestController
@RequestMapping("/api/v1/projects")
final class ProjectController {
    private final ProjectCommandService commands;
    private final ProjectQueryService queries;
    private final CurrentPrincipalProvider principals;

    ProjectController(
            ProjectCommandService commands,
            ProjectQueryService queries,
            CurrentPrincipalProvider principals
    ) {
        this.commands = commands;
        this.queries = queries;
        this.principals = principals;
    }

    @GetMapping
    ResponseEntity<ProjectPage> list(
            @RequestParam(required = false) String clientId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate activeOn,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "50") int limit,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        ProjectPage page = queries.list(principals.current(), correlationId,
                new ProjectQuery(clientId, status, activeOn, cursor, limit));
        return ResponseEntity.ok()
                .header(CorrelationIds.HEADER_NAME, correlationId)
                .body(page);
    }

    /**
     * 必须注册在 /{projectId} 之前，避免把 reference-options 解析为 UUID。
     */
    @GetMapping("/reference-options")
    ResponseEntity<ProjectReferenceOptions> referenceOptions(
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        ProjectReferenceOptions options = queries.referenceOptions(principals.current(), correlationId);
        return ResponseEntity.ok()
                .header(CorrelationIds.HEADER_NAME, correlationId)
                .body(options);
    }

    @GetMapping("/{projectId}")
    ResponseEntity<ProjectDetail> get(
            @PathVariable UUID projectId,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        ProjectDetail detail = queries.get(principals.current(), correlationId, projectId);
        return ResponseEntity.ok()
                .eTag(Long.toString(detail.project().version()))
                .header(CorrelationIds.HEADER_NAME, correlationId)
                .body(detail);
    }

    @GetMapping("/{projectId}/scope-revisions")
    ResponseEntity<ProjectScopeRelationRevisionPage> listScopeRevisions(
            @PathVariable UUID projectId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "50") int limit,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        ProjectScopeRelationRevisionPage page = queries.listScopeRevisions(
                principals.current(), correlationId, projectId, cursor, limit);
        return ResponseEntity.ok()
                .header(CorrelationIds.HEADER_NAME, correlationId)
                .body(page);
    }

    @PostMapping
    ResponseEntity<ProjectView> create(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId,
            @Valid @RequestBody CreateProjectRequest request
    ) {
        CurrentPrincipal principal = principals.current();
        CommandMetadata metadata = new CommandMetadata(correlationId, idempotencyKey);
        ProjectView result = commands.create(principal, metadata, new CreateProjectCommand(
                request.code(), request.clientId(), request.name(), request.startsOn(), request.endsOn(),
                request.regionCodes() == null ? List.of() : request.regionCodes(),
                request.networkIds() == null ? List.of() : request.networkIds()));
        return ResponseEntity
                .created(URI.create("/api/v1/projects/" + result.id()))
                .header(CorrelationIds.HEADER_NAME, correlationId)
                .body(result);
    }

    @PostMapping("/{projectId}:revise-scope-relations")
    ResponseEntity<ProjectScopeRelationRevisionView> reviseScopeRelations(
            @PathVariable UUID projectId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("If-Match") String ifMatch,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId,
            @Valid @RequestBody ReviseProjectScopeRelationsRequest request
    ) {
        ProjectScopeRelationRevisionView result = commands.reviseScopeRelations(
                principals.current(), new CommandMetadata(correlationId, idempotencyKey),
                new ReviseProjectScopeRelationsCommand(
                        projectId, version(ifMatch), request.regionCodes(), request.networkIds(), request.reason()));
        return ResponseEntity.ok()
                .eTag(Long.toString(result.aggregateVersion()))
                .header(CorrelationIds.HEADER_NAME, correlationId)
                .body(result);
    }

    private static long version(String ifMatch) {
        if (ifMatch == null || !ifMatch.matches("\\\"[1-9][0-9]*\\\"")) {
            throw new IllegalArgumentException("If-Match must contain one quoted positive aggregate version");
        }
        try {
            return Long.parseLong(ifMatch.substring(1, ifMatch.length() - 1));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("If-Match aggregate version is too large", exception);
        }
    }
}
