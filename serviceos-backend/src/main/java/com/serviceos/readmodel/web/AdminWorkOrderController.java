package com.serviceos.readmodel.web;

import com.serviceos.identity.api.CurrentPrincipalProvider;
import com.serviceos.readmodel.api.AdminWorkOrderDirectoryView;
import com.serviceos.readmodel.api.AdminWorkOrderQueryService;
import com.serviceos.readmodel.api.AdminWorkOrderWorkspaceView;
import com.serviceos.shared.CorrelationIds;
import com.serviceos.workorder.api.WorkOrderQuery;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

/** Admin 工单黄金链路页面级查询入口。 */
@RestController
@RequestMapping("/api/v1/admin/work-orders")
final class AdminWorkOrderController {
    private final AdminWorkOrderQueryService queries;
    private final CurrentPrincipalProvider principals;

    AdminWorkOrderController(
            AdminWorkOrderQueryService queries,
            CurrentPrincipalProvider principals
    ) {
        this.queries = queries;
        this.principals = principals;
    }

    @GetMapping
    ResponseEntity<AdminWorkOrderDirectoryView> list(
            @RequestParam(required = false) String clientCode,
            @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String currentStageCode,
            @RequestParam(required = false) UUID currentNetworkId,
            @RequestParam(required = false) UUID currentTechnicianId,
            @RequestParam(required = false) String responsibilityStatus,
            @RequestParam(required = false) String slaRisk,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate receivedFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate receivedTo,
            @RequestParam(required = false) String reviewCorrectionStatus,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        WorkOrderQuery query = new WorkOrderQuery(
                clientCode, projectId, status, null, null, null, null, currentStageCode, null,
                currentNetworkId, currentTechnicianId, responsibilityStatus, slaRisk, receivedFrom, receivedTo,
                reviewCorrectionStatus, q, cursor, limit);
        return ResponseEntity.ok()
                .header(CorrelationIds.HEADER_NAME, correlationId)
                .body(queries.list(principals.current(), correlationId, query));
    }

    @GetMapping("/{workOrderId}/workspace")
    ResponseEntity<AdminWorkOrderWorkspaceView> getWorkspace(
            @PathVariable UUID workOrderId,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        AdminWorkOrderWorkspaceView result = queries.getWorkspace(
                principals.current(), correlationId, workOrderId);
        return ResponseEntity.ok()
                .eTag(Long.toString(result.workspace().sourceVersions().workOrderVersion()))
                .header(CorrelationIds.HEADER_NAME, correlationId)
                .body(result);
    }
}
