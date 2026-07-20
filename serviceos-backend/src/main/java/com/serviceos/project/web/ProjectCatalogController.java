package com.serviceos.project.web;

import com.serviceos.identity.api.CurrentPrincipalProvider;
import com.serviceos.project.api.ProjectClientDirectoryItem;
import com.serviceos.project.api.ProjectClientDirectoryPage;
import com.serviceos.project.api.ProjectCommandService;
import com.serviceos.project.api.ProjectQueryService;
import com.serviceos.project.api.RegionCatalogPage;
import com.serviceos.shared.CommandMetadata;
import com.serviceos.shared.CorrelationIds;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * 车企主数据与行政区名称目录 HTTP 适配器。
 */
@RestController
@RequestMapping("/api/v1")
final class ProjectCatalogController {
    private final ProjectQueryService queries;
    private final ProjectCommandService commands;
    private final CurrentPrincipalProvider principals;

    ProjectCatalogController(
            ProjectQueryService queries,
            ProjectCommandService commands,
            CurrentPrincipalProvider principals
    ) {
        this.queries = queries;
        this.commands = commands;
        this.principals = principals;
    }

    @GetMapping("/project-clients")
    ResponseEntity<ProjectClientDirectoryPage> listClients(
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        return ResponseEntity.ok()
                .header(CorrelationIds.HEADER_NAME, correlationId)
                .body(queries.listClientDirectory(principals.current(), correlationId));
    }

    @PostMapping("/project-clients")
    ResponseEntity<ProjectClientDirectoryItem> registerClient(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId,
            @Valid @RequestBody RegisterClientRequest request
    ) {
        ProjectClientDirectoryItem item = commands.registerClient(
                principals.current(),
                new CommandMetadata(correlationId, idempotencyKey),
                request.clientCode(),
                request.displayName());
        return ResponseEntity.created(URI.create("/api/v1/project-clients/" + item.clientCode()))
                .header(CorrelationIds.HEADER_NAME, correlationId)
                .body(item);
    }

    @GetMapping("/region-catalog")
    ResponseEntity<RegionCatalogPage> listRegions(
            @RequestParam(required = false) String parentCode,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) Integer limit,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        return ResponseEntity.ok()
                .header(CorrelationIds.HEADER_NAME, correlationId)
                .body(queries.listRegionCatalog(
                        principals.current(), correlationId, parentCode, query, level, limit));
    }

    record RegisterClientRequest(
            @NotBlank @Size(max = 128) String clientCode,
            @NotBlank @Size(max = 200) String displayName
    ) {
    }
}
