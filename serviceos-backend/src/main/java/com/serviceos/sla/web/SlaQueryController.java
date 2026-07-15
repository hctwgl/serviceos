package com.serviceos.sla.web;

import com.serviceos.identity.api.CurrentPrincipalProvider;
import com.serviceos.shared.CorrelationIds;
import com.serviceos.sla.api.SlaInstanceDetail;
import com.serviceos.sla.api.SlaInstancePage;
import com.serviceos.sla.api.SlaInstanceQuery;
import com.serviceos.sla.api.SlaQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** SLA 只读 HTTP 适配器；tenant 和主体只来自受信 JWT 上下文。 */
@RestController
@RequestMapping("/api/v1")
final class SlaQueryController {
    private final SlaQueryService queries;
    private final CurrentPrincipalProvider principals;

    SlaQueryController(SlaQueryService queries, CurrentPrincipalProvider principals) {
        this.queries = queries;
        this.principals = principals;
    }

    @GetMapping("/sla-instances")
    SlaInstancePage list(
            @RequestParam UUID projectId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "50") int limit,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        return queries.list(principals.current(), correlationId,
                new SlaInstanceQuery(projectId, status, cursor, limit));
    }

    @GetMapping("/work-orders/{workOrderId}/sla-instances")
    SlaInstancePage listForWorkOrder(
            @PathVariable UUID workOrderId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "50") int limit,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        return queries.listForWorkOrder(
                principals.current(), correlationId, workOrderId, cursor, limit);
    }

    @GetMapping("/sla-instances/{slaInstanceId}")
    SlaInstanceDetail get(
            @PathVariable UUID slaInstanceId,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        return queries.get(principals.current(), correlationId, slaInstanceId);
    }
}
