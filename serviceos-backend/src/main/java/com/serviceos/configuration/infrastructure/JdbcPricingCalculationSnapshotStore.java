package com.serviceos.configuration.infrastructure;

import com.serviceos.configuration.api.FulfillmentFactInput;
import com.serviceos.configuration.api.PricingResolution;
import com.serviceos.configuration.application.PricingCalculationSnapshotStore;
import com.serviceos.shared.Sha256;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.serviceos.shared.infrastructure.PostgresJdbcParameters.timestamptz;

/** M327：履约事实与 CalculationSnapshot JDBC 适配器。 */
@Repository
class JdbcPricingCalculationSnapshotStore implements PricingCalculationSnapshotStore {
    private final JdbcClient jdbc;
    private final ObjectMapper objectMapper;

    JdbcPricingCalculationSnapshotStore(JdbcClient jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean snapshotExists(String tenantId, UUID sourceEventId, String pricingKey) {
        Integer count = jdbc.sql("""
                SELECT count(*)::int FROM cfg_calculation_snapshot
                 WHERE tenant_id = :tenantId
                   AND source_event_id = :sourceEventId
                   AND pricing_key = :pricingKey
                """)
                .param("tenantId", tenantId)
                .param("sourceEventId", sourceEventId)
                .param("pricingKey", pricingKey)
                .query(Integer.class)
                .single();
        return count != null && count > 0;
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
        for (FulfillmentFactInput fact : facts) {
            String digest = Sha256.digest(fact.factCode() + "|" + fact.valueType() + "|"
                    + (fact.valueText() == null ? "" : fact.valueText()));
            jdbc.sql("""
                    INSERT INTO cfg_fulfillment_fact (
                        fact_id, tenant_id, project_id, work_order_id, source_event_id,
                        fact_code, value_type, value_text, status, content_digest, created_at
                    ) VALUES (
                        :factId, :tenantId, :projectId, :workOrderId, :sourceEventId,
                        :factCode, :valueType, :valueText, 'CONFIRMED', :digest, :createdAt
                    )
                    ON CONFLICT (tenant_id, source_event_id, fact_code) DO NOTHING
                    """)
                    .param("factId", UUID.randomUUID())
                    .param("tenantId", tenantId)
                    .param("projectId", projectId)
                    .param("workOrderId", workOrderId)
                    .param("sourceEventId", sourceEventId)
                    .param("factCode", fact.factCode())
                    .param("valueType", fact.valueType())
                    .param("valueText", fact.valueText())
                    .param("digest", digest)
                    .param("createdAt", timestamptz(now))
                    .update();
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
        jdbc.sql("""
                INSERT INTO cfg_calculation_snapshot (
                    snapshot_id, tenant_id, project_id, work_order_id, source_event_id,
                    source_event_type, bundle_id, bundle_digest, pricing_key, asset_version_id,
                    asset_content_digest, currency, total_amount_minor, matched_lines_json,
                    explanations_json, facts_digest, mode, correlation_id, created_at
                ) VALUES (
                    :snapshotId, :tenantId, :projectId, :workOrderId, :sourceEventId,
                    :sourceEventType, :bundleId, :bundleDigest, :pricingKey, :assetVersionId,
                    :assetContentDigest, :currency, :totalAmountMinor, CAST(:matchedLines AS jsonb),
                    CAST(:explanations AS jsonb), :factsDigest, 'SHADOW', :correlationId, :createdAt
                )
                ON CONFLICT (tenant_id, source_event_id, pricing_key) DO NOTHING
                """)
                .param("snapshotId", UUID.randomUUID())
                .param("tenantId", tenantId)
                .param("projectId", projectId)
                .param("workOrderId", workOrderId)
                .param("sourceEventId", sourceEventId)
                .param("sourceEventType", sourceEventType)
                .param("bundleId", bundleId)
                .param("bundleDigest", bundleDigest)
                .param("pricingKey", resolution.pricingKey())
                .param("assetVersionId", resolution.assetVersionId())
                .param("assetContentDigest", resolution.contentDigest())
                .param("currency", resolution.currency())
                .param("totalAmountMinor", resolution.totalAmountMinor())
                .param("matchedLines", json(resolution.matchedLines()))
                .param("explanations", json(resolution.explanations()))
                .param("factsDigest", factsDigest)
                .param("correlationId", correlationId == null ? "" : correlationId)
                .param("createdAt", timestamptz(now))
                .update();
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("CalculationSnapshot JSON serialization failed", exception);
        }
    }
}
