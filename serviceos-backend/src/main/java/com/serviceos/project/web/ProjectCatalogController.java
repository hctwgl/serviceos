package com.serviceos.project.web;

import com.serviceos.identity.api.CurrentPrincipalProvider;
import com.serviceos.project.api.ProjectClientBrandItem;
import com.serviceos.project.api.ProjectClientBrandPage;
import com.serviceos.project.api.ProjectClientDirectoryItem;
import com.serviceos.project.api.ProjectClientDirectoryPage;
import com.serviceos.project.api.ProjectCommandService;
import com.serviceos.project.api.ProjectQueryService;
import com.serviceos.project.api.RegionCatalogPage;
import com.serviceos.shared.CommandMetadata;
import com.serviceos.shared.CorrelationIds;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * 车企主数据、品牌与行政区名称目录 HTTP 适配器。
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
            @RequestParam(required = false) String status,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        return ResponseEntity.ok()
                .header(CorrelationIds.HEADER_NAME, correlationId)
                .body(queries.listClientDirectory(principals.current(), correlationId, status));
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

    @PostMapping("/project-clients/{clientCode}/status")
    ResponseEntity<ProjectClientDirectoryItem> setClientStatus(
            @PathVariable String clientCode,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId,
            @Valid @RequestBody SetCatalogStatusRequest request
    ) {
        return ResponseEntity.ok()
                .header(CorrelationIds.HEADER_NAME, correlationId)
                .body(commands.setClientStatus(
                        principals.current(),
                        new CommandMetadata(correlationId, idempotencyKey),
                        clientCode,
                        request.status()));
    }

    @GetMapping("/project-clients/{clientCode}/brands")
    ResponseEntity<ProjectClientBrandPage> listBrands(
            @PathVariable String clientCode,
            @RequestParam(required = false) String status,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        return ResponseEntity.ok()
                .header(CorrelationIds.HEADER_NAME, correlationId)
                .body(queries.listClientBrands(
                        principals.current(), correlationId, clientCode, status));
    }

    @PostMapping("/project-clients/{clientCode}/brands")
    ResponseEntity<ProjectClientBrandItem> registerBrand(
            @PathVariable String clientCode,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId,
            @Valid @RequestBody RegisterBrandRequest request
    ) {
        ProjectClientBrandItem item = commands.registerBrand(
                principals.current(),
                new CommandMetadata(correlationId, idempotencyKey),
                clientCode,
                request.brandCode(),
                request.displayName(),
                request.sortOrder());
        return ResponseEntity.created(URI.create(
                        "/api/v1/project-clients/" + item.clientCode() + "/brands/" + item.brandCode()))
                .header(CorrelationIds.HEADER_NAME, correlationId)
                .body(item);
    }

    @PostMapping("/project-clients/{clientCode}/brands/{brandCode}/status")
    ResponseEntity<ProjectClientBrandItem> setBrandStatus(
            @PathVariable String clientCode,
            @PathVariable String brandCode,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId,
            @Valid @RequestBody SetCatalogStatusRequest request
    ) {
        return ResponseEntity.ok()
                .header(CorrelationIds.HEADER_NAME, correlationId)
                .body(commands.setBrandStatus(
                        principals.current(),
                        new CommandMetadata(correlationId, idempotencyKey),
                        clientCode,
                        brandCode,
                        request.status()));
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

    record RegisterBrandRequest(
            @NotBlank @Size(max = 128) String brandCode,
            @NotBlank @Size(max = 200) String displayName,
            @Min(0) @Max(999999) Integer sortOrder
    ) {
    }

    record SetCatalogStatusRequest(
            @NotNull @Pattern(regexp = "ACTIVE|DISABLED") String status
    ) {
    }
}
