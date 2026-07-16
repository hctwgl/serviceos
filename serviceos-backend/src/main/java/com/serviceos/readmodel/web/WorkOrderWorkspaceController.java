package com.serviceos.readmodel.web;

import com.serviceos.identity.api.CurrentPrincipalProvider;
import com.serviceos.readmodel.api.WorkOrderWorkspace;
import com.serviceos.readmodel.api.WorkOrderWorkspaceQueryService;
import com.serviceos.readmodel.api.WorkOrderWorkspaceSection;
import com.serviceos.shared.CorrelationIds;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/work-orders")
final class WorkOrderWorkspaceController {
    private final WorkOrderWorkspaceQueryService workspaces;
    private final CurrentPrincipalProvider principals;

    WorkOrderWorkspaceController(
            WorkOrderWorkspaceQueryService workspaces,
            CurrentPrincipalProvider principals
    ) {
        this.workspaces = workspaces;
        this.principals = principals;
    }

    @GetMapping("/{workOrderId}/workspace")
    ResponseEntity<WorkOrderWorkspace> get(
            @PathVariable UUID workOrderId,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        WorkOrderWorkspace result = workspaces.get(principals.current(), correlationId, workOrderId);
        return ResponseEntity.ok()
                .eTag(Long.toString(result.sourceVersions().workOrderVersion()))
                .header(CorrelationIds.HEADER_NAME, correlationId)
                .body(result);
    }

    @GetMapping("/{workOrderId}/workspace/sections/{section}")
    ResponseEntity<WorkOrderWorkspaceSection> getSection(
            @PathVariable UUID workOrderId,
            @PathVariable String section,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "50") int limit,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        WorkOrderWorkspaceSection result = workspaces.getSection(
                principals.current(), correlationId, workOrderId, section, cursor, limit);
        return ResponseEntity.ok()
                .eTag(Long.toString(result.sourceVersions().workOrderVersion()))
                .header(CorrelationIds.HEADER_NAME, correlationId)
                .body(result);
    }
}
