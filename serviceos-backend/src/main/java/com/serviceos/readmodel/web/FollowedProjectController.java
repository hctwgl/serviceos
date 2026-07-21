package com.serviceos.readmodel.web;

import com.serviceos.identity.api.CurrentPrincipalProvider;
import com.serviceos.readmodel.api.FollowedProjectCommandService;
import com.serviceos.readmodel.api.FollowedProjectItem;
import com.serviceos.readmodel.api.FollowedProjectPage;
import com.serviceos.readmodel.api.FollowedProjectQueryService;
import com.serviceos.shared.CorrelationIds;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Admin 个人关注项目 HTTP 适配器。tenant/principal 只来自受信 CurrentPrincipal。
 */
@RestController
@RequestMapping("/api/v1")
final class FollowedProjectController {
    private final FollowedProjectQueryService queries;
    private final FollowedProjectCommandService commands;
    private final CurrentPrincipalProvider principals;

    FollowedProjectController(
            FollowedProjectQueryService queries,
            FollowedProjectCommandService commands,
            CurrentPrincipalProvider principals
    ) {
        this.queries = queries;
        this.commands = commands;
        this.principals = principals;
    }

    @GetMapping("/me/followed-projects")
    ResponseEntity<FollowedProjectPage> list(
            @RequestParam(value = "portal", defaultValue = "ADMIN") String portal,
            @RequestParam(value = "limit", required = false) Integer limit,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        FollowedProjectPage page = queries.list(principals.current(), correlationId, portal, limit);
        return ResponseEntity.ok()
                .header(CorrelationIds.HEADER_NAME, correlationId)
                .body(page);
    }

    @GetMapping("/me/followed-projects/{projectId}/status")
    ResponseEntity<Map<String, Object>> status(
            @PathVariable UUID projectId,
            @RequestParam(value = "portal", defaultValue = "ADMIN") String portal,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        boolean followed = queries.isFollowed(principals.current(), correlationId, portal, projectId);
        return ResponseEntity.ok()
                .header(CorrelationIds.HEADER_NAME, correlationId)
                .body(Map.of("projectId", projectId.toString(), "followed", followed));
    }

    @PutMapping("/me/followed-projects")
    ResponseEntity<FollowedProjectItem> follow(
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId,
            @Valid @RequestBody FollowProjectRequest request
    ) {
        FollowedProjectItem item = commands.follow(
                principals.current(),
                correlationId,
                request.portal() != null ? request.portal() : "ADMIN",
                request.projectId(),
                request.displayRef());
        return ResponseEntity.ok()
                .header(CorrelationIds.HEADER_NAME, correlationId)
                .body(item);
    }

    @DeleteMapping("/me/followed-projects/{projectId}")
    ResponseEntity<Void> unfollow(
            @PathVariable UUID projectId,
            @RequestParam(value = "portal", defaultValue = "ADMIN") String portal,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        commands.unfollow(principals.current(), correlationId, portal, projectId);
        return ResponseEntity.noContent()
                .header(CorrelationIds.HEADER_NAME, correlationId)
                .build();
    }

    record FollowProjectRequest(
            @Size(max = 32) String portal,
            @NotNull UUID projectId,
            @Size(max = 120) String displayRef
    ) {
    }
}
