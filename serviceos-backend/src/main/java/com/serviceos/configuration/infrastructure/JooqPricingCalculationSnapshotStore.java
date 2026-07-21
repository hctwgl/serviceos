package com.serviceos.configuration.infrastructure;

import com.serviceos.configuration.api.FulfillmentFactInput;
import com.serviceos.configuration.api.PricingResolution;
import com.serviceos.configuration.application.PricingCalculationSnapshotStore;
import com.serviceos.jooq.generated.tables.CfgCalculationSnapshot;
import com.serviceos.jooq.generated.tables.CfgFulfillmentFact;
import com.serviceos.shared.Sha256;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.serviceos.jooq.generated.tables.CfgCalculationSnapshot.CFG_CALCULATION_SNAPSHOT;
import static com.serviceos.jooq.generated.tables.CfgFulfillmentFact.CFG_FULFILLMENT_FACT;

/** M327：履约事实与 CalculationSnapshot 适配器（jOOQ）。 */
@Repository
final class JooqPricingCalculationSnapshotStore implements PricingCalculationSnapshotStore {
    private final DSLContext dsl;
    private final ObjectMapper objectMapper;

    JooqPricingCalculationSnapshotStore(DSLContext dsl, ObjectMapper objectMapper) {
        this.dsl = dsl;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean snapshotExists(String tenantId, UUID sourceEventId, String pricingKey) {
        CfgCalculationSnapshot s = CFG_CALCULATION_SNAPSHOT;
        return dsl.fetchExists(s,
                s.TENANT_ID.eq(tenantId),
                s.SOURCE_EVENT_ID.eq(sourceEventId),
                s.PRICING_KEY.eq(pricingKey));
    }

    @Override
    public void saveFacts(
            String tenantId,
            UUID projectId,
            UUID workOrderId,
            UUID sourceEventId,
            Instant now,
            List<FulfillmentFactInput> facts
    ) {
        CfgFulfillmentFact f = CFG_FULFILLMENT_FACT;
        for (FulfillmentFactInput fact : facts) {
            String digest = Sha256.digest(fact.factCode() + "|" + fact.valueType() + "|"
                    + (fact.valueText() == null ? "" : fact.valueText()));
            // 幂等：同事件同事实码已落库则跳过，不覆盖既有事实。
            dsl.insertInto(f)
                    .set(f.FACT_ID, UUID.randomUUID())
                    .set(f.TENANT_ID, tenantId)
                    .set(f.PROJECT_ID, projectId)
                    .set(f.WORK_ORDER_ID, workOrderId)
                    .set(f.SOURCE_EVENT_ID, sourceEventId)
                    .set(f.FACT_CODE, fact.factCode())
                    .set(f.VALUE_TYPE, fact.valueType())
                    .set(f.VALUE_TEXT, fact.valueText())
                    .set(f.STATUS, "CONFIRMED")
                    .set(f.CONTENT_DIGEST, digest)
                    .set(f.CREATED_AT, now)
                    .onConflict(f.TENANT_ID, f.SOURCE_EVENT_ID, f.FACT_CODE)
                    .doNothing()
                    .execute();
        }
    }

    @Override
    public void saveSnapshot(
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
    ) {
        CfgCalculationSnapshot s = CFG_CALCULATION_SNAPSHOT;
        // matched_lines_json/explanations_json 由全局 JsonbStringConverter 绑定（String -> JSONB）；
        // 冲突说明同事件同定价策略已落库，保持首次快照不变。
        dsl.insertInto(s)
                .set(s.SNAPSHOT_ID, UUID.randomUUID())
                .set(s.TENANT_ID, tenantId)
                .set(s.PROJECT_ID, projectId)
                .set(s.WORK_ORDER_ID, workOrderId)
                .set(s.SOURCE_EVENT_ID, sourceEventId)
                .set(s.SOURCE_EVENT_TYPE, sourceEventType)
                .set(s.BUNDLE_ID, bundleId)
                .set(s.BUNDLE_DIGEST, bundleDigest)
                .set(s.PRICING_KEY, resolution.pricingKey())
                .set(s.ASSET_VERSION_ID, resolution.assetVersionId())
                .set(s.ASSET_CONTENT_DIGEST, resolution.contentDigest())
                .set(s.CURRENCY, resolution.currency())
                .set(s.TOTAL_AMOUNT_MINOR, resolution.totalAmountMinor())
                .set(s.MATCHED_LINES_JSON, json(resolution.matchedLines()))
                .set(s.EXPLANATIONS_JSON, json(resolution.explanations()))
                .set(s.FACTS_DIGEST, factsDigest)
                .set(s.MODE, "SHADOW")
                .set(s.CORRELATION_ID, correlationId == null ? "" : correlationId)
                .set(s.CREATED_AT, now)
                .onConflict(s.TENANT_ID, s.SOURCE_EVENT_ID, s.PRICING_KEY)
                .doNothing()
                .execute();
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("CalculationSnapshot JSON serialization failed", exception);
        }
    }
}
