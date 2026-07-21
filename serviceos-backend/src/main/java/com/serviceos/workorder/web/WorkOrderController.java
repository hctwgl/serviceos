package com.serviceos.workorder.web;

import com.serviceos.identity.api.CurrentPrincipalProvider;
import com.serviceos.shared.CorrelationIds;
import com.serviceos.workorder.api.WorkOrderDetail;
import com.serviceos.workorder.api.WorkOrderPage;
import com.serviceos.workorder.api.WorkOrderQuery;
import com.serviceos.workorder.api.WorkOrderQueryService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/work-orders")
final class WorkOrderController {
    private final WorkOrderQueryService queries; private final CurrentPrincipalProvider principals;
    WorkOrderController(WorkOrderQueryService queries, CurrentPrincipalProvider principals) {
        this.queries=queries; this.principals=principals;
    }
    @GetMapping ResponseEntity<WorkOrderPage> list(@RequestParam(required=false) String clientCode,
            @RequestParam(required=false) UUID projectId, @RequestParam(required=false) String status,
            @RequestParam(required=false) String provinceCode, @RequestParam(required=false) String cityCode,
            @RequestParam(required=false) String districtCode,
            @RequestParam(required=false) String currentStageCode,
            @RequestParam(required=false) String currentTaskStatus,
            @RequestParam(required=false) UUID currentNetworkId,
            @RequestParam(required=false) UUID currentTechnicianId,
            @RequestParam(required=false) String slaRisk,
            @RequestParam(required=false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate receivedFrom,
            @RequestParam(required=false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate receivedTo,
            @RequestParam(required=false) String reviewCorrectionStatus,
            @RequestParam(required=false) String q,
            @RequestParam(required=false) String cursor, @RequestParam(defaultValue="50") int limit,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId) {
        return ResponseEntity.ok().header(CorrelationIds.HEADER_NAME,correlationId).body(
                queries.list(principals.current(),correlationId,new WorkOrderQuery(
                        clientCode, projectId, status, null, provinceCode, cityCode, districtCode,
                        currentStageCode, currentTaskStatus, currentNetworkId, currentTechnicianId, slaRisk,
                        receivedFrom, receivedTo, reviewCorrectionStatus, q, cursor, limit)));
    }
    @GetMapping("/{workOrderId}") ResponseEntity<WorkOrderDetail> get(@PathVariable UUID workOrderId,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId) {
        WorkOrderDetail result=queries.get(principals.current(),correlationId,workOrderId);
        return ResponseEntity.ok().eTag(Long.toString(result.workOrder().version()))
                .header(CorrelationIds.HEADER_NAME,correlationId).body(result);
    }
}
