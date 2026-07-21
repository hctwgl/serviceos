package com.serviceos.configuration.application;

import com.serviceos.configuration.api.ProjectFulfillmentCompareImpact;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectFulfillmentCompareAnalyzerTest {

    private final ProjectFulfillmentCompareAnalyzer analyzer = new ProjectFulfillmentCompareAnalyzer();

    @Test
    void reportsNoneBaselineWhenNoPublishedRevision() {
        UUID profileId = UUID.randomUUID();
        UUID draftId = UUID.randomUUID();
        ProjectFulfillmentCompareImpact impact = analyzer.analyze(
                profileId,
                draftId,
                "{\"stages\":[{\"stageCode\":\"A\",\"stageName\":\"受理\",\"sequence\":1,\"ownerType\":\"PLATFORM\",\"formRefs\":[],\"evidenceRefs\":[],\"actions\":[]}]}",
                null,
                null,
                null,
                Instant.parse("2026-07-20T00:00:00Z"));
        assertThat(impact.baselineKind()).isEqualTo("NONE");
        assertThat(impact.changeCount()).isGreaterThanOrEqualTo(1);
        assertThat(impact.impact().existingWorkOrdersScope()).contains("冻结");
    }

    @Test
    void detectsAddedStageAgainstPublishedBaseline() {
        String baseline = "{\"stages\":[{\"stageCode\":\"A\",\"stageName\":\"受理\",\"sequence\":1,\"ownerType\":\"PLATFORM\",\"formRefs\":[],\"evidenceRefs\":[],\"actions\":[]}]}";
        String draft = "{\"stages\":["
                + "{\"stageCode\":\"A\",\"stageName\":\"受理\",\"sequence\":1,\"ownerType\":\"PLATFORM\",\"formRefs\":[],\"evidenceRefs\":[],\"actions\":[]},"
                + "{\"stageCode\":\"B\",\"stageName\":\"勘测\",\"sequence\":2,\"ownerType\":\"TECHNICIAN\",\"formRefs\":[\"f1\"],\"evidenceRefs\":[],\"actions\":[]}"
                + "]}";
        ProjectFulfillmentCompareImpact impact = analyzer.analyze(
                UUID.randomUUID(),
                UUID.randomUUID(),
                draft,
                UUID.randomUUID(),
                "v1",
                baseline,
                Instant.parse("2026-07-20T00:00:00Z"));
        assertThat(impact.baselineKind()).isEqualTo("PUBLISHED");
        assertThat(impact.changes()).anyMatch(change ->
                "STAGE".equals(change.category())
                        && "ADDED".equals(change.changeType())
                        && change.summary().contains("勘测"));
        assertThat(impact.changeCount()).isEqualTo(impact.changes().size());
    }
}
