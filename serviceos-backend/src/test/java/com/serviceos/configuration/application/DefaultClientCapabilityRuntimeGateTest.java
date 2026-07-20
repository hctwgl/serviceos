package com.serviceos.configuration.application;

import com.serviceos.configuration.api.ConfigurationAssetType;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultClientCapabilityRuntimeGateTest {
    private final DefaultClientCapabilityRuntimeGate gate = new DefaultClientCapabilityRuntimeGate();

    @Test
    void webAcceptsVisibleWhenForm() {
        assertThatCode(() -> gate.requireCompatible(
                "TECHNICIAN_WEB", ConfigurationAssetType.FORM, """
                        {"sections":[{"sectionKey":"s","title":"S","fields":[
                          {"fieldKey":"need","label":"N","dataType":"BOOLEAN","binding":"task.input.need"},
                          {"fieldKey":"detail","label":"D","dataType":"STRING","binding":"task.input.detail",
                           "visibleWhen":{"language":"SERVICEOS_EXPR_V1","source":"formValues[\\"need\\"] == true"}}]}]}
                        """))
                .doesNotThrowAnyException();
    }

    @Test
    void iosRejectsVisibleWhenForm() {
        assertThatThrownBy(() -> gate.requireCompatible(
                "TECHNICIAN_IOS", ConfigurationAssetType.FORM, """
                        {"sections":[{"sectionKey":"s","title":"S","fields":[
                          {"fieldKey":"need","label":"N","dataType":"BOOLEAN","binding":"task.input.need"},
                          {"fieldKey":"detail","label":"D","dataType":"STRING","binding":"task.input.detail",
                           "visibleWhen":{"language":"SERVICEOS_EXPR_V1","source":"formValues[\\"need\\"] == true"}}]}]}
                        """))
                .isInstanceOfSatisfying(BusinessProblem.class, problem -> {
                    assertThat(problem.code()).isEqualTo(ProblemCode.CLIENT_CAPABILITY_UNSUPPORTED);
                    assertThat(problem.getMessage()).contains("form.condition.visibleWhen");
                    assertThat(problem.getMessage()).contains("师傅 iOS");
                });
    }

    @Test
    void unknownClientSkipsEnforcement() {
        assertThatCode(() -> gate.requireCompatible(
                "UNKNOWN", ConfigurationAssetType.FORM, """
                        {"sections":[{"sectionKey":"s","title":"S","fields":[
                          {"fieldKey":"kind","label":"K","dataType":"ENUM","binding":"task.input.kind"}]}]}
                        """))
                .doesNotThrowAnyException();
    }

    @Test
    void iosRejectsSignatureEvidenceSlot() {
        assertThatThrownBy(() -> gate.requireCompatibleEvidenceSlots(
                "TECHNICIAN_IOS",
                List.of("SIGNATURE"),
                List.of("{\"mediaType\":\"SIGNATURE\",\"capture\":{}}")))
                .isInstanceOfSatisfying(BusinessProblem.class, problem ->
                        assertThat(problem.code()).isEqualTo(ProblemCode.CLIENT_CAPABILITY_UNSUPPORTED));
    }

    @Test
    void iosRejectedWhenOutsideWebOnlyTarget() {
        assertThatThrownBy(() -> gate.requireCompatible(
                "TECHNICIAN_IOS", ConfigurationAssetType.FORM, """
                        {"sections":[{"sectionKey":"s","title":"S","fields":[
                          {"fieldKey":"a","label":"A","dataType":"STRING","binding":"task.input.a"}]}]}
                        """,
                List.of("TECHNICIAN_WEB")))
                .isInstanceOfSatisfying(BusinessProblem.class, problem -> {
                    assertThat(problem.code()).isEqualTo(ProblemCode.CLIENT_CAPABILITY_UNSUPPORTED);
                    assertThat(problem.getMessage()).contains("定向发布目标");
                });
    }

    @Test
    void networkWebAcceptsPhotoAndSkipsDirectedTechnicianTarget() {
        assertThatCode(() -> gate.requireCompatibleEvidenceSlots(
                "NETWORK_WEB",
                List.of("PHOTO"),
                List.of("{\"mediaType\":\"PHOTO\",\"capture\":{}}"),
                List.of("TECHNICIAN_WEB")))
                .doesNotThrowAnyException();
    }

    @Test
    void networkWebRejectsSignatureSlot() {
        assertThatThrownBy(() -> gate.requireCompatibleEvidenceSlots(
                "NETWORK_WEB",
                List.of("SIGNATURE"),
                List.of("{\"mediaType\":\"SIGNATURE\",\"capture\":{}}")))
                .isInstanceOfSatisfying(BusinessProblem.class, problem -> {
                    assertThat(problem.code()).isEqualTo(ProblemCode.CLIENT_CAPABILITY_UNSUPPORTED);
                    assertThat(problem.getMessage()).contains("网点 Web");
                });
    }
}
