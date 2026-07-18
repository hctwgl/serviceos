package com.serviceos.contracts;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

/** 客户端类型/版本是可观测性契约，不得被描述为授权输入，也不得接受无界客户端类型。 */
class ClientMetadataContractTest {
    @Test
    void openApiMustDeclareBoundedNonAuthorizationClientMetadata() throws Exception {
        JsonNode root = new ObjectMapper(new YAMLFactory()).readTree(Path.of(
                "src/main/resources/openapi/serviceos-core-v1.yaml").toFile());
        JsonNode metadata = root.path("x-serviceos-client-metadata");

        assertThat(metadata.path("requiredForNewClients").asBoolean()).isTrue();
        assertThat(metadata.path("authorizationInput").asBoolean()).isFalse();
        Set<String> kinds = StreamSupport.stream(
                        metadata.path("headers").path("kind").path("enum").spliterator(), false)
                .map(JsonNode::asText)
                .collect(Collectors.toSet());
        assertThat(kinds).containsExactlyInAnyOrder(
                "ADMIN_WEB", "NETWORK_WEB", "TECHNICIAN_WEB", "TECHNICIAN_IOS");
        assertThat(metadata.path("headers").path("version").path("pattern").asText()).isNotBlank();
    }
}
