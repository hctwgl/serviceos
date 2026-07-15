package com.serviceos.evidence.application;

import com.serviceos.evidence.api.EvidenceSlotView;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 一个 task.created 事件的原子解析结果；即使无匹配模板也保存零槽位完成事实。 */
public record EvidenceTaskResolution(
        UUID resolutionId,
        String tenantId,
        UUID projectId,
        UUID taskId,
        UUID configurationBundleId,
        String configurationBundleDigest,
        String stageCode,
        UUID sourceEventId,
        String sourceEventDigest,
        String resolverVersion,
        String conditionInputDigest,
        String resolutionExplanationJson,
        int generationNo,
        String conditionFactType,
        String conditionFactRef,
        int conditionFactRevision,
        UUID previousResolutionId,
        Instant resolvedAt,
        List<EvidenceSlotView> slots,
        List<EvidenceResolutionMember> members
) {
    public EvidenceTaskResolution {
        slots = List.copyOf(slots);
        members = List.copyOf(members);
    }
}
