package com.serviceos.evidence.api;

import java.util.UUID;

/** 高风险豁免 CorrectionCase。 */
public record WaiveCorrectionCaseCommand(
        UUID correctionCaseId,
        String reason,
        String approvalRef
) {
}
