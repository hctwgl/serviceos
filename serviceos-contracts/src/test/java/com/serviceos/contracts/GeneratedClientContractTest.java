package com.serviceos.contracts;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 Maven 生命周期确实从同一 Core OpenAPI 产出约定的 Web/iOS 客户端，
 * 而不是只声明未执行的生成插件或维护一份会漂移的手工 SDK。
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
        assertThat(packageNode.path("scripts").path("build").asText())
                .isEqualTo("tsc && tsc -p tsconfig.esm.json");
        assertThat(packageNode.path("scripts").has("prepare"))
                .as("file: 依赖安装期不得隐式编译生成客户端，编译由仓库门禁和 Admin 启动脚本显式负责")
                .isFalse();

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
                .contains("markAppointmentNoShow")
                .contains("listWorkOrderVisits")
                .contains("listAuthorizedReviewCases")
                .contains("listAuthorizedCorrectionCases")
                .contains("listAuthorizedOutboundDeliveries")
                .contains("checkInVisit")
                .contains("checkOutVisit")
                .contains("interruptVisit")
                .contains("checkInTechnicianVisit")
                .contains("interruptTechnicianVisit")
                .contains("listTechnicianTaskForms")
                .contains("submitTechnicianTaskForm")
                .contains("listTechnicianTaskEvidenceSlots")
                .contains("listTechnicianTaskEvidenceItems")
                .contains("beginTechnicianEvidenceUpload")
                .contains("finalizeTechnicianEvidenceUpload")
                .contains("createTechnicianTaskEvidenceSetSnapshot")
                .contains("completeTechnicianTask")
                .contains("createBydReviewSubmission")
                .contains("getWorkOrderActivitySummary")
                .contains("getOutboundDelivery")
                .contains("retryUnknownOutboundDelivery");
        assertThat(apiSource)
                .contains("listSlaInstances")
                .contains("listWorkOrderSlaInstances")
                .contains("getSlaInstance");
    }

    @Test
    void pinnedGeneratorMustProduceTheExpectedSwiftClientSurface() throws Exception {
        Path clientDirectory = Path.of("target/generated-clients/swift6");
        Path packageManifest = clientDirectory.resolve("Package.swift");
        Path generatorVersion = clientDirectory.resolve(".openapi-generator/VERSION");
        Path defaultApi = clientDirectory.resolve("Sources/ServiceOSCoreClient/APIs/DefaultAPI.swift");
        Path preferenceValue = clientDirectory.resolve(
                "Sources/ServiceOSCoreClient/Models/UiPreferenceEntryValue.swift");

        assertThat(packageManifest).exists();
        assertThat(generatorVersion).hasContent("7.22.0");
        assertThat(Files.readString(packageManifest))
                .contains("name: \"ServiceOSCoreClient\"")
                .contains(".library(name: \"ServiceOSCoreClient\"");

        String apiSource = Files.readString(defaultApi);
        assertThat(apiSource)
                .contains("open class DefaultAPI")
                .contains("listTechnicianTaskFeed")
                .contains("getTechnicianTaskDetail")
                .contains("checkInVisit")
                .contains("checkInTechnicianVisit")
                .contains("interruptTechnicianVisit")
                .contains("listTechnicianTaskForms")
                .contains("submitTechnicianTaskForm")
                .contains("listTechnicianTaskEvidenceSlots")
                .contains("listTechnicianTaskEvidenceItems")
                .contains("beginTechnicianEvidenceUpload")
                .contains("finalizeTechnicianEvidenceUpload")
                .contains("createTechnicianTaskEvidenceSetSnapshot")
                .contains("completeTechnicianTask")
                .contains("finalizeEvidenceUpload");

        // 上游 swift6 oneOf 模板会把 Dictionary 类型拼进 case 名；受控模板必须保持合法 Swift 标识符。
        assertThat(Files.readString(preferenceValue))
                .contains("case value5([String: JSONValue])")
                .doesNotContain("&#x60;")
                .doesNotContain("case typeArrayOfString:");
    }
}
