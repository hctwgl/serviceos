package com.serviceos.readmodel.web;

import com.serviceos.identity.api.CurrentPrincipalProvider;
import com.serviceos.readmodel.api.AdminUserDirectoryPage;
import com.serviceos.readmodel.api.AdminUserDirectoryQueryService;
import com.serviceos.shared.CorrelationIds;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin 用户目录投影。主体授权走 identity.read；组织/角色摘要 soft-gate。
 */
@RestController
@RequestMapping("/api/v1/admin")
final class AdminUserDirectoryController {
    private final AdminUserDirectoryQueryService queries;
    private final CurrentPrincipalProvider principals;

    AdminUserDirectoryController(
            AdminUserDirectoryQueryService queries, CurrentPrincipalProvider principals
    ) {
        this.queries = queries;
        this.principals = principals;
    }

    @GetMapping("/user-directory")
    ResponseEntity<AdminUserDirectoryPage> list(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "50") int limit,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        AdminUserDirectoryPage page = queries.list(
                principals.current(), correlationId, query, status, cursor, limit);
        return ResponseEntity.ok()
                .header(CorrelationIds.HEADER_NAME, correlationId)
                .body(page);
    }
}
