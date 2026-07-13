package com.serviceos.project.web;

import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.identity.api.CurrentPrincipalProvider;
import com.serviceos.project.api.CreateProjectCommand;
import com.serviceos.project.api.ProjectCommandService;
import com.serviceos.project.api.ProjectView;
import com.serviceos.shared.CommandMetadata;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
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
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @Valid @RequestBody CreateProjectRequest request
    ) {
        String effectiveCorrelationId = correlationId == null || correlationId.isBlank()
                ? UUID.randomUUID().toString()
                : correlationId;
        CurrentPrincipal principal = principals.current();
        CommandMetadata metadata = new CommandMetadata(effectiveCorrelationId, idempotencyKey);
        ProjectView result = commands.create(principal, metadata, new CreateProjectCommand(
                request.code(), request.clientId(), request.name(), request.startsOn(), request.endsOn()));
        return ResponseEntity
                .created(URI.create("/api/v1/projects/" + result.id()))
                .header("X-Correlation-Id", effectiveCorrelationId)
                .body(result);
    }
}
