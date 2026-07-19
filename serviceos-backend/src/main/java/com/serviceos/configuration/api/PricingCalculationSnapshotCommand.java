package com.serviceos.configuration.api;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 工单履约完成后捕获 PRICING CalculationSnapshot 的命令。
 */
public record PricingCalculationSnapshotCommand(
        String tenantId,
        UUID eventId,
        int schemaVersion,
        String payloadDigest,
        String correlationId,
        String sourceEventType,
        String sourceAggregateType,
        String sourceAggregateId,
        UUID projectId,
        UUID workOrderId,
        UUID bundleId,
        String bundleDigest,
        ExpressionContext expressionContext,
        List<FulfillmentFactInput> facts
) {
    public PricingCalculationSnapshotCommand {
        tenantId = required(tenantId, "tenantId");
        Objects.requireNonNull(eventId, "eventId");
        payloadDigest = required(payloadDigest, "payloadDigest");
        sourceEventType = required(sourceEventType, "sourceEventType");
        sourceAggregateType = required(sourceAggregateType, "sourceAggregateType");
        sourceAggregateId = required(sourceAggregateId, "sourceAggregateId");
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(workOrderId, "workOrderId");
        Objects.requireNonNull(bundleId, "bundleId");
        bundleDigest = required(bundleDigest, "bundleDigest").toLowerCase(java.util.Locale.ROOT);
        Objects.requireNonNull(expressionContext, "expressionContext");
        facts = List.copyOf(Objects.requireNonNullElse(facts, List.of()));
        if (correlationId == null) {
            correlationId = "";
        }
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
