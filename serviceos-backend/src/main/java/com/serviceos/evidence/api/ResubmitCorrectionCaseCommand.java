package com.serviceos.evidence.api;

import java.util.UUID;

/** 为 OPEN/RESUBMITTED CorrectionCase 追加补传 Snapshot。 */
public record ResubmitCorrectionCaseCommand(
        UUID correctionCaseId,
        UUID evidenceSetSnapshotId
) {
}
