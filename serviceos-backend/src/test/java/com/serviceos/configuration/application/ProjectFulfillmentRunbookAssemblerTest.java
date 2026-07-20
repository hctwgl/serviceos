package com.serviceos.configuration.application;

import com.serviceos.configuration.api.ProjectFulfillmentRunbook;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectFulfillmentRunbookAssemblerTest {

    @Test
    void buildsChineseRunbookWithoutRequiringFrontendManifestParsing() {
        ProjectFulfillmentRunbookAssembler assembler = new ProjectFulfillmentRunbookAssembler();
        Map<String, Object> stage = new LinkedHashMap<>();
        stage.put("stageCode", "SURVEY");
        stage.put("stageName", "现场勘测");
        stage.put("sequence", 1);
        stage.put("ownerType", "TECHNICIAN");
        stage.put("taskType", "SURVEY");
        stage.put("formRefs", List.of("form-a"));
        stage.put("evidenceRefs", List.of());
        stage.put("actions", List.of("complete"));
        stage.put("exceptionPaths", List.of());
        stage.put("terminal", false);
        stage.put("slaRef", "sla-survey");

        ProjectFulfillmentManifestCompiler compiler = new ProjectFulfillmentManifestCompiler();
        var compiled = compiler.compile(
                java.util.UUID.randomUUID(),
                java.util.UUID.randomUUID(),
                java.util.UUID.randomUUID(),
                "HOME_CHARGING_SURVEY_INSTALL",
                "标准家充履约方案",
                "draft-preview",
                null,
                null,
                null,
                java.time.Instant.parse("2026-07-20T00:00:00Z"),
                Map.of(
                        "orderTypeName", "勘测安装",
                        "supportedClientKinds", List.of("ADMIN_WEB", "TECHNICIAN_WEB"),
                        "stages", List.of(stage)));

        ProjectFulfillmentRunbook runbook = assembler.fromManifestJson(compiled.json());
        assertThat(runbook.serviceProductLabel()).isEqualTo("家充勘测安装");
        assertThat(runbook.stageCount()).isEqualTo(1);
        assertThat(runbook.stages().getFirst().ownerTypeLabel()).isEqualTo("师傅");
        assertThat(runbook.stages().getFirst().formSummary()).contains("表单");
        assertThat(runbook.clientSupportSummary()).contains("师傅 H5");
        assertThat(runbook.impactSummary()).contains("存量工单");
    }
}
