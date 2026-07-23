package com.serviceos.readmodel.web;

import com.serviceos.identity.api.CurrentPrincipalProvider;
import com.serviceos.readmodel.api.AdminProjectWorkspaceQueryService;
import com.serviceos.readmodel.api.AdminProjectWorkspaceView;
import com.serviceos.shared.CorrelationIds;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Admin 项目详情与履约配置概览入口。 */
@RestController
@RequestMapping("/api/v1/admin/projects")
final class AdminProjectWorkspaceController {
    private final AdminProjectWorkspaceQueryService queries;
    private final CurrentPrincipalProvider principals;

    AdminProjectWorkspaceController(
            AdminProjectWorkspaceQueryService queries,
            CurrentPrincipalProvider principals
    ) {
        this.queries = queries;
        this.principals = principals;
    }

    @GetMapping("/{projectId}/workspace")
    ResponseEntity<AdminProjectWorkspaceView> get(
            @PathVariable UUID projectId,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        return ResponseEntity.ok()
                .header(CorrelationIds.HEADER_NAME, correlationId)
                .body(queries.get(principals.current(), correlationId, projectId));
    }
}
