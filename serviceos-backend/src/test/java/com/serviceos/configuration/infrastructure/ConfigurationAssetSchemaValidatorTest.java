package com.serviceos.configuration.infrastructure;

import com.serviceos.configuration.api.ConfigurationAssetType;
import com.serviceos.configuration.api.ConfigurationPublicationException;
import com.serviceos.configuration.api.PublishConfigurationAssetCommand;
import com.serviceos.shared.Sha256;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfigurationAssetSchemaValidatorTest {
    private final ConfigurationAssetSchemaValidator validator = new ConfigurationAssetSchemaValidator();

    @Test
    void linearWorkflowWithoutConditionsPublishes() {
        String definition = """
                {"workflowKey":"linear.demo","semanticVersion":"1.0.0","startNodeId":"START",
                 "nodes":[
                   {"nodeId":"START","nodeType":"START","name":"开始"},
                   {"nodeId":"TASK","nodeType":"USER_TASK","name":"任务","stageCode":"S1","taskType":"T1"},
                   {"nodeId":"END","nodeType":"END","name":"结束"}],
                 "transitions":[
                   {"transitionId":"t1","from":"START","to":"TASK"},
                   {"transitionId":"t2","from":"TASK","to":"END"}]}
                """.trim();
        assertThatCode(() -> validator.validate(command(definition))).doesNotThrowAnyException();
    }

    @Test
    void exclusiveGatewayRequiresConditionalOutgoingEdges() {
        String definition = """
                {"workflowKey":"gateway.demo","semanticVersion":"1.0.0","startNodeId":"START",
                 "nodes":[
                   {"nodeId":"START","nodeType":"START","name":"开始"},
                   {"nodeId":"GW","nodeType":"EXCLUSIVE_GATEWAY","name":"网关"},
                   {"nodeId":"A","nodeType":"USER_TASK","name":"A","stageCode":"S1","taskType":"TA"},
                   {"nodeId":"B","nodeType":"USER_TASK","name":"B","stageCode":"S1","taskType":"TB"},
                   {"nodeId":"END","nodeType":"END","name":"结束"}],
                 "transitions":[
                   {"transitionId":"t1","from":"START","to":"GW"},
                   {"transitionId":"t2","from":"GW","to":"A",
                    "condition":{"language":"SERVICEOS_EXPR_V1","source":"true"}},
                   {"transitionId":"t3","from":"GW","to":"B",
                    "condition":{"language":"SERVICEOS_EXPR_V1","source":"false"}},
                   {"transitionId":"t4","from":"A","to":"END"},
                   {"transitionId":"t5","from":"B","to":"END"}]}
                """.trim();
        assertThatCode(() -> validator.validate(command(definition))).doesNotThrowAnyException();
    }

    @Test
    void exclusiveGatewayWithoutConditionFailsClosed() {
        String definition = """
                {"workflowKey":"gateway.bad","semanticVersion":"1.0.0","startNodeId":"START",
                 "nodes":[
                   {"nodeId":"START","nodeType":"START","name":"开始"},
                   {"nodeId":"GW","nodeType":"EXCLUSIVE_GATEWAY","name":"网关"},
                   {"nodeId":"A","nodeType":"USER_TASK","name":"A","stageCode":"S1","taskType":"TA"},
                   {"nodeId":"B","nodeType":"USER_TASK","name":"B","stageCode":"S1","taskType":"TB"}],
                 "transitions":[
                   {"transitionId":"t1","from":"START","to":"GW"},
                   {"transitionId":"t2","from":"GW","to":"A"},
                   {"transitionId":"t3","from":"GW","to":"B",
                    "condition":{"language":"SERVICEOS_EXPR_V1","source":"true"}}]}
                """.trim();
        assertThatThrownBy(() -> validator.validate(command(definition)))
                .isInstanceOf(ConfigurationPublicationException.class)
                .hasMessageContaining("EXCLUSIVE_GATEWAY 出边必须带 condition");
    }

    @Test
    void stringConditionFailsClosed() {
        String definition = """
                {"workflowKey":"string.condition","semanticVersion":"1.0.0","startNodeId":"START",
                 "nodes":[
                   {"nodeId":"START","nodeType":"START","name":"开始"},
                   {"nodeId":"TASK","nodeType":"USER_TASK","name":"任务","stageCode":"S1","taskType":"T1"},
                   {"nodeId":"END","nodeType":"END","name":"结束"}],
                 "transitions":[
                   {"transitionId":"t1","from":"START","to":"TASK","condition":"legacy-string"},
                   {"transitionId":"t2","from":"TASK","to":"END"}]}
                """.trim();
        assertThatThrownBy(() -> validator.validate(command(definition)))
                .isInstanceOf(ConfigurationPublicationException.class)
                .hasMessageContaining("SERVICEOS_EXPR_V1 对象");
    }

    private static PublishConfigurationAssetCommand command(String definition) {
        return new PublishConfigurationAssetCommand(
                "tenant-test", ConfigurationAssetType.WORKFLOW, "demo", "1.0.0", "1.0.0",
                definition, Sha256.digest(definition));
    }
}
