package com.serviceos.evidence.api;

import java.util.UUID;

/** 关闭已补传的 CorrectionCase（不等于审核通过）。 */
public record CloseCorrectionCaseCommand(UUID correctionCaseId, String note) {
}
