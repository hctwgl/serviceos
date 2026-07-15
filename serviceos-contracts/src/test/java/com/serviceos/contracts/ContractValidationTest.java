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
                        "/tasks/{taskId}:assign-candidates", "/tasks/{taskId}:claim",
                        "/tasks/{taskId}:start", "/tasks/{taskId}:complete", "/tasks/{taskId}:release",
                        "/tasks/{taskId}/contact-attempts", "/appointments/{appointmentId}:cancel",
                        "/appointments/{appointmentId}:mark-no-show",
                        "/work-orders/{workOrderId}/visits",
                        "/appointments/{appointmentId}/visits:check-in",
                        "/visits/{visitId}:check-out", "/visits/{visitId}:interrupt",
                        "/internal/integration/byd/review-routes",
                        "/internal/integration/byd/review-submissions",
                        "/outbound-deliveries/{deliveryId}",
                        "/inbound-envelopes/{envelopeId}", "/canonical-messages/{messageId}");
    }

    @Test
    void bydCpimOpenApiMustParseAndExposeReviewSubmissionAndCallback() throws Exception {
        String yaml = resourceText("/openapi/byd-cpim-v731.yaml");
        SwaggerParseResult result = new OpenAPIV3Parser().readContents(yaml, null, null);

        assertThat(result.getMessages()).as("BYD OpenAPI parser messages").isEmpty();
        assertThat(result.getOpenAPI().getPaths())
                .containsKeys("/integrations/byd/cpim/v7.3.1/install-orders",
                        "/integrations/byd/cpim/v7.3.1/review-results",
                        "/jumpto/openapi/sp/pushSubmitReviewInfo");
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

    @Test
    void fileInvalidatedExampleMustMatchPublishedSchema() throws Exception {
        assertValidEvent(
                "/events/file-invalidated-v1.schema.json",
                "/events/file-invalidated-v1.valid.json");
    }

    @Test
    void evidenceCorrectionCaseCreatedExampleMustMatchPublishedSchema() throws Exception {
        assertValidEvent(
                "/events/evidence-correction-case-created-v1.schema.json",
                "/events/evidence-correction-case-created-v1.valid.json");
    }

    @Test
    void evidenceCorrectionResubmittedExampleMustMatchPublishedSchema() throws Exception {
        assertValidEvent(
                "/events/evidence-correction-resubmitted-v1.schema.json",
                "/events/evidence-correction-resubmitted-v1.valid.json");
    }

    @Test
    void evidenceCorrectionClosedExampleMustMatchPublishedSchema() throws Exception {
        assertValidEvent(
                "/events/evidence-correction-closed-v1.schema.json",
                "/events/evidence-correction-closed-v1.valid.json");
    }

    @Test
    void evidenceCorrectionWaivedExampleMustMatchPublishedSchema() throws Exception {
        assertValidEvent(
                "/events/evidence-correction-waived-v1.schema.json",
                "/events/evidence-correction-waived-v1.valid.json");
    }

    @Test
    void evidenceReviewCaseCreatedExampleMustMatchPublishedSchema() throws Exception {
        assertValidEvent(
                "/events/evidence-review-case-created-v1.schema.json",
                "/events/evidence-review-case-created-v1.valid.json");
    }

    @Test
    void evidenceClientReviewCaseCreatedExampleMustMatchPublishedSchema() throws Exception {
        assertValidEvent(
                "/events/evidence-client-review-case-created-v1.schema.json",
                "/events/evidence-client-review-case-created-v1.valid.json");
    }

    @Test
    void integrationCanonicalMessageProcessedExampleMustMatchPublishedSchema() throws Exception {
        assertValidEvent(
                "/events/integration-canonical-message-processed-v1.schema.json",
                "/events/integration-canonical-message-processed-v1.valid.json");
    }

    @Test
    void integrationExternalReviewRouteRegisteredExampleMustMatchPublishedSchema() throws Exception {
        assertValidEvent(
                "/events/integration-external-review-route-registered-v1.schema.json",
                "/events/integration-external-review-route-registered-v1.valid.json");
    }

    @Test
    void integrationExternalReviewCallbackProcessedExampleMustMatchPublishedSchema() throws Exception {
        assertValidEvent(
                "/events/integration-external-review-callback-processed-v1.schema.json",
                "/events/integration-external-review-callback-processed-v1.valid.json");
    }

    @Test
    void integrationOutboundDeliveryCreatedExampleMustMatchPublishedSchema() throws Exception {
        assertValidEvent(
                "/events/integration-outbound-delivery-created-v1.schema.json",
                "/events/integration-outbound-delivery-created-v1.valid.json");
    }

    @Test
    void integrationOutboundDeliveryAcknowledgedExampleMustMatchPublishedSchema() throws Exception {
        assertValidEvent(
                "/events/integration-outbound-delivery-acknowledged-v1.schema.json",
                "/events/integration-outbound-delivery-acknowledged-v1.valid.json");
    }

    @Test
    void bydReviewCallbackExampleMustMatchExternalSchema() throws Exception {
        assertValidEvent(
                "/external/byd-cpim-review-callback-v731.schema.json",
                "/external/byd-cpim-review-callback-v731.valid.json");
    }

    @Test
    void bydSubmitReviewExampleMustMatchExternalSchema() throws Exception {
        assertValidEvent(
                "/external/byd-cpim-submit-review-v731.schema.json",
                "/external/byd-cpim-submit-review-v731.valid.json");
    }

    @Test
    void evidenceReviewDecidedExampleMustMatchPublishedSchema() throws Exception {
        assertValidEvent(
                "/events/evidence-review-decided-v1.schema.json",
                "/events/evidence-review-decided-v1.valid.json");
    }

    @Test
    void evidenceExternalReviewReceiptRecordedExampleMustMatchPublishedSchema() throws Exception {
        assertValidEvent(
                "/events/evidence-external-review-receipt-recorded-v1.schema.json",
                "/events/evidence-external-review-receipt-recorded-v1.valid.json");
    }

    @Test
    void evidenceReviewCaseReopenedExampleMustMatchPublishedSchema() throws Exception {
        assertValidEvent(
                "/events/evidence-review-case-reopened-v1.schema.json",
                "/events/evidence-review-case-reopened-v1.valid.json");
    }

    @Test
    void taskCompletedV2ExampleMustMatchPublishedSchema() throws Exception {
        assertValidEvent(
                "/events/task-completed-v2.schema.json",
                "/events/task-completed-v2.valid.json");
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
