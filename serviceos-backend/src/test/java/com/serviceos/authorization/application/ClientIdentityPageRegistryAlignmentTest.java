package com.serviceos.authorization.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

/** 后端代码注册的 pageId 必须与跨端机器注册表完全一致，防止导航与客户端深链身份漂移。 */
class ClientIdentityPageRegistryAlignmentTest {
    @Test
    void codePageRegistryMustMatchSharedMachineRegistry() throws Exception {
        JsonNode root = new ObjectMapper().readTree(Path.of(
                "../serviceos-contracts/src/main/resources/client-identities/serviceos-client-identities-v1.json")
                .toFile());
        Set<String> registered = StreamSupport.stream(root.path("pageIds").spliterator(), false)
                .map(JsonNode::asText)
                .collect(Collectors.toSet());

        Set<String> backend = new CodePageRegistry().all().stream()
                .map(RegisteredPage::pageId)
                .collect(Collectors.toSet());
        assertThat(backend).containsExactlyInAnyOrderElementsOf(registered);
        assertThat(backend).hasSize(root.path("pageIds").size());
    }
}
