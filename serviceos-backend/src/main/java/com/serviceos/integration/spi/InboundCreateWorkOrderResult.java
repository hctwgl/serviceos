package com.serviceos.integration.spi;

import java.util.UUID;

/**
 * 通用建单入站管道结果。适配器负责映射为 OEM HTTP/文件协议响应，不得改写领域事实。
 */
public sealed interface InboundCreateWorkOrderResult {
    record Accepted(
            UUID workOrderId,
            UUID projectId,
            String businessKey,
            String connectorVersionId,
            String mappingVersionId,
            boolean replay
    ) implements InboundCreateWorkOrderResult {
    }

    record Rejected(String code, String message) implements InboundCreateWorkOrderResult {
    }
}
