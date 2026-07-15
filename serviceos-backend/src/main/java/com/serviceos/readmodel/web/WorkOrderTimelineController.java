package com.serviceos.readmodel.web;

import com.serviceos.identity.api.CurrentPrincipalProvider;
import com.serviceos.readmodel.api.WorkOrderTimelinePage;
import com.serviceos.readmodel.api.WorkOrderTimelineQueryService;
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
final class WorkOrderTimelineController {
    private final WorkOrderTimelineQueryService timelines;
    private final CurrentPrincipalProvider principals;

    WorkOrderTimelineController(
            WorkOrderTimelineQueryService timelines,
            CurrentPrincipalProvider principals
    ) {
        this.timelines = timelines;
        this.principals = principals;
    }

    @GetMapping("/{workOrderId}/timeline")
    ResponseEntity<WorkOrderTimelinePage> list(
            @PathVariable UUID workOrderId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "50") int limit,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        WorkOrderTimelinePage result = timelines.list(
                principals.current(), correlationId, workOrderId, cursor, limit);
        return ResponseEntity.ok()
                .eTag(Long.toString(result.resourceVersion()))
                .header(CorrelationIds.HEADER_NAME, correlationId)
                .body(result);
    }
}
