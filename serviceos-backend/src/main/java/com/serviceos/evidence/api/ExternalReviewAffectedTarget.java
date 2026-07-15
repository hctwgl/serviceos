package com.serviceos.evidence.api;

import java.util.UUID;

/**
 * 车企回执精确影响对象。
 *
 * <p>M54 仅开放已冻结在 ReviewCase 对应 EvidenceSetSnapshot 中的资料版本。三个标识必须共同
 * 命中同一 SnapshotMember，不能仅凭 revisionId 猜测槽位或逻辑资料归属。</p>
 */
public record ExternalReviewAffectedTarget(
        String targetType,
        UUID evidenceSlotId,
        UUID evidenceItemId,
        UUID evidenceRevisionId
) {
}
