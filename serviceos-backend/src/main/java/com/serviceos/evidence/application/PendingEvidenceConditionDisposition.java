package com.serviceos.evidence.application;

import java.util.UUID;

/** 最新 generation 中等待人工保留/作废决定的已提交槽位。 */
public record PendingEvidenceConditionDisposition(
        UUID memberId,
        UUID resolutionId,
        UUID taskId,
        UUID projectId,
        UUID slotId
) {
}
