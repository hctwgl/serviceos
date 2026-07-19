package com.serviceos.configuration.web;

import com.serviceos.configuration.api.PricingShadowSnapshotQueryService;
import com.serviceos.configuration.api.PricingShadowSnapshotView;
import com.serviceos.identity.api.CurrentPrincipalProvider;
import com.serviceos.shared.CorrelationIds;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Admin 工单 SHADOW 定价试算只读 HTTP 边界（非结算）。 */
@RestController
@RequestMapping("/api/v1/work-orders")
final class WorkOrderPricingSnapshotController {
    private final PricingShadowSnapshotQueryService queries;
    private final CurrentPrincipalProvider principals;

    WorkOrderPricingSnapshotController(
            PricingShadowSnapshotQueryService queries,
            CurrentPrincipalProvider principals
    ) {
        this.queries = queries;
        this.principals = principals;
    }

    @GetMapping("/{workOrderId}/pricing-snapshots")
    ResponseEntity<PricingShadowSnapshotPageResponse> list(
            @PathVariable UUID workOrderId,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        List<PricingShadowSnapshotView> items = queries.listByWorkOrder(
                principals.current(), correlationId, workOrderId);
        return ResponseEntity.ok()
                .header(CorrelationIds.HEADER_NAME, correlationId)
                .body(PricingShadowSnapshotPageResponse.from(
                        workOrderId, items, Instant.now()));
    }

    record PricingShadowSnapshotPageResponse(
            UUID workOrderId,
            List<PricingShadowSnapshotItemResponse> items,
            Instant queriedAt,
            String emptyHint
    ) {
        static PricingShadowSnapshotPageResponse from(
                UUID workOrderId, List<PricingShadowSnapshotView> views, Instant queriedAt
        ) {
            return new PricingShadowSnapshotPageResponse(
                    workOrderId,
                    views.stream().map(PricingShadowSnapshotItemResponse::from).toList(),
                    queriedAt,
                    views.isEmpty()
                            ? "仅真实完工事件触发影子试算；SQL 种子 FULFILLED 不会有快照"
                            : null);
        }
    }

    record PricingShadowSnapshotItemResponse(
            UUID snapshotId,
            UUID workOrderId,
            UUID projectId,
            UUID sourceEventId,
            String sourceEventType,
            String pricingKey,
            String currency,
            long totalAmountMinor,
            String mode,
            String correlationId,
            Instant createdAt
    ) {
        static PricingShadowSnapshotItemResponse from(PricingShadowSnapshotView view) {
            return new PricingShadowSnapshotItemResponse(
                    view.snapshotId(), view.workOrderId(), view.projectId(),
                    view.sourceEventId(), view.sourceEventType(), view.pricingKey(),
                    view.currency(), view.totalAmountMinor(), view.mode(),
                    view.correlationId(), view.createdAt());
        }
    }
}
