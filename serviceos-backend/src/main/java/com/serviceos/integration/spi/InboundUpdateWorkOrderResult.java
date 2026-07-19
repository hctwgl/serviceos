package com.serviceos.integration.spi;

import java.util.UUID;

/** 通用更新入站管道结果。 */
public sealed interface InboundUpdateWorkOrderResult {
    record Accepted(
            UUID workOrderId,
            UUID projectId,
            String businessKey,
            String connectorVersionId,
            String mappingVersionId,
            boolean replay
    ) implements InboundUpdateWorkOrderResult {
    }

    record Rejected(String code, String message) implements InboundUpdateWorkOrderResult {
    }
}
