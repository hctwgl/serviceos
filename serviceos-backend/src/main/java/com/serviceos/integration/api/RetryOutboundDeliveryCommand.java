package com.serviceos.integration.api;

import java.util.UUID;

/** UNKNOWN 外部交付的高风险人工重发命令。 */
public record RetryOutboundDeliveryCommand(
        UUID deliveryId,
        long expectedAggregateVersion,
        String reason,
        String approvalRef
) {
}
