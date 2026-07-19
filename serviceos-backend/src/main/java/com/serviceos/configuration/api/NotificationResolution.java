package com.serviceos.configuration.api;

import java.util.List;
import java.util.UUID;

/** NOTIFICATION 运行时输出：触发匹配、发送结果、UNKNOWN/人工接管与解释。 */
public record NotificationResolution(
        String policyKey,
        UUID assetVersionId,
        String contentDigest,
        List<DeliveryAttempt> attempts,
        boolean requiresManualIntervention,
        List<String> explanations
) {
    public NotificationResolution {
        attempts = List.copyOf(attempts);
        explanations = List.copyOf(explanations);
    }

    public record DeliveryAttempt(
            String triggerKey,
            String eventType,
            String channel,
            String recipientPrincipalId,
            String templateKey,
            String idempotencyKey,
            String outcome,
            String detail
    ) {
    }
}
