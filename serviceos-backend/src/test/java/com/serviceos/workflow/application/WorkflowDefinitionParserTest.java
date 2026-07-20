package com.serviceos.workflow.application;

import com.serviceos.configuration.api.ConfigurationAssetDefinition;
import com.serviceos.configuration.api.ConfigurationAssetType;
import com.serviceos.configuration.api.ExpressionContext;
import com.serviceos.configuration.api.ExpressionDefinition;
import com.serviceos.configuration.api.ExpressionEvaluation;
import com.serviceos.configuration.api.ExpressionEvaluator;
import com.serviceos.shared.Sha256;
import com.serviceos.task.api.WorkflowTaskKind;
import com.serviceos.workflow.api.ReviewGateWait;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkflowDefinitionParserTest {
    private final WorkflowDefinitionParser parser =
            new WorkflowDefinitionParser(new ObjectMapper(), new BrandAwareEvaluator());

    @Test
    void freezesTheFirstExecutableTaskFromTheExactAsset() {
        var result = parser.parse(asset(validDefinition()));

        assertThat(result.workflowKey()).isEqualTo("byd.survey-install");
        assertThat(result.workflowVersion()).isEqualTo("1.0.0");
        assertThat(result.firstNodeId()).isEqualTo("ASSIGN_COORDINATORS");
        assertThat(result.firstStageCode()).isEqualTo("INTAKE");
        assertThat(result.firstTaskKind()).isEqualTo(WorkflowTaskKind.AUTOMATED);
        assertThat(result.firstFormRef()).isEqualTo("survey.form");
        assertThat(result.firstSlaRef()).isEqualTo("intake.assignment.sla");
    }

    @Test
    void rejectsAmbiguousStartAndVersionDrift() {
        String ambiguous = validDefinition().replace(
                "]}", ",{\"transitionId\":\"t2\",\"from\":\"START\",\"to\":\"ASSIGN_COORDINATORS\"}]}");
        assertThatThrownBy(() -> parser.parse(asset(ambiguous)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exactly one");

        ConfigurationAssetDefinition drift = new ConfigurationAssetDefinition(
                UUID.randomUUID(), ConfigurationAssetType.WORKFLOW, "wf", "2.0.0", "1.0.0",
                validDefinition(), Sha256.digest(validDefinition()));
        assertThatThrownBy(() -> parser.parse(drift))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not match");
    }

    @Test
    void resolvesTheOnlyUnconditionalNextTaskFromTheFrozenDefinition() {
        var result = parser.progression(asset(linearDefinition()), "ASSIGN_COORDINATORS", oceanContext());

        assertThat(result.nodeId()).isEqualTo("INITIAL_REVIEW");
        assertThat(result.stageCode()).isEqualTo("INTAKE");
        assertThat(result.taskType()).isEqualTo("INITIAL_REVIEW");
        assertThat(result.taskKind()).isEqualTo(WorkflowTaskKind.AUTOMATED);
        assertThat(result.formRef()).isEqualTo("review.form");
        assertThat(result.slaRef()).isEqualTo("intake.review.sla");
    }

    @Test
    void exclusiveGatewaySelectsTheOnlyTrueBranch() {
        var result = parser.progression(asset(gatewayDefinition()), "SURVEY_TASK", oceanContext());
        assertThat(result.nodeId()).isEqualTo("INSTALL_TASK");
        assertThat(result.taskType()).isEqualTo("INSTALL");
    }

    @Test
    void multiInstanceTaskCarriesCardinality() {
        var result = parser.progression(asset(multiDefinition()), "INTAKE", oceanContext());
        assertThat(result.multiInstance()).isTrue();
        assertThat(result.multiInstanceCardinality()).isEqualTo(3);
        assertThat(result.nodeId()).isEqualTo("MULTI_TASK");
    }

    @Test
    void parallelGatewayForksAndJoinPending() {
        var fork = parser.progression(asset(parallelDefinition()), "INTAKE_TASK", oceanContext());
        assertThat(fork.fork()).isTrue();
        assertThat(fork.forkBranches()).extracting(b -> b.nodeId())
                .containsExactly("BRANCH_A", "BRANCH_B");

        var joinA = parser.progression(asset(parallelDefinition()), "BRANCH_A", oceanContext());
        assertThat(joinA.joinPending()).isTrue();
        assertThat(joinA.nodeId()).isEqualTo("JOIN");
        assertThat(joinA.joinFromNodeId()).isEqualTo("BRANCH_A");
        assertThat(joinA.expectedJoinTokens()).isEqualTo(2);

        var after = parser.progressionAfterJoin(asset(parallelDefinition()), "JOIN", oceanContext());
        assertThat(after.nodeId()).isEqualTo("MERGE_TASK");
    }

    @Test
    void waitEventProgressionReturnsWaitingDefinition() {
        var result = parser.progression(asset(waitDefinition()), "SURVEY_TASK", oceanContext());
        assertThat(result.waiting()).isTrue();
        assertThat(result.nodeId()).isEqualTo("WAIT_ACK");
        assertThat(result.waitEventType()).isEqualTo("demo.client-ack");
        assertThat(result.correlationKeyTemplate()).isEqualTo("workOrder:{workOrderId}");

        var after = parser.progressionAfterWait(asset(waitDefinition()), "WAIT_ACK", oceanContext());
        assertThat(after.nodeId()).isEqualTo("INSTALL_TASK");
        assertThat(after.waiting()).isFalse();
    }

    @Test
    void reviewTaskProgressionIsWaitingGateNotHumanWorkflowTask() {
        var result = parser.progression(asset(reviewGateDefinition()), "INSTALL_TASK", oceanContext());
        assertThat(result.waiting()).isTrue();
        assertThat(result.nodeId()).isEqualTo("REVIEW_TASK");
        assertThat(result.waitEventType()).isEqualTo(ReviewGateWait.WAIT_EVENT_TYPE);
        assertThat(result.correlationKeyTemplate()).isEqualTo(ReviewGateWait.CORRELATION_KEY_TEMPLATE);
        assertThat(result.taskType()).isNull();
        assertThat(result.taskKind()).isNull();

        var after = parser.progressionAfterWait(
                asset(reviewGateDefinition()), "REVIEW_TASK", oceanContext());
        assertThat(after.nodeId()).isEqualTo("WAIT_OEM");
        assertThat(after.waiting()).isTrue();
    }

    @Test
    void exclusiveGatewayZeroAndMultiHitFailClosed() {
        assertThatThrownBy(() -> parser.progression(
                asset(gatewayDefinition("false", "false")), "SURVEY_TASK", oceanContext()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("zero-hit");

        assertThatThrownBy(() -> parser.progression(
                asset(gatewayDefinition("true", "true")), "SURVEY_TASK", oceanContext()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("multi-hit");
    }

    @Test
    void rejectsConditionalAmbiguousAndCrossSemanticShortcuts() {
        String conditional = linearDefinition().replace(
                "\"from\":\"ASSIGN_COORDINATORS\",\"to\":\"INITIAL_REVIEW\"",
                "\"from\":\"ASSIGN_COORDINATORS\",\"to\":\"INITIAL_REVIEW\",\"condition\":\"approved\"");
        assertThatThrownBy(() -> parser.progression(asset(conditional), "ASSIGN_COORDINATORS", oceanContext()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exactly one unconditional");

        String objectCondition = linearDefinition().replace(
                "\"from\":\"ASSIGN_COORDINATORS\",\"to\":\"INITIAL_REVIEW\"",
                "\"from\":\"ASSIGN_COORDINATORS\",\"to\":\"INITIAL_REVIEW\","
                        + "\"condition\":{\"language\":\"SERVICEOS_EXPR_V1\",\"source\":\"true\"}");
        assertThatThrownBy(() -> parser.progression(
                asset(objectCondition), "ASSIGN_COORDINATORS", oceanContext()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exactly one unconditional");

        assertThatThrownBy(() -> parser.progression(asset(validDefinition()), "ASSIGN_COORDINATORS", oceanContext()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exactly one unconditional");
    }

    private static ExpressionContext oceanContext() {
        return new ExpressionContext(
                new ExpressionContext.WorkOrderContext("BYD", "BYD_OCEAN", "HOME_CHARGING_SURVEY_INSTALL"),
                new ExpressionContext.RegionContext("370000", "370100", "370102"),
                new ExpressionContext.TaskContext("SURVEY", "SURVEY"));
    }

    private static ExpressionContext otherBrandContext() {
        return new ExpressionContext(
                new ExpressionContext.WorkOrderContext("OEM2", "OTHER_BRAND", "HOME_CHARGING_SURVEY_INSTALL"),
                new ExpressionContext.RegionContext("370000", "370100", "370102"),
                new ExpressionContext.TaskContext("SURVEY", "SURVEY"));
    }

    private static ConfigurationAssetDefinition asset(String definition) {
        return new ConfigurationAssetDefinition(
                UUID.randomUUID(), ConfigurationAssetType.WORKFLOW, "wf", "1.0.0", "1.0.0",
                definition, Sha256.digest(definition));
    }

    private static String validDefinition() {
        return """
                {"workflowKey":"byd.survey-install","semanticVersion":"1.0.0","startNodeId":"START",
                 "nodes":[
                   {"nodeId":"START","nodeType":"START","name":"开始"},
                   {"nodeId":"ASSIGN_COORDINATORS","nodeType":"SERVICE_TASK","name":"分配跟进人",
                    "stageCode":"INTAKE","taskType":"ASSIGN_COORDINATORS","formRef":"survey.form",
                    "slaRef":"intake.assignment.sla"}],
                 "transitions":[{"transitionId":"t1","from":"START","to":"ASSIGN_COORDINATORS"}]}
                """;
    }

    private static String linearDefinition() {
        return """
                {"workflowKey":"byd.survey-install","semanticVersion":"1.0.0","startNodeId":"START",
                 "nodes":[
                   {"nodeId":"START","nodeType":"START","name":"开始"},
                   {"nodeId":"ASSIGN_COORDINATORS","nodeType":"SERVICE_TASK","name":"分配跟进人",
                    "stageCode":"INTAKE","taskType":"ASSIGN_COORDINATORS"},
                   {"nodeId":"INITIAL_REVIEW","nodeType":"SERVICE_TASK","name":"工单初审",
                    "stageCode":"INTAKE","taskType":"INITIAL_REVIEW","formRef":"review.form",
                    "slaRef":"intake.review.sla"}],
                 "transitions":[
                   {"transitionId":"t1","from":"START","to":"ASSIGN_COORDINATORS"},
                   {"transitionId":"t2","from":"ASSIGN_COORDINATORS","to":"INITIAL_REVIEW"}]}
                """;
    }

    private static String multiDefinition() {
        return """
                {"workflowKey":"multi.demo","semanticVersion":"1.0.0","startNodeId":"START",
                 "nodes":[
                   {"nodeId":"START","nodeType":"START","name":"开始"},
                   {"nodeId":"INTAKE","nodeType":"SERVICE_TASK","name":"受理",
                    "stageCode":"INTAKE","taskType":"ASSIGN_COORDINATORS"},
                   {"nodeId":"MULTI_TASK","nodeType":"SERVICE_TASK","name":"多实例",
                    "stageCode":"WORK","taskType":"MULTI_WORK",
                    "multiInstance":{"cardinality":3}},
                   {"nodeId":"AFTER","nodeType":"SERVICE_TASK","name":"之后",
                    "stageCode":"CLOSE","taskType":"AFTER"}],
                 "transitions":[
                   {"transitionId":"t1","from":"START","to":"INTAKE"},
                   {"transitionId":"t2","from":"INTAKE","to":"MULTI_TASK"},
                   {"transitionId":"t3","from":"MULTI_TASK","to":"AFTER"}]}
                """;
    }

    private static String parallelDefinition() {
        return """
                {"workflowKey":"parallel.demo","semanticVersion":"1.0.0","startNodeId":"START",
                 "nodes":[
                   {"nodeId":"START","nodeType":"START","name":"开始"},
                   {"nodeId":"INTAKE_TASK","nodeType":"SERVICE_TASK","name":"受理",
                    "stageCode":"INTAKE","taskType":"ASSIGN_COORDINATORS"},
                   {"nodeId":"FORK","nodeType":"PARALLEL_GATEWAY","name":"分叉"},
                   {"nodeId":"BRANCH_A","nodeType":"SERVICE_TASK","name":"分支A",
                    "stageCode":"PARALLEL","taskType":"BRANCH_A"},
                   {"nodeId":"BRANCH_B","nodeType":"SERVICE_TASK","name":"分支B",
                    "stageCode":"PARALLEL","taskType":"BRANCH_B"},
                   {"nodeId":"JOIN","nodeType":"PARALLEL_GATEWAY","name":"汇聚"},
                   {"nodeId":"MERGE_TASK","nodeType":"SERVICE_TASK","name":"汇合后",
                    "stageCode":"CLOSE","taskType":"MERGE"}],
                 "transitions":[
                   {"transitionId":"t1","from":"START","to":"INTAKE_TASK"},
                   {"transitionId":"t2","from":"INTAKE_TASK","to":"FORK"},
                   {"transitionId":"t3","from":"FORK","to":"BRANCH_A"},
                   {"transitionId":"t4","from":"FORK","to":"BRANCH_B"},
                   {"transitionId":"t5","from":"BRANCH_A","to":"JOIN"},
                   {"transitionId":"t6","from":"BRANCH_B","to":"JOIN"},
                   {"transitionId":"t7","from":"JOIN","to":"MERGE_TASK"}]}
                """;
    }

    private static String waitDefinition() {
        return """
                {"workflowKey":"wait.demo","semanticVersion":"1.0.0","startNodeId":"START",
                 "nodes":[
                   {"nodeId":"START","nodeType":"START","name":"开始"},
                   {"nodeId":"SURVEY_TASK","nodeType":"SERVICE_TASK","name":"勘测",
                    "stageCode":"SURVEY","taskType":"SURVEY"},
                   {"nodeId":"WAIT_ACK","nodeType":"WAIT_EVENT","name":"等待确认",
                    "stageCode":"SURVEY","waitEventType":"demo.client-ack",
                    "correlationKeyTemplate":"workOrder:{workOrderId}"},
                   {"nodeId":"INSTALL_TASK","nodeType":"SERVICE_TASK","name":"安装",
                    "stageCode":"INSTALL","taskType":"INSTALL"}],
                 "transitions":[
                   {"transitionId":"t1","from":"START","to":"SURVEY_TASK"},
                   {"transitionId":"t2","from":"SURVEY_TASK","to":"WAIT_ACK"},
                   {"transitionId":"t3","from":"WAIT_ACK","to":"INSTALL_TASK"}]}
                """;
    }

    private static String reviewGateDefinition() {
        return """
                {"workflowKey":"review.gate.demo","semanticVersion":"1.0.0","startNodeId":"START",
                 "nodes":[
                   {"nodeId":"START","nodeType":"START","name":"开始"},
                   {"nodeId":"INSTALL_TASK","nodeType":"SERVICE_TASK","name":"安装",
                    "stageCode":"INSTALL","taskType":"FIELD_INSTALL"},
                   {"nodeId":"REVIEW_TASK","nodeType":"REVIEW_TASK","name":"资料审核",
                    "stageCode":"REVIEW","taskType":"evidence.review"},
                   {"nodeId":"WAIT_OEM","nodeType":"WAIT_EVENT","name":"等待车企",
                    "stageCode":"HANDOFF","waitEventType":"platform.oem.acknowledged",
                    "correlationKeyTemplate":"workOrder:{workOrderId}"}],
                 "transitions":[
                   {"transitionId":"t1","from":"START","to":"INSTALL_TASK"},
                   {"transitionId":"t2","from":"INSTALL_TASK","to":"REVIEW_TASK"},
                   {"transitionId":"t3","from":"REVIEW_TASK","to":"WAIT_OEM"}]}
                """;
    }

    private static String gatewayDefinition() {
        return gatewayDefinition(
                "workOrder.brandCode == \\\"BYD_OCEAN\\\"",
                "workOrder.brandCode != \\\"BYD_OCEAN\\\"");
    }

    private static String gatewayDefinition(String installCondition, String skipCondition) {
        return ("""
                {"workflowKey":"gateway.demo","semanticVersion":"1.0.0","startNodeId":"START",
                 "nodes":[
                   {"nodeId":"START","nodeType":"START","name":"开始"},
                   {"nodeId":"SURVEY_TASK","nodeType":"SERVICE_TASK","name":"勘测",
                    "stageCode":"SURVEY","taskType":"SURVEY"},
                   {"nodeId":"GW","nodeType":"EXCLUSIVE_GATEWAY","name":"是否安装"},
                   {"nodeId":"INSTALL_TASK","nodeType":"SERVICE_TASK","name":"安装",
                    "stageCode":"INSTALL","taskType":"INSTALL"},
                   {"nodeId":"SKIP_TASK","nodeType":"SERVICE_TASK","name":"跳过",
                    "stageCode":"CLOSE","taskType":"SKIP"},
                   {"nodeId":"END","nodeType":"END","name":"结束"}],
                 "transitions":[
                   {"transitionId":"t1","from":"START","to":"SURVEY_TASK"},
                   {"transitionId":"t2","from":"SURVEY_TASK","to":"GW"},
                   {"transitionId":"t3","from":"GW","to":"INSTALL_TASK","priority":10,
                    "condition":{"language":"SERVICEOS_EXPR_V1","source":"%s"}},
                   {"transitionId":"t4","from":"GW","to":"SKIP_TASK","priority":20,
                    "condition":{"language":"SERVICEOS_EXPR_V1","source":"%s"}},
                   {"transitionId":"t5","from":"INSTALL_TASK","to":"END"},
                   {"transitionId":"t6","from":"SKIP_TASK","to":"END"}]}
                """).formatted(installCondition, skipCondition);
    }

    /** 测试替身：支持 true/false 字面量与 brandCode 等值比较。 */
    private static final class BrandAwareEvaluator implements ExpressionEvaluator {
        @Override
        public ExpressionEvaluation evaluate(ExpressionDefinition expression, ExpressionContext context) {
            String source = expression.source().trim();
            boolean result = switch (source) {
                case "true" -> true;
                case "false" -> false;
                default -> {
                    if (source.equals("workOrder.brandCode == \"BYD_OCEAN\"")) {
                        yield "BYD_OCEAN".equals(context.workOrder().brandCode());
                    }
                    if (source.equals("workOrder.brandCode != \"BYD_OCEAN\"")) {
                        yield !"BYD_OCEAN".equals(context.workOrder().brandCode());
                    }
                    throw new IllegalArgumentException("unsupported test expression: " + source);
                }
            };
            return new ExpressionEvaluation(result, Map.of(), expression);
        }
    }
}
