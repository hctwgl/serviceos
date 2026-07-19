package com.serviceos.evidence.api;

import java.util.UUID;

/**
 * M353/M354：decide 结果；REJECTED 时携带同事务创建的 CorrectionCase。
 */
public record DecideReviewCaseResult(
        ReviewCaseView reviewCase,
        UUID correctionCaseId
) {
}
