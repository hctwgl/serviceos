package com.serviceos.configuration.api;

/** 运行说明书中的单个阶段业务摘要。 */
public record ProjectFulfillmentRunbookStage(
        String stageName,
        int sequence,
        String ownerTypeLabel,
        String taskTypeLabel,
        int formCount,
        String formSummary,
        int evidenceCount,
        String evidenceSummary,
        int actionCount,
        String actionSummary,
        String nextStageSummary,
        String exceptionSummary,
        String slaSummary,
        boolean terminal
) {
}
