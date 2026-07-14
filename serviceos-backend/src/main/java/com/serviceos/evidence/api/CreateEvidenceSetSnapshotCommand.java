package com.serviceos.evidence.api;

import java.util.List;
import java.util.UUID;

/** 创建不可变 EvidenceSetSnapshot 的命令输入。 */
public record CreateEvidenceSetSnapshotCommand(
        UUID taskId,
        String purpose,
        List<UUID> memberRevisionIds
) {
}
