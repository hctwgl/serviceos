package com.serviceos.configuration.application;

import com.serviceos.configuration.api.ProjectFulfillmentDocument;
import com.serviceos.configuration.api.ProjectFulfillmentStageDraft;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectFulfillmentDocumentMapperTest {

    private final ProjectFulfillmentDocumentMapper mapper =
            new ProjectFulfillmentDocumentMapper(JsonMapper.builder().build());

    @Test
    void roundTripsStructuredDocumentWithoutLosingStageBusinessFields() {
        ProjectFulfillmentDocument source = new ProjectFulfillmentDocument(
                "1.0.0",
                "勘测安装",
                List.of("ADMIN_WEB", "TECHNICIAN_WEB"),
                List.of(new ProjectFulfillmentStageDraft(
                        "SURVEY",
                        "现场勘测",
                        1,
                        "USER_TASK",
                        "SURVEY",
                        "TECHNICIAN",
                        "上门勘测",
                        List.of("form.survey"),
                        List.of("slot.photo"),
                        List.of(),
                        List.of(),
                        List.of(),
                        "sla-survey",
                        false)));

        String json = mapper.toJson(source);
        ProjectFulfillmentDocument restored = mapper.fromJson(json);

        assertThat(restored.orderTypeName()).isEqualTo("勘测安装");
        assertThat(restored.supportedClientKinds()).containsExactly("ADMIN_WEB", "TECHNICIAN_WEB");
        assertThat(restored.stages()).hasSize(1);
        assertThat(restored.stages().getFirst().stageName()).isEqualTo("现场勘测");
        assertThat(restored.stages().getFirst().ownerType()).isEqualTo("TECHNICIAN");
        assertThat(restored.stages().getFirst().formRefs()).containsExactly("form.survey");
        assertThat(restored.stages().getFirst().evidenceRefs()).containsExactly("slot.photo");
        assertThat(json).doesNotContain("documentJson");
    }
}
