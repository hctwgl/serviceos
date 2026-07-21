package com.serviceos.configuration.api;

/** 发布影响范围说明（新工单 / 存量冻结）。 */
public record ProjectFulfillmentImpactSummary(
        String newWorkOrdersScope,
        String existingWorkOrdersScope,
        String effectiveFromHint
) {
}
