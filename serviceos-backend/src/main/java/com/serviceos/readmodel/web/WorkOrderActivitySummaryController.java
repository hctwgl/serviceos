package com.serviceos.readmodel.web;

import com.serviceos.identity.api.CurrentPrincipalProvider;
import com.serviceos.readmodel.api.WorkOrderActivitySummary;
import com.serviceos.readmodel.api.WorkOrderActivitySummaryQueryService;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.CorrelationIds;
import com.serviceos.shared.ProblemCode;
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
final class WorkOrderActivitySummaryController {
    private final WorkOrderActivitySummaryQueryService summaries;
    private final CurrentPrincipalProvider principals;

    WorkOrderActivitySummaryController(
            WorkOrderActivitySummaryQueryService summaries,
            CurrentPrincipalProvider principals
    ) {
        this.summaries = summaries;
        this.principals = principals;
    }

    @GetMapping("/{workOrderId}/activity-summary")
    ResponseEntity<WorkOrderActivitySummary> get(
            @PathVariable UUID workOrderId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "5") int limit,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        if (cursor != null) {
            throw new BusinessProblem(
                    ProblemCode.VALIDATION_FAILED,
                    "activity-summary cursor paging is not accepted");
        }
        WorkOrderActivitySummary result =
                summaries.get(principals.current(), correlationId, workOrderId, limit);
        return ResponseEntity.ok()
                .eTag(Long.toString(result.resourceVersion()))
                .header(CorrelationIds.HEADER_NAME, correlationId)
                .body(result);
    }
}
