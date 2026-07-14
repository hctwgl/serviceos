package com.serviceos.contracts;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 机器可读契约的最低 CI 门禁：OpenAPI 必须可解析，事件样本必须通过已发布 Schema。
 */
class ContractValidationTest {
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void openApiMustParseWithoutErrors() throws Exception {
        String yaml = resourceText("/openapi/serviceos-core-v1.yaml");
        SwaggerParseResult result = new OpenAPIV3Parser().readContents(yaml, null, null);

        assertThat(result.getMessages()).as("OpenAPI parser messages").isEmpty();
        assertThat(result.getOpenAPI()).isNotNull();
        assertThat(result.getOpenAPI().getPaths())
                .containsKeys("/projects", "/files/upload-sessions",
                        "/tasks/{taskId}:claim", "/tasks/{taskId}:start", "/tasks/{taskId}:complete");
    }

    @Test
    void writeApiMustUseBearerIdentityAndMustNotAcceptSpoofableActorHeaders() throws Exception {
        String yaml = resourceText("/openapi/serviceos-core-v1.yaml");
        SwaggerParseResult result = new OpenAPIV3Parser().readContents(yaml, null, null);
        var openApi = result.getOpenAPI();
        var operation = openApi.getPaths().get("/projects").getPost();

        assertThat(openApi.getComponents().getSecuritySchemes()).containsKey("bearerAuth");
        assertThat(operation.getParameters())
                .extracting(parameter -> parameter.getName())
                .doesNotContain("X-Tenant-Id", "X-Actor-Id");
    }

    @Test
    void projectCreatedExampleMustMatchPublishedSchema() throws Exception {
        assertValidEvent(
                "/events/project-created-v1.schema.json",
                "/events/project-created-v1.valid.json");
    }

    @Test
    void taskManualInterventionExampleMustMatchPublishedSchema() throws Exception {
        assertValidEvent(
                "/events/task-manual-intervention-required-v1.schema.json",
                "/events/task-manual-intervention-required-v1.valid.json");
    }

    @Test
    void fileScanCompletedExampleMustMatchPublishedSchema() throws Exception {
        assertValidEvent(
                "/events/file-scan-completed-v1.schema.json",
                "/events/file-scan-completed-v1.valid.json");
    }

    @Test
    void taskCompletedExampleMustMatchPublishedSchema() throws Exception {
        assertValidEvent(
                "/events/task-completed-v1.schema.json",
                "/events/task-completed-v1.valid.json");
    }

    private void assertValidEvent(String schemaPath, String eventPath) throws Exception {
        JsonNode schemaNode = objectMapper.readTree(resource(schemaPath));
        JsonNode eventNode = objectMapper.readTree(resource(eventPath));
        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
        JsonSchema schema = factory.getSchema(schemaNode);

        Set<ValidationMessage> errors = schema.validate(eventNode);

        assertThat(errors).as("event schema violations").isEmpty();
    }

    private InputStream resource(String path) {
        InputStream stream = getClass().getResourceAsStream(path);
        if (stream == null) {
            throw new IllegalArgumentException("Missing test resource: " + path);
        }
        return stream;
    }

    private String resourceText(String path) throws Exception {
        try (InputStream stream = resource(path)) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
