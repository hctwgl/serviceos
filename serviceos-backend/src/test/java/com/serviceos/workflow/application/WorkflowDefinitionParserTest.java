package com.serviceos.workflow.application;

import com.serviceos.configuration.api.ConfigurationAssetDefinition;
import com.serviceos.configuration.api.ConfigurationAssetType;
import com.serviceos.shared.Sha256;
import com.serviceos.task.api.WorkflowTaskKind;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkflowDefinitionParserTest {
    private final WorkflowDefinitionParser parser = new WorkflowDefinitionParser(new ObjectMapper());

    @Test
    void freezesTheFirstExecutableTaskFromTheExactAsset() {
        var result = parser.parse(asset(validDefinition()));

        assertThat(result.workflowKey()).isEqualTo("byd.survey-install");
        assertThat(result.workflowVersion()).isEqualTo("1.0.0");
        assertThat(result.firstNodeId()).isEqualTo("ASSIGN_COORDINATORS");
        assertThat(result.firstStageCode()).isEqualTo("INTAKE");
        assertThat(result.firstTaskKind()).isEqualTo(WorkflowTaskKind.AUTOMATED);
        assertThat(result.firstFormRef()).isEqualTo("survey.form");
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
        var result = parser.progression(asset(linearDefinition()), "ASSIGN_COORDINATORS");

        assertThat(result.nodeId()).isEqualTo("INITIAL_REVIEW");
        assertThat(result.stageCode()).isEqualTo("INTAKE");
        assertThat(result.taskType()).isEqualTo("INITIAL_REVIEW");
        assertThat(result.taskKind()).isEqualTo(WorkflowTaskKind.AUTOMATED);
        assertThat(result.formRef()).isEqualTo("review.form");
    }

    @Test
    void rejectsConditionalAmbiguousAndCrossSemanticShortcuts() {
        String conditional = linearDefinition().replace(
                "\"from\":\"ASSIGN_COORDINATORS\",\"to\":\"INITIAL_REVIEW\"",
                "\"from\":\"ASSIGN_COORDINATORS\",\"to\":\"INITIAL_REVIEW\",\"condition\":\"approved\"");
        assertThatThrownBy(() -> parser.progression(asset(conditional), "ASSIGN_COORDINATORS"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exactly one unconditional");

        assertThatThrownBy(() -> parser.progression(asset(validDefinition()), "ASSIGN_COORDINATORS"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exactly one unconditional");
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
                    "stageCode":"INTAKE","taskType":"ASSIGN_COORDINATORS","formRef":"survey.form"}],
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
                    "stageCode":"INTAKE","taskType":"INITIAL_REVIEW","formRef":"review.form"}],
                 "transitions":[
                   {"transitionId":"t1","from":"START","to":"ASSIGN_COORDINATORS"},
                   {"transitionId":"t2","from":"ASSIGN_COORDINATORS","to":"INITIAL_REVIEW"}]}
                """;
    }
}
