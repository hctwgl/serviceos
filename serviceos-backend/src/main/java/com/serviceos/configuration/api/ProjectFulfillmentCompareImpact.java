package com.serviceos.configuration.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 草稿相对当前发布版的真实差异与影响说明。 */
public record ProjectFulfillmentCompareImpact(
        UUID profileId,
        UUID draftRevisionId,
        String baselineKind,
        UUID baselineRevisionId,
        String baselineVersionLabel,
        int changeCount,
        List<ProjectFulfillmentCompareChange> changes,
        ProjectFulfillmentImpactSummary impact,
        List<String> risks,
        Instant asOf
) {
    public ProjectFulfillmentCompareImpact {
        changes = List.copyOf(changes == null ? List.of() : changes);
        risks = List.copyOf(risks == null ? List.of() : risks);
    }
}
