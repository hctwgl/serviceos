package com.serviceos.configuration.infrastructure;

import com.serviceos.configuration.api.NotificationResolution;
import com.serviceos.configuration.application.NotificationDispatchStore;
import com.serviceos.shared.Sha256;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

import static com.serviceos.shared.infrastructure.PostgresJdbcParameters.timestamptz;

/**
 * M326：Notification Intent/Delivery/Attempt JDBC 适配器。
 */
@Repository
class JdbcNotificationDispatchStore implements NotificationDispatchStore {
    private final JdbcClient jdbc;

    JdbcNotificationDispatchStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean intentExists(String tenantId, UUID sourceEventId, String policyKey) {
        Integer count = jdbc.sql("""
                SELECT count(*)::int FROM cfg_notification_intent
                 WHERE tenant_id = :tenantId
                   AND source_event_id = :sourceEventId
                   AND policy_key = :policyKey
                """)
                .param("tenantId", tenantId)
                .param("sourceEventId", sourceEventId)
                .param("policyKey", policyKey)
                .query(Integer.class)
                .single();
        return count != null && count > 0;
    }

    @Override
    public void saveResolution(
            String tenantId,
            UUID projectId,
            UUID sourceEventId,
            String sourceEventType,
            String sourceAggregateType,
            String sourceAggregateId,
            UUID workOrderId,
            UUID taskId,
            UUID bundleId,
            String bundleDigest,
            String correlationId,
            Instant now,
            NotificationResolution resolution
    ) {
        String intentStatus = resolveIntentStatus(resolution);
        UUID intentId = UUID.randomUUID();
        String explanationDigest = Sha256.digest(String.join("\n", resolution.explanations()));
        // 与 Inbox 同事务；冲突说明同事件同策略已落库，不再重复写 Delivery。
        int intentInserted = jdbc.sql("""
                INSERT INTO cfg_notification_intent (
                    intent_id, tenant_id, project_id, source_event_id, source_event_type,
                    source_aggregate_type, source_aggregate_id, work_order_id, task_id,
                    bundle_id, bundle_digest, policy_key, asset_version_id, content_digest,
                    status, requires_manual_intervention, explanation_digest, correlation_id,
                    created_at, resolved_at
                ) VALUES (
                    :intentId, :tenantId, :projectId, :sourceEventId, :sourceEventType,
                    :sourceAggregateType, :sourceAggregateId, :workOrderId, :taskId,
                    :bundleId, :bundleDigest, :policyKey, :assetVersionId, :contentDigest,
                    :status, :requiresManual, :explanationDigest, :correlationId,
                    :createdAt, :resolvedAt
                )
                ON CONFLICT (tenant_id, source_event_id, policy_key) DO NOTHING
                """)
                .param("intentId", intentId)
                .param("tenantId", tenantId)
                .param("projectId", projectId)
                .param("sourceEventId", sourceEventId)
                .param("sourceEventType", sourceEventType)
                .param("sourceAggregateType", sourceAggregateType)
                .param("sourceAggregateId", sourceAggregateId)
                .param("workOrderId", workOrderId)
                .param("taskId", taskId)
                .param("bundleId", bundleId)
                .param("bundleDigest", bundleDigest)
                .param("policyKey", resolution.policyKey())
                .param("assetVersionId", resolution.assetVersionId())
                .param("contentDigest", resolution.contentDigest())
                .param("status", intentStatus)
                .param("requiresManual", resolution.requiresManualIntervention())
                .param("explanationDigest", explanationDigest)
                .param("correlationId", correlationId == null ? "" : correlationId)
                .param("createdAt", timestamptz(now))
                .param("resolvedAt", timestamptz(now))
                .update();
        if (intentInserted == 0) {
            return;
        }

        for (NotificationResolution.DeliveryAttempt attempt : resolution.attempts()) {
            UUID deliveryId = UUID.randomUUID();
            Instant acknowledgedAt = switch (attempt.outcome()) {
                case "SENT", "SENT_REPLAY" -> now;
                default -> null;
            };
            String detail = attempt.detail();
            if (detail != null && detail.length() > 240) {
                detail = detail.substring(0, 240);
            }
            int inserted = jdbc.sql("""
                    INSERT INTO cfg_notification_delivery (
                        delivery_id, intent_id, tenant_id, trigger_key, event_type, channel,
                        recipient_principal_id, template_key, idempotency_key, status,
                        provider_detail, created_at, acknowledged_at
                    ) VALUES (
                        :deliveryId, :intentId, :tenantId, :triggerKey, :eventType, :channel,
                        :recipient, :templateKey, :idempotencyKey, :status,
                        :detail, :createdAt, :acknowledgedAt
                    )
                    ON CONFLICT (tenant_id, idempotency_key) DO NOTHING
                    """)
                    .param("deliveryId", deliveryId)
                    .param("intentId", intentId)
                    .param("tenantId", tenantId)
                    .param("triggerKey", attempt.triggerKey())
                    .param("eventType", attempt.eventType())
                    .param("channel", attempt.channel())
                    .param("recipient", attempt.recipientPrincipalId())
                    .param("templateKey", attempt.templateKey())
                    .param("idempotencyKey", attempt.idempotencyKey())
                    .param("status", attempt.outcome())
                    .param("detail", detail)
                    .param("createdAt", timestamptz(now))
                    .param("acknowledgedAt", acknowledgedAt == null ? null : timestamptz(acknowledgedAt))
                    .update();
            if (inserted == 0) {
                continue;
            }
            jdbc.sql("""
                    INSERT INTO cfg_notification_attempt (
                        attempt_id, delivery_id, tenant_id, attempt_no, outcome, detail,
                        started_at, finished_at
                    ) VALUES (
                        :attemptId, :deliveryId, :tenantId, 1, :outcome, :detail,
                        :startedAt, :finishedAt
                    )
                    """)
                    .param("attemptId", UUID.randomUUID())
                    .param("deliveryId", deliveryId)
                    .param("tenantId", tenantId)
                    .param("outcome", attempt.outcome())
                    .param("detail", detail)
                    .param("startedAt", timestamptz(now))
                    .param("finishedAt", timestamptz(now))
                    .update();
        }
    }

    private static String resolveIntentStatus(NotificationResolution resolution) {
        boolean anyFailed = resolution.attempts().stream()
                .anyMatch(attempt -> "FAILED".equals(attempt.outcome()));
        boolean anyUnknown = resolution.attempts().stream()
                .anyMatch(attempt -> "UNKNOWN".equals(attempt.outcome()));
        boolean anySent = resolution.attempts().stream()
                .anyMatch(attempt -> "SENT".equals(attempt.outcome())
                        || "SENT_REPLAY".equals(attempt.outcome()));
        if (anyFailed && !anySent && !anyUnknown) {
            return "FAILED";
        }
        if (resolution.requiresManualIntervention() || anyUnknown || anyFailed
                || resolution.attempts().isEmpty()) {
            return "PARTIAL";
        }
        return "COMPLETED";
    }
}
