package com.serviceos.integration.api;

import java.util.List;
import java.util.UUID;

/**
 * 人工确认外部已处理或放弃 UNKNOWN Delivery。
 *
 * <p>{@code result}：{@code MANUAL_CONFIRMED} 或 {@code ABANDONED}。</p>
 */
public record RecordManualAckCommand(
        UUID deliveryId,
        long expectedAggregateVersion,
        String result,
        String reason,
        String approvalRef,
        String externalRef,
        List<String> evidenceRefs
) {
}
