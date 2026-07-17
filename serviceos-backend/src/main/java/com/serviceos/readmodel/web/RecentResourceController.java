package com.serviceos.readmodel.web;

import com.serviceos.identity.api.CurrentPrincipalProvider;
import com.serviceos.readmodel.api.RecentResourceCommandService;
import com.serviceos.readmodel.api.RecentResourceItem;
import com.serviceos.readmodel.api.RecentResourcePage;
import com.serviceos.readmodel.api.RecentResourceQueryService;
import com.serviceos.readmodel.api.RecentResourceTouch;
import com.serviceos.readmodel.api.RecentResourceType;
import com.serviceos.shared.CorrelationIds;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin 个人最近访问 HTTP 适配器。tenant/principal 只来自受信 CurrentPrincipal；
 * 无跨主体读取路径；portal 仅 ADMIN。
 */
@RestController
@RequestMapping("/api/v1")
final class RecentResourceController {
    private final RecentResourceQueryService queries;
    private final RecentResourceCommandService commands;
    private final CurrentPrincipalProvider principals;

    RecentResourceController(
            RecentResourceQueryService queries,
            RecentResourceCommandService commands,
            CurrentPrincipalProvider principals
    ) {
        this.queries = queries;
        this.commands = commands;
        this.principals = principals;
    }

    @GetMapping("/me/recent-resources")
    ResponseEntity<RecentResourcePage> list(
            @RequestParam(value = "portal", defaultValue = "ADMIN") String portal,
            @RequestParam(value = "limit", required = false) Integer limit,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        RecentResourcePage page = queries.list(principals.current(), correlationId, portal, limit);
        return ResponseEntity.ok()
                .header(CorrelationIds.HEADER_NAME, correlationId)
                .body(page);
    }

    @PutMapping("/me/recent-resources")
    ResponseEntity<RecentResourceItem> touch(
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId,
            @Valid @RequestBody TouchRecentResourceRequest request
    ) {
        RecentResourceItem item = commands.touch(
                principals.current(),
                correlationId,
                request.portal() != null ? request.portal() : "ADMIN",
                new RecentResourceTouch(
                        request.resourceType(),
                        request.resourceId(),
                        request.pageId(),
                        request.displayRef()
                ));
        return ResponseEntity.ok()
                .header(CorrelationIds.HEADER_NAME, correlationId)
                .body(item);
    }

    record TouchRecentResourceRequest(
            @Size(max = 32) String portal,
            @NotNull RecentResourceType resourceType,
            @NotBlank @Size(max = 128) String resourceId,
            @Size(max = 64) String pageId,
            @Size(max = 120) String displayRef
    ) {
    }
}
