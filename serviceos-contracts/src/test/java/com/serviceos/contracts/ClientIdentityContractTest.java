package com.serviceos.contracts;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Page、Feature 与 Action 身份属于客户端间共享的机器契约；新增值必须显式注册，不能由客户端猜测。
 */
class ClientIdentityContractTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void registryMustBeVersionedUniqueAndCoverPublishedActionEnums() throws Exception {
        JsonNode root = objectMapper.readTree(Path.of(
                "src/main/resources/client-identities/serviceos-client-identities-v1.json").toFile());

        assertThat(root.path("schemaVersion").asInt()).isEqualTo(1);
        assertThat(root.path("catalogVersion").asText()).isEqualTo("client-identities-v1");
        assertUnique(root.path("pageIds"));
        assertUnique(root.path("actionCodes"));

        Set<String> actionCodes = values(root.path("actionCodes"));
        assertThat(actionCodes).containsExactlyInAnyOrder(
                "task.claim", "task.start", "task.complete", "task.release",
                "CONFIRM", "RESCHEDULE", "CANCEL", "MARK_NO_SHOW",
                "visit.checkOut", "visit.interrupt", "ACKNOWLEDGE");
        assertThat(actionCodes).containsAll(publishedActionEnums());

        JsonNode formalSettlement = root.path("featureIds").get(0);
        assertThat(formalSettlement.path("id").asText()).isEqualTo("FORMAL_SETTLEMENT");
        assertThat(formalSettlement.path("defaultState").asText()).isEqualTo("DISABLED");
        assertThat(formalSettlement.path("lifecycle").asText()).isEqualTo("RESERVED");
    }

    private static void assertUnique(JsonNode array) {
        assertThat(array.isArray()).isTrue();
        assertThat(values(array)).hasSize(array.size());
    }

    private static Set<String> values(JsonNode array) {
        Set<String> values = new HashSet<>();
        StreamSupport.stream(array.spliterator(), false).map(JsonNode::asText).forEach(values::add);
        return values;
    }

    private static Set<String> publishedActionEnums() {
        try {
            JsonNode schemas = new ObjectMapper(new YAMLFactory()).readTree(Path.of(
                    "src/main/resources/openapi/serviceos-core-v1.yaml").toFile())
                    .path("components").path("schemas");
            Set<String> values = new HashSet<>();
            values.addAll(values(schemas.path("TaskAllowedAction").path("properties").path("code").path("enum")));
            for (String schema : java.util.List.of("Appointment", "Visit", "OperationalExceptionItem")) {
                values.addAll(values(schemas.path(schema).path("properties")
                        .path("allowedActions").path("items").path("enum")));
            }
            return values;
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("无法读取 Core OpenAPI action enum", exception);
        }
    }
}
