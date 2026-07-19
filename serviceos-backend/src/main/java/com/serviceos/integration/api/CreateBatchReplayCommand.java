package com.serviceos.integration.api;

import java.util.List;
import java.util.UUID;

/** 创建批量重放预演或提交审批。mode=PREVIEW|SUBMIT。 */
public record CreateBatchReplayCommand(
        List<UUID> deliveryIds,
        String mode,
        String reason,
        String approvalRef,
        Integer maxItems
) {
}
