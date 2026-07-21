package com.serviceos.configuration.infrastructure;

import com.serviceos.configuration.api.NotificationResolution;
import com.serviceos.configuration.application.NotificationDispatchStore;
import com.serviceos.jooq.generated.tables.CfgNotificationAttempt;
import com.serviceos.jooq.generated.tables.CfgNotificationDelivery;
import com.serviceos.jooq.generated.tables.CfgNotificationIntent;
import com.serviceos.shared.Sha256;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

import static com.serviceos.jooq.generated.tables.CfgNotificationAttempt.CFG_NOTIFICATION_ATTEMPT;
import static com.serviceos.jooq.generated.tables.CfgNotificationDelivery.CFG_NOTIFICATION_DELIVERY;
import static com.serviceos.jooq.generated.tables.CfgNotificationIntent.CFG_NOTIFICATION_INTENT;

/**
 * M326：Notification Intent/Delivery/Attempt 适配器（jOOQ）。
 */
@Repository
final class JooqNotificationDispatchStore implements NotificationDispatchStore {
    private final DSLContext dsl;

    JooqNotificationDispatchStore(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public boolean intentExists(String tenantId, UUID sourceEventId, String policyKey) {
        CfgNotificationIntent i = CFG_NOTIFICATION_INTENT;
        return dsl.fetchExists(i,
                i.TENANT_ID.eq(tenantId),
                i.SOURCE_EVENT_ID.eq(sourceEventId),
                i.POLICY_KEY.eq(policyKey));
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
        CfgNotificationIntent i = CFG_NOTIFICATION_INTENT;
        int intentInserted = dsl.insertInto(i)
                .set(i.INTENT_ID, intentId)
                .set(i.TENANT_ID, tenantId)
                .set(i.PROJECT_ID, projectId)
                .set(i.SOURCE_EVENT_ID, sourceEventId)
                .set(i.SOURCE_EVENT_TYPE, sourceEventType)
                .set(i.SOURCE_AGGREGATE_TYPE, sourceAggregateType)
                .set(i.SOURCE_AGGREGATE_ID, sourceAggregateId)
                .set(i.WORK_ORDER_ID, workOrderId)
                .set(i.TASK_ID, taskId)
                .set(i.BUNDLE_ID, bundleId)
                .set(i.BUNDLE_DIGEST, bundleDigest)
                .set(i.POLICY_KEY, resolution.policyKey())
                .set(i.ASSET_VERSION_ID, resolution.assetVersionId())
                .set(i.CONTENT_DIGEST, resolution.contentDigest())
                .set(i.STATUS, intentStatus)
                .set(i.REQUIRES_MANUAL_INTERVENTION, resolution.requiresManualIntervention())
                .set(i.EXPLANATION_DIGEST, explanationDigest)
                .set(i.CORRELATION_ID, correlationId == null ? "" : correlationId)
                .set(i.CREATED_AT, now)
                .set(i.RESOLVED_AT, now)
                .onConflict(i.TENANT_ID, i.SOURCE_EVENT_ID, i.POLICY_KEY)
                .doNothing()
                .execute();
        if (intentInserted == 0) {
            return;
        }

        CfgNotificationDelivery d = CFG_NOTIFICATION_DELIVERY;
        CfgNotificationAttempt at = CFG_NOTIFICATION_ATTEMPT;
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
            int inserted = dsl.insertInto(d)
                    .set(d.DELIVERY_ID, deliveryId)
                    .set(d.INTENT_ID, intentId)
                    .set(d.TENANT_ID, tenantId)
                    .set(d.TRIGGER_KEY, attempt.triggerKey())
                    .set(d.EVENT_TYPE, attempt.eventType())
                    .set(d.CHANNEL, attempt.channel())
                    .set(d.RECIPIENT_PRINCIPAL_ID, attempt.recipientPrincipalId())
                    .set(d.TEMPLATE_KEY, attempt.templateKey())
                    .set(d.IDEMPOTENCY_KEY, attempt.idempotencyKey())
                    .set(d.STATUS, attempt.outcome())
                    .set(d.PROVIDER_DETAIL, detail)
                    .set(d.CREATED_AT, now)
                    .set(d.ACKNOWLEDGED_AT, acknowledgedAt)
                    .onConflict(d.TENANT_ID, d.IDEMPOTENCY_KEY)
                    .doNothing()
                    .execute();
            if (inserted == 0) {
                continue;
            }
            dsl.insertInto(at)
                    .set(at.ATTEMPT_ID, UUID.randomUUID())
                    .set(at.DELIVERY_ID, deliveryId)
                    .set(at.TENANT_ID, tenantId)
                    .set(at.ATTEMPT_NO, 1)
                    .set(at.OUTCOME, attempt.outcome())
                    .set(at.DETAIL, detail)
                    .set(at.STARTED_AT, now)
                    .set(at.FINISHED_AT, now)
                    .execute();
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
