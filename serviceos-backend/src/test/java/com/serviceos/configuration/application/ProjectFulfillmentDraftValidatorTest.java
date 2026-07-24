package com.serviceos.configuration.application;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectFulfillmentDraftValidatorTest {

    private final ProjectFulfillmentDraftValidator validator =
            new ProjectFulfillmentDraftValidator();

    @Test
    void rejectsProductStagesThatDoNotMatchTheBoundRuntimeWorkflow() {
        Map<String, Object> document = Map.of(
                "stages", List.of(
                        stage("SURVEY", false),
                        stage("INSTALLATION", true)));
        Map<String, Object> workflow = Map.of(
                "nodes", List.of(
                        Map.of("nodeId", "START", "nodeType", "START"),
                        Map.of("nodeId", "SURVEY", "nodeType", "USER_TASK",
                                "stageCode", "SURVEY"),
                        Map.of("nodeId", "REVIEW", "nodeType", "REVIEW_TASK",
                                "stageCode", "FINAL_REVIEW"),
                        Map.of("nodeId", "END", "nodeType", "END")));

        var issues = validator.validate(UUID.randomUUID(), document, workflow, true);

        assertThat(issues)
                .extracting(issue -> issue.errorCode())
                .contains("STAGE_NOT_IN_WORKFLOW", "WORKFLOW_STAGE_NOT_DESCRIBED");
    }

    @Test
    void acceptsCompleteStageCoverageForTheBoundRuntimeWorkflow() {
        Map<String, Object> document = Map.of(
                "stages", List.of(
                        stage("SURVEY", false),
                        stage("FINAL_REVIEW", true)));
        Map<String, Object> workflow = Map.of(
                "nodes", List.of(
                        Map.of("nodeId", "START", "nodeType", "START"),
                        Map.of("nodeId", "SURVEY", "nodeType", "USER_TASK",
                                "stageCode", "SURVEY"),
                        Map.of("nodeId", "REVIEW", "nodeType", "REVIEW_TASK",
                                "stageCode", "FINAL_REVIEW"),
                        Map.of("nodeId", "END", "nodeType", "END")));

        var issues = validator.validate(UUID.randomUUID(), document, workflow, true);

        assertThat(issues).noneMatch(issue -> "ERROR".equals(issue.severity()));
    }

    private static Map<String, Object> stage(String code, boolean terminal) {
        return Map.of(
                "stageCode", code,
                "ownerType", "PLATFORM",
                "terminal", terminal);
    }
}
