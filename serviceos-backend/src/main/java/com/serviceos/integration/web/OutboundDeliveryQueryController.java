package com.serviceos.integration.web;

import com.serviceos.identity.api.CurrentPrincipalProvider;
import com.serviceos.integration.api.OutboundDeliveryQueryService;
import com.serviceos.integration.api.OutboundDeliveryQueuePage;
import com.serviceos.integration.api.OutboundDeliveryQueueQuery;
import com.serviceos.shared.CorrelationIds;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/outbound-deliveries")
final class OutboundDeliveryQueryController {
    private final OutboundDeliveryQueryService deliveries;
    private final CurrentPrincipalProvider principals;

    OutboundDeliveryQueryController(
            OutboundDeliveryQueryService deliveries, CurrentPrincipalProvider principals
    ) {
        this.deliveries = deliveries;
        this.principals = principals;
    }

    @GetMapping
    ResponseEntity<OutboundDeliveryQueuePage> list(
            @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String businessMessageType,
            @RequestParam(required = false) UUID sourceWorkOrderId,
            @RequestParam(required = false) UUID sourceReviewCaseId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "50") int limit,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        OutboundDeliveryQueuePage page = deliveries.list(
                principals.current(),
                correlationId,
                new OutboundDeliveryQueueQuery(
                        projectId, status, businessMessageType, sourceWorkOrderId,
                        sourceReviewCaseId, cursor, limit));
        return ResponseEntity.ok()
                .header(CorrelationIds.HEADER_NAME, correlationId)
                .body(page);
    }
}
