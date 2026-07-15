package com.serviceos.evidence.application;

/** 条件事实应用结果；STALE 只完成 Inbox 与乱序审计，不产生倒退 generation。 */
public record EvidenceResolutionApplyResult(
        String outcome,
        EvidenceTaskResolution resolution,
        int activeSlotCount,
        long activatedCount,
        long deactivatedCount,
        long reviewRequiredCount
) {
    public static EvidenceResolutionApplyResult stale() {
        return new EvidenceResolutionApplyResult("STALE_NO_CHANGE", null, 0, 0, 0, 0);
    }

    public static EvidenceResolutionApplyResult awaitingFormFacts() {
        return new EvidenceResolutionApplyResult("AWAITING_FORM_FACTS", null, 0, 0, 0, 0);
    }

    public boolean applied() {
        return resolution != null;
    }
}
