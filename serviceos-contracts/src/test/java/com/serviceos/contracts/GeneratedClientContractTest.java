package com.serviceos.contracts;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 Maven 生命周期确实产出约定的 Portal 客户端，而不是只声明了一个未执行的生成插件。
 */
class GeneratedClientContractTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void pinnedGeneratorMustProduceTheExpectedTypescriptClientSurface() throws Exception {
        Path clientDirectory = Path.of("target/generated-clients/typescript-fetch");
        Path packageJson = clientDirectory.resolve("package.json");
        Path generatorVersion = clientDirectory.resolve(".openapi-generator/VERSION");
        Path defaultApi = clientDirectory.resolve("src/apis/DefaultApi.ts");

        assertThat(packageJson).exists();
        assertThat(generatorVersion).hasContent("7.22.0");

        JsonNode packageNode = objectMapper.readTree(packageJson.toFile());
        assertThat(packageNode.path("name").asText()).isEqualTo("@serviceos/core-client");
        assertThat(packageNode.path("version").asText()).isEqualTo("0.4.0");

        String apiSource = Files.readString(defaultApi);
        assertThat(apiSource)
                .contains("authorizeFileDownload")
                .contains("assignTaskCandidates")
                .contains("beginFileUpload")
                .contains("claimHumanTask")
                .contains("completeHumanTask")
                .contains("createProject")
                .contains("finalizeFileUpload")
                .contains("releaseHumanTask")
                .contains("startHumanTask")
                .contains("recordTaskContactAttempt")
                .contains("cancelAppointment")
                .contains("markAppointmentNoShow");
    }
}
