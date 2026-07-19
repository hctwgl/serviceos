package com.serviceos.evidence.api;

import java.util.List;
import java.util.UUID;

/** 单项审核目标决定；V1 仅开放 EvidenceRevision。 */
public record ReviewTargetDecisionCommand(
        String targetType,
        UUID targetId,
        int targetVersion,
        String decision,
        List<String> reasonCodes,
        String note
) {
}
