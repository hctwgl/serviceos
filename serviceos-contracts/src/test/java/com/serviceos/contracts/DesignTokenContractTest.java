package com.serviceos.contracts;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 共享视觉基础必须保持为跨客户端机器契约：只表达视觉语义，不夹带 Portal、角色或数据权限假设。
 */
class DesignTokenContractTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void designTokenSourceMustRemainVersionedPlatformNeutralAndComplete() throws Exception {
        Path tokenPath = Path.of("src/main/resources/design-tokens/serviceos-design-tokens-v1.json");
        JsonNode root = objectMapper.readTree(tokenPath.toFile());

        assertThat(root.path("schemaVersion").asInt()).isEqualTo(1);
        assertThat(root.properties().stream().map(java.util.Map.Entry::getKey).collect(java.util.stream.Collectors.toSet()))
                .containsExactlyInAnyOrder("schemaVersion", "color", "spacing", "radius", "typography", "shadow");

        for (String category : Set.of("color", "spacing", "radius", "typography", "shadow")) {
            assertThat(root.path(category).isObject()).as(category).isTrue();
            assertThat(root.path(category).isEmpty()).as(category).isFalse();
        }

        assertThat(root.path("color").path("actionPrimary").asText()).isEqualTo("#243B53");
        assertThat(root.path("spacing").path("lg").asInt()).isEqualTo(16);
        assertThat(root.toString())
                .doesNotContain("ADMIN")
                .doesNotContain("NETWORK")
                .doesNotContain("TECHNICIAN")
                .doesNotContain("CONSUMER");
    }
}
