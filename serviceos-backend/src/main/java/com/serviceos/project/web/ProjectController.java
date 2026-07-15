package com.serviceos.project.web;

import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.identity.api.CurrentPrincipalProvider;
import com.serviceos.project.api.CreateProjectCommand;
import com.serviceos.project.api.ProjectCommandService;
import com.serviceos.project.api.ProjectScopeRelationRevisionView;
import com.serviceos.project.api.ProjectView;
import com.serviceos.project.api.ReviseProjectScopeRelationsCommand;
import com.serviceos.shared.CommandMetadata;
import com.serviceos.shared.CorrelationIds;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
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
    private final CurrentPrincipalProvider principals;

    ProjectController(ProjectCommandService commands, CurrentPrincipalProvider principals) {
        this.commands = commands;
        this.principals = principals;
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
