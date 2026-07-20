package com.serviceos.configuration.infrastructure;

import com.serviceos.configuration.api.ConfigurationAssetDefinition;
import com.serviceos.configuration.api.ConfigurationAssetType;
import com.serviceos.configuration.api.ConfigurationPublicationException;
import com.serviceos.configuration.api.PublishConfigurationAssetCommand;
import com.serviceos.shared.Sha256;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

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
    void parallelGatewayForkAndJoinShapesValidated() {
        String valid = """
                {"workflowKey":"parallel.demo","semanticVersion":"1.0.0","startNodeId":"START",
                 "nodes":[
                   {"nodeId":"START","nodeType":"START","name":"开始"},
                   {"nodeId":"A","nodeType":"SERVICE_TASK","name":"A","stageCode":"S1","taskType":"TA"},
                   {"nodeId":"FORK","nodeType":"PARALLEL_GATEWAY","name":"fork"},
                   {"nodeId":"B","nodeType":"SERVICE_TASK","name":"B","stageCode":"S2","taskType":"TB"},
                   {"nodeId":"C","nodeType":"SERVICE_TASK","name":"C","stageCode":"S2","taskType":"TC"},
                   {"nodeId":"JOIN","nodeType":"PARALLEL_GATEWAY","name":"join"},
                   {"nodeId":"D","nodeType":"SERVICE_TASK","name":"D","stageCode":"S3","taskType":"TD"}],
                 "transitions":[
                   {"transitionId":"t1","from":"START","to":"A"},
                   {"transitionId":"t2","from":"A","to":"FORK"},
                   {"transitionId":"t3","from":"FORK","to":"B"},
                   {"transitionId":"t4","from":"FORK","to":"C"},
                   {"transitionId":"t5","from":"B","to":"JOIN"},
                   {"transitionId":"t6","from":"C","to":"JOIN"},
                   {"transitionId":"t7","from":"JOIN","to":"D"}]}
                """.trim();
        assertThatCode(() -> validator.validate(command(valid))).doesNotThrowAnyException();

        String badFork = valid.replace(
                "{\"transitionId\":\"t4\",\"from\":\"FORK\",\"to\":\"C\"}",
                "{\"transitionId\":\"t4\",\"from\":\"FORK\",\"to\":\"C\","
                        + "\"condition\":{\"language\":\"SERVICEOS_EXPR_V1\",\"source\":\"true\"}}");
        assertThatThrownBy(() -> validator.validate(command(badFork)))
                .isInstanceOf(ConfigurationPublicationException.class)
                .hasMessageContaining("PARALLEL fork");
    }

    @Test
    void waitEventRequiresTypeTemplateAndUnconditionalExit() {
        String valid = """
                {"workflowKey":"wait.demo","semanticVersion":"1.0.0","startNodeId":"START",
                 "nodes":[
                   {"nodeId":"START","nodeType":"START","name":"开始"},
                   {"nodeId":"TASK","nodeType":"USER_TASK","name":"任务","stageCode":"S1","taskType":"T1"},
                   {"nodeId":"WAIT","nodeType":"WAIT_EVENT","name":"等待",
                    "stageCode":"S1","waitEventType":"demo.ack",
                    "correlationKeyTemplate":"workOrder:{workOrderId}"},
                   {"nodeId":"END","nodeType":"END","name":"结束"}],
                 "transitions":[
                   {"transitionId":"t1","from":"START","to":"TASK"},
                   {"transitionId":"t2","from":"TASK","to":"WAIT"},
                   {"transitionId":"t3","from":"WAIT","to":"END"}]}
                """.trim();
        assertThatCode(() -> validator.validate(command(valid))).doesNotThrowAnyException();

        String missingType = valid.replace("\"waitEventType\":\"demo.ack\",", "");
        assertThatThrownBy(() -> validator.validate(command(missingType)))
                .isInstanceOf(ConfigurationPublicationException.class)
                .hasMessageContaining("waitEventType");
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

    @Test
    void calendarAssetPublishesWithValidTimezoneAndWindows() {
        String definition = sampleCalendar("Asia/Shanghai");
        assertThatCode(() -> validator.validate(assetCommand(
                ConfigurationAssetType.CALENDAR, "cn.workdays.sample", definition)))
                .doesNotThrowAnyException();
    }

    @Test
    void calendarRejectsInvalidTimezone() {
        String definition = sampleCalendar("Not/AZone");
        assertThatThrownBy(() -> validator.validate(assetCommand(
                ConfigurationAssetType.CALENDAR, "cn.workdays.sample", definition)))
                .isInstanceOf(ConfigurationPublicationException.class)
                .hasMessageContaining("timeZone");
    }

    @Test
    void calendarRejectsWindowEndNotAfterStart() {
        String definition = """
                {"calendarKey":"cn.workdays.sample","version":"1.0.0","timeZone":"Asia/Shanghai",
                 "weeklyWindows":[{"dayOfWeek":"MONDAY","start":"18:00","end":"09:00"}]}
                """.trim();
        assertThatThrownBy(() -> validator.validate(assetCommand(
                ConfigurationAssetType.CALENDAR, "cn.workdays.sample", definition)))
                .isInstanceOf(ConfigurationPublicationException.class)
                .hasMessageContaining("end");
    }

    @Test
    void businessSlaRequiresCalendarRefInSchema() {
        String definition = """
                {"policyKey":"survey.response.sla","version":"1.0.0","subjectType":"TASK",
                 "taskTypes":["SURVEY_RESPONSE"],"startEvent":"TASK_CREATED",
                 "stopEvent":"TASK_COMPLETED","clockMode":"BUSINESS","targetDurationSeconds":3600}
                """.trim();
        assertThatThrownBy(() -> validator.validate(assetCommand(
                ConfigurationAssetType.SLA, "survey.response.sla", definition)))
                .isInstanceOf(ConfigurationPublicationException.class);
    }

    @Test
    void elapsedSlaRejectsCalendarRefInSchema() {
        String definition = """
                {"policyKey":"survey.response.sla","version":"1.0.0","subjectType":"TASK",
                 "taskTypes":["SURVEY_RESPONSE"],"startEvent":"TASK_CREATED",
                 "stopEvent":"TASK_COMPLETED","clockMode":"ELAPSED","targetDurationSeconds":3600,
                 "calendarRef":"cn.workdays.sample"}
                """.trim();
        assertThatThrownBy(() -> validator.validate(assetCommand(
                ConfigurationAssetType.SLA, "survey.response.sla", definition)))
                .isInstanceOf(ConfigurationPublicationException.class);
    }

    @Test
    void businessSlaBundleRequiresExactCalendarRef() {
        String calendar = sampleCalendar("Asia/Shanghai");
        String sla = """
                {"policyKey":"survey.response.sla","version":"1.0.0","subjectType":"TASK",
                 "taskTypes":["SURVEY_RESPONSE"],"startEvent":"TASK_CREATED",
                 "stopEvent":"TASK_COMPLETED","clockMode":"BUSINESS","targetDurationSeconds":3600,
                 "calendarRef":"missing.calendar"}
                """.trim();
        assertThatThrownBy(() -> validator.validateBundle(List.of(
                assetDefinition(ConfigurationAssetType.CALENDAR, "cn.workdays.sample", calendar),
                assetDefinition(ConfigurationAssetType.SLA, "survey.response.sla", sla))))
                .isInstanceOf(ConfigurationPublicationException.class)
                .hasMessageContaining("calendarRef");
    }

    private static PublishConfigurationAssetCommand command(String definition) {
        return new PublishConfigurationAssetCommand(
                "tenant-test", ConfigurationAssetType.WORKFLOW, "demo", "1.0.0", "1.0.0",
                definition, Sha256.digest(definition));
    }

    private static PublishConfigurationAssetCommand assetCommand(
            ConfigurationAssetType type, String key, String definition
    ) {
        return new PublishConfigurationAssetCommand(
                "tenant-test", type, key, "1.0.0", "1.0.0", definition, Sha256.digest(definition));
    }

    private static ConfigurationAssetDefinition assetDefinition(
            ConfigurationAssetType type, String key, String definition
    ) {
        return new ConfigurationAssetDefinition(
                UUID.randomUUID(), type, key, "1.0.0", "1.0.0", definition, Sha256.digest(definition));
    }

    private static String sampleCalendar(String timeZone) {
        return """
                {"calendarKey":"cn.workdays.sample","version":"1.0.0","timeZone":"%s",
                 "weeklyWindows":[
                   {"dayOfWeek":"MONDAY","start":"09:00","end":"18:00"},
                   {"dayOfWeek":"TUESDAY","start":"09:00","end":"18:00"},
                   {"dayOfWeek":"WEDNESDAY","start":"09:00","end":"18:00"},
                   {"dayOfWeek":"THURSDAY","start":"09:00","end":"18:00"},
                   {"dayOfWeek":"FRIDAY","start":"09:00","end":"18:00"}]}
                """.formatted(timeZone).trim();
    }
}
