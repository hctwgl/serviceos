package com.serviceos.configuration.application;

import com.serviceos.configuration.api.ProjectFulfillmentDocument;
import com.serviceos.configuration.api.ProjectFulfillmentMatchRule;
import com.serviceos.configuration.api.ProjectFulfillmentNodeDraft;
import com.serviceos.configuration.api.ProjectFulfillmentPhaseDraft;
import com.serviceos.configuration.api.ProjectFulfillmentTransitionDraft;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectFulfillmentWorkflowCompilerTest {

    private final ProjectFulfillmentWorkflowCompiler compiler =
            new ProjectFulfillmentWorkflowCompiler();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void compilesReviewResultsAsOneExclusiveSerialDecision() throws Exception {
        var document = new ProjectFulfillmentDocument(
                "2.0", "勘测安装", ProjectFulfillmentMatchRule.unrestricted(), List.of(),
                List.of(), List.of(new ProjectFulfillmentPhaseDraft(
                        "SURVEY", "现场勘测", 1, null, "#1677ff")),
                List.of(
                        node("START", "START", null, Map.of(), List.of()),
                        node("REVIEW", "REVIEW", "SURVEY",
                                Map.of("taskType", "survey.review"), List.of("PASS", "REJECT")),
                        node("PASS_TASK", "HUMAN_TASK", "SURVEY",
                                Map.of("taskType", "install"), List.of()),
                        node("REWORK", "HUMAN_TASK", "SURVEY",
                                Map.of("taskType", "survey.rework"), List.of()),
                        node("END", "END", null, Map.of(), List.of())),
                List.of(
                        edge("E1", "START", "REVIEW", null, false),
                        edge("E2", "REVIEW", "PASS_TASK", "PASS", false),
                        edge("E3", "REVIEW", "REWORK", "REJECT", true),
                        edge("E4", "PASS_TASK", "END", null, false),
                        edge("E5", "REWORK", "REVIEW", null, false)));

        JsonNode json = mapper.readTree(
                compiler.compile("BYD_SURVEY", "比亚迪勘测安装", "1", document)
                        .definitionJson());

        assertThat(json.get("executionMode").asText()).isEqualTo("SERIAL_V1");
        assertThat(json.get("nodes").toString()).doesNotContain("PARALLEL_GATEWAY");
        assertThat(json.get("nodes").toString()).contains("RESULT_REVIEW", "EXCLUSIVE_GATEWAY");
        assertThat(json.get("transitions").toString())
                .contains("task.resultCode == \\\"PASS\\\"")
                .contains("!(task.resultCode == \\\"PASS\\\")");
    }

    @Test
    void keepsSingleResultAnnotatedSuccessorUnconditional() throws Exception {
        var document = new ProjectFulfillmentDocument(
                "2.0", "单线流程", ProjectFulfillmentMatchRule.unrestricted(), List.of(),
                List.of(), List.of(new ProjectFulfillmentPhaseDraft(
                        "P1", "执行", 1, null, "#1677ff")),
                List.of(
                        node("START", "START", null, Map.of(), List.of()),
                        node("ACTION", "SYSTEM_ACTION", "P1", Map.of(), List.of("SUCCESS")),
                        node("END", "END", null, Map.of(), List.of())),
                List.of(
                        edge("E1", "START", "ACTION", null, false),
                        edge("E2", "ACTION", "END", "SUCCESS", false)));

        JsonNode json = mapper.readTree(
                compiler.compile("ONE_LINE", "单线流程", "1", document).definitionJson());

        assertThat(json.get("nodes")).noneMatch(node ->
                "EXCLUSIVE_GATEWAY".equals(node.get("nodeType").asText()));
        JsonNode actionEdge = java.util.stream.StreamSupport
                .stream(json.get("transitions").spliterator(), false)
                .filter(edge -> "ACTION".equals(edge.get("from").asText()))
                .findFirst()
                .orElseThrow();
        assertThat(actionEdge.path("condition").isMissingNode()
                || actionEdge.path("condition").isNull()).isTrue();
    }

    @Test
    void freezesDesignerAssetBindingsIntoExecutableNodes() throws Exception {
        ProjectFulfillmentNodeDraft survey = new ProjectFulfillmentNodeDraft(
                "SURVEY", "HUMAN_TASK", "现场勘测", "P1", null, 0, 0,
                "现场工程师", "按项目责任范围匹配", true,
                Map.of("taskType", "FIELD_SURVEY"),
                Map.of("formKey", "survey-form"),
                List.of(Map.of("evidenceKey", "survey-photo")),
                Map.of("targetMinutes", 1440),
                List.of("COMPLETED"), Map.of(), Map.of(), Map.of(), null, List.of());
        var document = new ProjectFulfillmentDocument(
                "2.0", "资产绑定", ProjectFulfillmentMatchRule.unrestricted(), List.of(),
                List.of(), List.of(new ProjectFulfillmentPhaseDraft(
                        "P1", "现场勘测", 1, null, "#1677ff")),
                List.of(
                        node("START", "START", null, Map.of(), List.of()),
                        survey,
                        node("END", "END", null, Map.of(), List.of())),
                List.of(
                        edge("E1", "START", "SURVEY", null, false),
                        edge("E2", "SURVEY", "END", null, false)));
        var bindings = new ProjectFulfillmentWorkflowCompiler.RuntimeAssetBindings(
                null,
                "product.dispatch",
                Map.of("SURVEY", "product.survey.form"),
                Map.of("SURVEY", "product.survey.evidence"),
                Map.of("SURVEY", "product.survey.sla"),
                "product.submit");

        JsonNode json = mapper.readTree(
                compiler.compile("BINDINGS", "资产绑定", "1.0.0", document, bindings)
                        .definitionJson());
        JsonNode runtimeSurvey = java.util.stream.StreamSupport
                .stream(json.get("nodes").spliterator(), false)
                .filter(node -> "SURVEY".equals(node.get("nodeId").asText()))
                .findFirst()
                .orElseThrow();

        assertThat(runtimeSurvey.get("dispatchPolicyRef").asText())
                .isEqualTo("product.dispatch");
        assertThat(runtimeSurvey.get("formRef").asText()).isEqualTo("product.survey.form");
        assertThat(runtimeSurvey.get("evidenceRef").asText()).isEqualTo("product.survey.evidence");
        assertThat(runtimeSurvey.get("slaRef").asText()).isEqualTo("product.survey.sla");
        assertThat(runtimeSurvey.get("responsibilityRole").asText()).isEqualTo("现场工程师");
        assertThat(json.path("metadata").path("phaseNames").path("P1").asText())
                .isEqualTo("现场勘测");
    }

    private static ProjectFulfillmentNodeDraft node(
            String id,
            String type,
            String phase,
            Map<String, Object> task,
            List<String> results
    ) {
        Map<String, Object> system = "SYSTEM_ACTION".equals(type)
                ? Map.of(
                        "actionType", "test.action",
                        "retryPolicy", Map.of("maxAttempts", 3),
                        "failurePolicy", "RETRY_THEN_MANUAL")
                : Map.of();
        return new ProjectFulfillmentNodeDraft(
                id, type, id, phase, null, 0, 0, "PROJECT_OPERATOR", null,
                false, task, Map.of(), List.of(), Map.of(), results, system,
                Map.of(), Map.of(), null, List.of());
    }

    private static ProjectFulfillmentTransitionDraft edge(
            String id,
            String from,
            String to,
            String result,
            boolean defaultBranch
    ) {
        return new ProjectFulfillmentTransitionDraft(
                id, from, to, result, result, defaultBranch, Map.of());
    }
}
