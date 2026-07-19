package com.serviceos.workflow.application;

import com.serviceos.configuration.api.ExpressionContext;
import com.serviceos.configuration.api.FulfillmentFactInput;
import com.serviceos.configuration.api.PricingCalculationSnapshotCommand;
import com.serviceos.configuration.api.PricingCalculationSnapshotService;
import com.serviceos.reliability.spi.OutboxMessage;
import com.serviceos.reliability.spi.OutboxMessageHandler;
import com.serviceos.workorder.api.WorkOrderConfigurationBinding;
import com.serviceos.workorder.api.WorkOrderConfigurationBindingQuery;
import com.serviceos.workorder.api.WorkOrderExpressionContext;
import com.serviceos.workorder.api.WorkOrderExpressionContextQuery;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * M327：消费 workorder.fulfilled，委托 configuration 捕获履约事实与 PRICING SHADOW 快照。
 */
@Service
final class WorkOrderFulfilledPricingHandler implements OutboxMessageHandler {
    private final WorkOrderConfigurationBindingQuery bindings;
    private final WorkOrderExpressionContextQuery expressionContexts;
    private final PricingCalculationSnapshotService snapshots;
    private final ObjectMapper objectMapper;

    WorkOrderFulfilledPricingHandler(
            WorkOrderConfigurationBindingQuery bindings,
            WorkOrderExpressionContextQuery expressionContexts,
            PricingCalculationSnapshotService snapshots,
            ObjectMapper objectMapper
    ) {
        this.bindings = bindings;
        this.expressionContexts = expressionContexts;
        this.snapshots = snapshots;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(String eventType, int schemaVersion) {
        return "workorder.fulfilled".equals(eventType) && schemaVersion == 1;
    }

    @Override
    public void handle(OutboxMessage message) {
        if (!"workorder".equals(message.module()) || !"WorkOrder".equals(message.aggregateType())) {
            throw new IllegalArgumentException("unsupported WorkOrder pricing event envelope");
        }
        WorkOrderFulfilled payload = read(message.payload(), WorkOrderFulfilled.class);
        if (payload.workOrderId() == null || payload.fulfilledAt() == null
                || !payload.workOrderId().toString().equals(message.aggregateId())
                || !payload.fulfilledAt().equals(message.occurredAt())) {
            throw new IllegalArgumentException("WorkOrder pricing event identity mismatch");
        }

        WorkOrderConfigurationBinding binding = bindings.find(message.tenantId(), payload.workOrderId())
                .orElseThrow(() -> new IllegalStateException(
                        "WorkOrder configuration binding missing for pricing snapshot"));
        if (binding.configurationBundleId() == null
                || binding.configurationBundleDigest() == null
                || binding.configurationBundleDigest().isBlank()) {
            return;
        }
        WorkOrderExpressionContext wo = expressionContexts.find(message.tenantId(), payload.workOrderId())
                .orElseThrow(() -> new IllegalStateException(
                        "WorkOrder expression context missing for pricing snapshot"));

        ExpressionContext expressionContext = new ExpressionContext(
                new ExpressionContext.WorkOrderContext(
                        wo.clientCode(), wo.brandCode(), wo.serviceProductCode()),
                new ExpressionContext.RegionContext(
                        wo.provinceCode(), wo.cityCode(), wo.districtCode()),
                new ExpressionContext.TaskContext("FULFILLMENT", "SYSTEM"));

        snapshots.capture(new PricingCalculationSnapshotCommand(
                message.tenantId(),
                message.eventId(),
                message.schemaVersion(),
                message.payloadDigest(),
                message.correlationId(),
                message.eventType(),
                message.aggregateType(),
                message.aggregateId(),
                binding.projectId(),
                payload.workOrderId(),
                binding.configurationBundleId(),
                binding.configurationBundleDigest(),
                expressionContext,
                factsFrom(wo)));
    }

    private static List<FulfillmentFactInput> factsFrom(WorkOrderExpressionContext wo) {
        List<FulfillmentFactInput> facts = new ArrayList<>();
        add(facts, "workOrder.clientCode", wo.clientCode());
        add(facts, "workOrder.brandCode", wo.brandCode());
        add(facts, "workOrder.serviceProductCode", wo.serviceProductCode());
        add(facts, "workOrder.provinceCode", wo.provinceCode());
        add(facts, "workOrder.cityCode", wo.cityCode());
        add(facts, "workOrder.districtCode", wo.districtCode());
        return List.copyOf(facts);
    }

    private static void add(List<FulfillmentFactInput> facts, String code, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        facts.add(new FulfillmentFactInput(code, "STRING", value.trim()));
    }

    private <T> T read(String payload, Class<T> type) {
        try {
            return objectMapper.readValue(payload, type);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException(
                    "WorkOrder pricing event payload cannot be decoded", exception);
        }
    }

    private record WorkOrderFulfilled(UUID workOrderId, Instant fulfilledAt) {
    }
}
