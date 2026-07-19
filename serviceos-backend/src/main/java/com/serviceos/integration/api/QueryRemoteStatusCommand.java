package com.serviceos.integration.api;

import java.util.UUID;

/** UNKNOWN OutboundDelivery 远端状态查询命令。 */
public record QueryRemoteStatusCommand(
        UUID deliveryId,
        String reason
) {
}
