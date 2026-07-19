package com.serviceos.integration.spi;

import java.util.UUID;

/** 通用取消入站管道结果。 */
public sealed interface InboundCancelWorkOrderResult {
    record Accepted(
            UUID workOrderId,
            UUID projectId,
            String businessKey,
            String connectorVersionId,
            String mappingVersionId,
            boolean replay
    ) implements InboundCancelWorkOrderResult {
    }

    record Rejected(String code, String message) implements InboundCancelWorkOrderResult {
    }
}
