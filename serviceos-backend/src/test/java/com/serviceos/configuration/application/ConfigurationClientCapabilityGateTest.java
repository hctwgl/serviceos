package com.serviceos.configuration.application;

import com.serviceos.configuration.api.ClientCompatibilityReport;
import com.serviceos.configuration.api.ConfigurationAssetType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigurationClientCapabilityGateTest {
    private final ConfigurationClientCapabilityGate gate = new ConfigurationClientCapabilityGate();

    @Test
    void scalarFormIsCompatibleWithWebAndIos() {
        ClientCompatibilityReport report = gate.evaluate(ConfigurationAssetType.FORM, """
                {"formKey":"ok","version":"1.0.0","stage":"SURVEY","sections":[{"sectionKey":"s",
                 "title":"S","fields":[{"fieldKey":"a","label":"A","dataType":"STRING",
                 "binding":"task.input.a"}]}]}
                """);
        assertThat(report.blocking()).isFalse();
        assertThat(report.requiredCapabilities()).containsExactly("form.fieldType.STRING");
        assertThat(report.clientReports()).allMatch(ClientCompatibilityReport.ClientReport::compatible);
    }

    @Test
    void unsupportedEnumFieldBlocksPublish() {
        ClientCompatibilityReport report = gate.evaluate(ConfigurationAssetType.FORM, """
                {"formKey":"enum","version":"1.0.0","stage":"SURVEY","sections":[{"sectionKey":"s",
                 "title":"S","fields":[{"fieldKey":"kind","label":"类型","dataType":"ENUM",
                 "binding":"task.input.kind"}]}]}
                """);
        assertThat(report.blocking()).isTrue();
        assertThat(report.blockingErrors().getFirst()).contains("form.fieldType.ENUM");
        assertThat(report.blockingErrors().getFirst()).contains("禁止发布");
    }

    @Test
    void signatureEvidenceBlocksPublish() {
        ClientCompatibilityReport report = gate.evaluate(ConfigurationAssetType.EVIDENCE, """
                {"templateKey":"sig","version":"1.0.0","title":"签名","stage":"SURVEY",
                 "items":[{"evidenceKey":"customer.sign","name":"客户签名","mediaType":"SIGNATURE",
                   "required":true,"capture":{"allowCamera":false,"allowGallery":false,
                     "minCount":1,"maxCount":1}}]}
                """);
        assertThat(report.blocking()).isTrue();
        assertThat(report.blockingErrors().getFirst()).contains("evidence.mediaType.SIGNATURE");
    }

    @Test
    void visibleWhenKeepsWebCompatibleAndMarksIosGapWithoutBlocking() {
        ClientCompatibilityReport report = gate.evaluate(ConfigurationAssetType.FORM, """
                {"formKey":"cond","version":"1.0.0","stage":"SURVEY","sections":[{"sectionKey":"s",
                 "title":"S","fields":[{"fieldKey":"need","label":"需要","dataType":"BOOLEAN",
                 "binding":"task.input.need"},{"fieldKey":"detail","label":"详情","dataType":"STRING",
                 "binding":"task.input.detail",
                 "visibleWhen":{"language":"SERVICEOS_EXPR_V1","source":"formValues[\\"need\\"] == true"}}]}]}
                """);
        assertThat(report.blocking()).isFalse();
        assertThat(report.clientReports()).anySatisfy(item -> {
            assertThat(item.clientKind()).isEqualTo("TECHNICIAN_WEB");
            assertThat(item.compatible()).isTrue();
        });
        assertThat(report.clientReports()).anySatisfy(item -> {
            assertThat(item.clientKind()).isEqualTo("TECHNICIAN_IOS");
            assertThat(item.compatible()).isFalse();
            assertThat(item.missingCapabilities()).contains("form.condition.visibleWhen");
        });
    }

    @Test
    void photoEvidenceIsCompatible() {
        ClientCompatibilityReport report = gate.evaluate(ConfigurationAssetType.EVIDENCE, """
                {"templateKey":"photo","version":"1.0.0","title":"照片","stage":"SURVEY",
                 "items":[{"evidenceKey":"site.panorama","name":"全景","mediaType":"PHOTO",
                   "required":true,"capture":{"allowCamera":true,"allowGallery":false,
                     "minCount":1,"maxCount":3}}]}
                """);
        assertThat(report.blocking()).isFalse();
        assertThat(report.clientReports()).allMatch(ClientCompatibilityReport.ClientReport::compatible);
    }
}
