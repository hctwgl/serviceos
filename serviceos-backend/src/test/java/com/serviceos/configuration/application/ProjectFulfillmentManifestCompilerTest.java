package com.serviceos.configuration.application;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectFulfillmentManifestCompilerTest {

    @Test
    void compilesDeterministicDigestRegardlessOfStageMapInsertionOrder() {
        ProjectFulfillmentManifestCompiler compiler = new ProjectFulfillmentManifestCompiler();
        UUID profileId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID revisionId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID projectId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID bundleId = UUID.fromString("44444444-4444-4444-4444-444444444444");
        UUID workflowId = UUID.fromString("55555555-5555-5555-5555-555555555555");
        Instant effectiveFrom = Instant.parse("2026-07-20T00:00:00Z");

        Map<String, Object> stageA = new LinkedHashMap<>();
        stageA.put("stageCode", "SURVEY");
        stageA.put("stageName", "勘测");
        Map<String, Object> stageB = new LinkedHashMap<>();
        stageB.put("stageName", "勘测");
        stageB.put("stageCode", "SURVEY");

        Map<String, Object> doc1 = Map.of("stages", List.of(stageA), "orderTypeName", "勘测安装");
        Map<String, Object> doc2 = Map.of("stages", List.of(stageB), "orderTypeName", "勘测安装");

        var first = compiler.compile(
                profileId, revisionId, projectId, "HOME_CHARGING_SURVEY_INSTALL",
                "标准家充", "1", bundleId, "1.0.0", workflowId, effectiveFrom, doc1);
        var second = compiler.compile(
                profileId, revisionId, projectId, "HOME_CHARGING_SURVEY_INSTALL",
                "标准家充", "1", bundleId, "1.0.0", workflowId, effectiveFrom, doc2);

        assertThat(first.contentDigest()).isEqualTo(second.contentDigest());
        assertThat(first.contentDigest()).matches("[0-9a-f]{64}");
        assertThat(first.json()).contains("\"serviceProductCode\":\"HOME_CHARGING_SURVEY_INSTALL\"");
    }
}
