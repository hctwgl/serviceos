package com.serviceos.configuration.application;

import com.serviceos.configuration.api.FulfillmentFactInput;
import com.serviceos.configuration.api.PricingResolution;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** CalculationSnapshot / FulfillmentFact 持久化端口。 */
public interface PricingCalculationSnapshotStore {
    boolean snapshotExists(String tenantId, UUID sourceEventId, String pricingKey);

    void saveFacts(
            String tenantId,
            UUID projectId,
            UUID workOrderId,
            UUID sourceEventId,
            Instant now,
            List<FulfillmentFactInput> facts
    );

    void saveSnapshot(
            String tenantId,
            UUID projectId,
            UUID workOrderId,
            UUID sourceEventId,
            String sourceEventType,
            UUID bundleId,
            String bundleDigest,
            String factsDigest,
            String correlationId,
            Instant now,
            PricingResolution resolution
    );
}
