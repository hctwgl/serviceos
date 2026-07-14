package com.serviceos.evidence.infrastructure;

import com.serviceos.configuration.api.ConfigurationAssetDefinition;
import com.serviceos.configuration.api.ConfigurationAssetType;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonEvidenceTemplateResolverTest {
    private final JsonEvidenceTemplateResolver resolver =
            new JsonEvidenceTemplateResolver(new ObjectMapper());

    @Test
    void resolvesOnlyMatchingFixedStageAndDerivesSafeCountDefaults() {
        ConfigurationAssetDefinition template = asset("""
                {
                  "templateKey":"survey.site","version":"1.0.0","stage":"SURVEY",
                  "items":[
                    {"evidenceKey":"site.photo","name":"现场照","mediaType":"PHOTO","required":true},
                    {"evidenceKey":"site.note","name":"补充资料","mediaType":"DOCUMENT","required":false}
                  ]
                }
                """);

        assertThat(resolver.resolve(template, "INSTALLATION")).isEmpty();
        assertThat(resolver.resolve(template, "SURVEY"))
                .extracting(requirement -> requirement.requirementCode() + ":"
                        + requirement.minCount() + ":" + requirement.maxCount())
                .containsExactly("site.photo:1:null", "site.note:0:null");
    }

    @Test
    void rejectsConditionalOrUnscopedTemplatesInsteadOfGuessingRuntimeSemantics() {
        assertThatThrownBy(() -> resolver.resolve(asset("""
                {
                  "templateKey":"survey.site","version":"1.0.0","stage":"SURVEY",
                  "items":[{"evidenceKey":"site.photo","name":"现场照","mediaType":"PHOTO",
                    "required":false,"requiredWhen":{"language":"SERVICEOS_EXPR_V1","source":"pole == true"}}]
                }
                """), "SURVEY"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsupported SERVICEOS_EXPR_V1");
        assertThatThrownBy(() -> resolver.resolve(asset("""
                {"templateKey":"global","version":"1.0.0","items":[
                  {"evidenceKey":"site.photo","name":"现场照","mediaType":"PHOTO","required":true}]}
                """), "SURVEY"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("stage");
    }

    private ConfigurationAssetDefinition asset(String definition) {
        return new ConfigurationAssetDefinition(
                UUID.randomUUID(), ConfigurationAssetType.EVIDENCE, "survey.site",
                "1.0.0", "1.0.0", definition.trim(), "a".repeat(64));
    }
}
