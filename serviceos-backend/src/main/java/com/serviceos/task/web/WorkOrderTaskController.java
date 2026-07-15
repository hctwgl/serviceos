package com.serviceos.task.web;

import com.serviceos.identity.api.CurrentPrincipalProvider;
import com.serviceos.shared.CorrelationIds;
import com.serviceos.task.api.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/work-orders/{workOrderId}/tasks")
final class WorkOrderTaskController {
 private final WorkOrderTaskQueryService queries;private final CurrentPrincipalProvider principals;
 WorkOrderTaskController(WorkOrderTaskQueryService queries,CurrentPrincipalProvider principals){this.queries=queries;this.principals=principals;}
 @GetMapping ResponseEntity<WorkOrderTaskPage> list(@PathVariable UUID workOrderId,@RequestParam(required=false) String cursor,@RequestParam(defaultValue="50") int limit,@RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId){return ResponseEntity.ok().header(CorrelationIds.HEADER_NAME,correlationId).body(queries.list(principals.current(),correlationId,workOrderId,cursor,limit));}
}
