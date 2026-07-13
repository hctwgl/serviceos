package com.serviceos.contracts;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 事件契约发布治理门禁。
 *
 * <p>跨 Git 版本的“已发布文件不可修改”由 check-contract-compatibility.sh 负责；本测试负责保证
 * 新增版本自身的文件名、$id、schemaVersion、eventType 和有效样本彼此一致，避免通过新增文件
 * 绕开版本语义。</p>
 */
class EventSchemaGovernanceTest {
    private static final Pattern VERSIONED_SCHEMA =
            Pattern.compile("^(.+)-v([1-9][0-9]*)\\.schema\\.json$");
    private static final Pattern EVENT_TYPE =
            Pattern.compile("^[a-z][a-z0-9]*(?:\\.[a-z][a-z0-9-]*)+$");

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void everyPublishedEventVersionMustBeSelfConsistentAndHaveAValidSample() throws Exception {
        Path schemaDirectory = Path.of("src/main/resources/events");
        Path sampleDirectory = Path.of("src/test/resources/events");
        List<Path> schemas;
        try (var files = Files.list(schemaDirectory)) {
            schemas = files
                    .filter(path -> path.getFileName().toString().endsWith(".schema.json"))
                    .sorted()
                    .toList();
        }

        assertThat(schemas).as("published event schemas").isNotEmpty();
        Set<String> publishedIdentities = new HashSet<>();
        var schemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);

        for (Path schemaPath : schemas) {
            String fileName = schemaPath.getFileName().toString();
            Matcher matcher = VERSIONED_SCHEMA.matcher(fileName);
            assertThat(matcher.matches())
                    .as("versioned event schema filename: %s", fileName)
                    .isTrue();

            int version = Integer.parseInt(matcher.group(2));
            JsonNode schemaNode = objectMapper.readTree(schemaPath.toFile());
            String eventType = schemaNode.path("properties").path("eventType").path("const").asText();
            int declaredVersion = schemaNode.path("properties").path("schemaVersion").path("const").asInt(-1);

            assertThat(schemaNode.path("$schema").asText())
                    .isEqualTo("https://json-schema.org/draft/2020-12/schema");
            assertThat(schemaNode.path("$id").asText()).endsWith("/" + fileName);
            assertThat(schemaNode.path("title").asText()).endsWith("V" + version);
            assertThat(schemaNode.path("additionalProperties").asBoolean(true)).isFalse();
            assertThat(declaredVersion).isEqualTo(version);
            assertThat(eventType).matches(EVENT_TYPE);
            assertThat(publishedIdentities.add(eventType + "@v" + version))
                    .as("unique event type and version: %s@v%s", eventType, version)
                    .isTrue();

            String sampleName = fileName.replace(".schema.json", ".valid.json");
            Path samplePath = sampleDirectory.resolve(sampleName);
            assertThat(samplePath).as("valid sample for %s", fileName).exists();

            JsonNode sampleNode = objectMapper.readTree(samplePath.toFile());
            assertThat(sampleNode.path("eventType").asText()).isEqualTo(eventType);
            assertThat(sampleNode.path("schemaVersion").asInt(-1)).isEqualTo(version);
            assertThat(schemaFactory.getSchema(schemaNode).validate(sampleNode))
                    .as("schema violations for %s", sampleName)
                    .isEmpty();
        }
    }
}
