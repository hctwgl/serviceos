package com.serviceos.project.web;

import com.serviceos.project.api.CreateProjectCommand;
import com.serviceos.project.api.ProjectCommandService;
import com.serviceos.project.api.ProjectView;
import com.serviceos.shared.CommandContext;
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
 * 项目写 API。真实 OIDC 接入前，tenant/actor 由受信网关注入的标准头模拟；
 * 生产实现必须改由 CurrentPrincipal 解析，不能信任公网客户端自报主体。
 */
@RestController
@RequestMapping("/api/v1/projects")
final class ProjectController {
    private final ProjectCommandService commands;

    ProjectController(ProjectCommandService commands) {
        this.commands = commands;
    }

    @PostMapping
    ResponseEntity<ProjectView> create(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("X-Tenant-Id") String tenantId,
            @RequestHeader("X-Actor-Id") String actorId,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @Valid @RequestBody CreateProjectRequest request
    ) {
        String effectiveCorrelationId = correlationId == null || correlationId.isBlank()
                ? UUID.randomUUID().toString()
                : correlationId;
        CommandContext context = new CommandContext(
                tenantId, actorId, effectiveCorrelationId, idempotencyKey);
        ProjectView result = commands.create(context, new CreateProjectCommand(
                request.code(), request.clientId(), request.name(), request.startsOn(), request.endsOn()));
        return ResponseEntity
                .created(URI.create("/api/v1/projects/" + result.id()))
                .header("X-Correlation-Id", effectiveCorrelationId)
                .body(result);
    }
}
