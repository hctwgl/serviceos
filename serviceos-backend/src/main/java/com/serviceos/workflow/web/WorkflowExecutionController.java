package com.serviceos.workflow.web;

import com.serviceos.identity.api.CurrentPrincipalProvider;
import com.serviceos.shared.CorrelationIds;
import com.serviceos.workflow.api.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/work-orders/{workOrderId}/stages")
final class WorkflowExecutionController {
 private final WorkflowExecutionQueryService queries; private final CurrentPrincipalProvider principals;
 WorkflowExecutionController(WorkflowExecutionQueryService queries,CurrentPrincipalProvider principals){this.queries=queries;this.principals=principals;}
 @GetMapping ResponseEntity<WorkflowExecutionProjection> get(@PathVariable UUID workOrderId,@RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId){return ResponseEntity.ok().header(CorrelationIds.HEADER_NAME,correlationId).body(queries.get(principals.current(),correlationId,workOrderId));}
}
