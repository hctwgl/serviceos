package com.serviceos.evidence.api;

import java.util.List;
import java.util.UUID;

/**
 * 对 OPEN ReviewCase 追加决定。
 *
 * <p>M353：客户端只提交 targetDecisions；整组结果由服务端派生。</p>
 */
public record DecideReviewCaseCommand(
        UUID reviewCaseId,
        List<ReviewTargetDecisionCommand> targetDecisions,
        String note,
        long expectedAggregateVersion
) {
}
